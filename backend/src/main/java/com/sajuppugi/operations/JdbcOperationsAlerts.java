package com.sajuppugi.operations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOperationsAlerts {
    private final JdbcTemplate jdbc;
    private final OperationsAlertProperties properties;
    public JdbcOperationsAlerts(JdbcTemplate jdbc, OperationsAlertProperties properties) { this.jdbc = jdbc; this.properties = properties; }

    public List<Problem> candidates(Instant now) {
        return jdbc.query("""
                SELECT p.kind, p.resource_id FROM (
                    SELECT 'REFUND_REVIEW' AS kind, id AS resource_id FROM top_up_refunds WHERE state = 'REVIEW_REQUIRED' AND resolution IS NULL
                    UNION ALL SELECT 'REFUND_STALLED', id FROM top_up_refunds WHERE state IN ('RESERVED','IN_FLIGHT','RETRY') AND created_at < ?
                    UNION ALL SELECT 'TOP_UP_REVIEW', order_id FROM top_up_executions WHERE state = 'REVIEW_REQUIRED'
                    UNION ALL SELECT 'TOP_UP_STALLED', e.order_id FROM top_up_executions e JOIN top_up_confirmation_intents i ON i.order_id = e.order_id
                        WHERE e.state IN ('READY','IN_FLIGHT','RETRY') AND i.created_at < ?
                    UNION ALL SELECT 'PAYMENT_RECONCILIATION', order_id FROM payment_reconciliation_jobs WHERE state = 'REVIEW_REQUIRED'
                    UNION ALL SELECT DISTINCT 'WALLET_INTEGRITY', wallet_user_id FROM wallet_integrity_findings WHERE resolved_at IS NULL
                ) p LEFT JOIN operations_incidents i ON i.kind = p.kind AND i.resource_id = p.resource_id
                WHERE i.id IS NULL OR i.state = 'RESOLVED' ORDER BY p.kind, p.resource_id LIMIT 100
                """, (rs, row) -> new Problem(rs.getString(1), rs.getObject(2, UUID.class)), threshold(now), threshold(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void observe(Problem problem, Instant now) {
        if (!stillOpen(problem, now)) return;
        jdbc.update("INSERT INTO operations_incidents(id, kind, resource_id, state, generation, detected_at) VALUES (?, ?, ?, 'OPEN', 1, ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), problem.kind(), problem.resource(), Timestamp.from(now));
        var incident = jdbc.query("SELECT id, state, generation FROM operations_incidents WHERE kind = ? AND resource_id = ? FOR UPDATE",
                (rs, row) -> new Incident(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)), problem.kind(), problem.resource()).getFirst();
        int generation = incident.generation();
        if ("RESOLVED".equals(incident.state())) {
            generation++;
            jdbc.update("UPDATE operations_incidents SET state = 'OPEN', generation = ?, detected_at = ?, resolved_at = NULL WHERE id = ?",
                    generation, Timestamp.from(now), incident.id());
        }
        jdbc.update("INSERT INTO operations_alert_deliveries(id, incident_id, generation, state, next_attempt_at) VALUES (?, ?, ?, 'READY', ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), incident.id(), generation, Timestamp.from(now));
    }

    public List<UUID> openAfter(UUID cursor) {
        return cursor == null ? jdbc.query("SELECT id FROM operations_incidents WHERE state = 'OPEN' ORDER BY id LIMIT 100", (rs, row) -> rs.getObject(1, UUID.class))
                : jdbc.query("SELECT id FROM operations_incidents WHERE state = 'OPEN' AND id > ? ORDER BY id LIMIT 100", (rs, row) -> rs.getObject(1, UUID.class), cursor);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void resolveIfHealthy(UUID id, Instant now) {
        var problems = jdbc.query("SELECT kind, resource_id FROM operations_incidents WHERE id = ? AND state = 'OPEN' FOR UPDATE",
                (rs, row) -> new Problem(rs.getString(1), rs.getObject(2, UUID.class)), id);
        if (!problems.isEmpty() && !stillOpen(problems.getFirst(), now))
            jdbc.update("UPDATE operations_incidents SET state = 'RESOLVED', resolved_at = ? WHERE id = ?", Timestamp.from(now), id);
    }

    public List<UUID> due(Instant now) {
        return jdbc.query("SELECT id FROM operations_alert_deliveries WHERE state IN ('READY','RETRY','IN_FLIGHT') AND next_attempt_at <= ? ORDER BY next_attempt_at, id LIMIT 20",
                (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(now));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<Lease> claim(UUID delivery, Instant now) {
        var ids = jdbc.query("SELECT incident_id FROM operations_alert_deliveries WHERE id = ?", (rs, row) -> rs.getObject(1, UUID.class), delivery);
        if (ids.isEmpty()) return Optional.empty();
        var incidents = jdbc.query("SELECT kind, resource_id, state, generation, detected_at FROM operations_incidents WHERE id = ? FOR UPDATE",
                (rs, row) -> new AlertIncident(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4), rs.getTimestamp(5).toInstant()), ids.getFirst());
        var rows = jdbc.query("SELECT state, generation, attempts, next_attempt_at FROM operations_alert_deliveries WHERE id = ? FOR UPDATE",
                (rs, row) -> new Attempt(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getTimestamp(4).toInstant()), delivery);
        var attempt = rows.getFirst(); var incident = incidents.getFirst();
        if (!List.of("READY","RETRY","IN_FLIGHT").contains(attempt.state()) || attempt.next().isAfter(now)) return Optional.empty();
        if (!"OPEN".equals(incident.state()) || incident.generation() != attempt.generation()) {
            jdbc.update("UPDATE operations_alert_deliveries SET state = 'CANCELED', lease_token = NULL, lease_until = NULL WHERE id = ?", delivery);
            return Optional.empty();
        }
        if (attempt.attempts() >= 5) {
            jdbc.update("UPDATE operations_alert_deliveries SET state = 'REVIEW_REQUIRED', lease_token = NULL, lease_until = NULL WHERE id = ?", delivery);
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        jdbc.update("UPDATE operations_alert_deliveries SET state = 'IN_FLIGHT', attempts = attempts + 1, lease_token = ?, lease_until = ?, next_attempt_at = ? WHERE id = ?",
                token, Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(120)), delivery);
        return Optional.of(new Lease(new OperationsAlertSink.Alert(delivery, incident.kind(), incident.resource(), incident.generation(), incident.detected()), token));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void finish(Lease lease, boolean accepted, Instant now) {
        var rows = jdbc.query("SELECT attempts FROM operations_alert_deliveries WHERE id = ? AND state = 'IN_FLIGHT' AND lease_token = ? AND lease_until > ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), lease.alert().alertId(), lease.token(), Timestamp.from(now));
        if (rows.isEmpty()) return;
        jdbc.update("UPDATE operations_alert_deliveries SET state = ?, delivered_at = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL WHERE id = ?",
                accepted ? "DELIVERED" : rows.getFirst() >= 5 ? "REVIEW_REQUIRED" : "RETRY", accepted ? Timestamp.from(now) : null,
                Timestamp.from(now.plusSeconds(30)), lease.alert().alertId());
    }

    private Timestamp threshold(Instant now) { return Timestamp.from(now.minus(properties.stalledAfter())); }
    private boolean stillOpen(Problem problem, Instant now) {
        String sql = switch (problem.kind()) {
            case "REFUND_REVIEW" -> "SELECT COUNT(*) FROM top_up_refunds WHERE id = ? AND state = 'REVIEW_REQUIRED' AND resolution IS NULL";
            case "REFUND_STALLED" -> "SELECT COUNT(*) FROM top_up_refunds WHERE id = ? AND state IN ('RESERVED','IN_FLIGHT','RETRY') AND created_at < ?";
            case "TOP_UP_REVIEW" -> "SELECT COUNT(*) FROM top_up_executions WHERE order_id = ? AND state = 'REVIEW_REQUIRED'";
            case "TOP_UP_STALLED" -> "SELECT COUNT(*) FROM top_up_executions e JOIN top_up_confirmation_intents i ON i.order_id = e.order_id WHERE e.order_id = ? AND e.state IN ('READY','IN_FLIGHT','RETRY') AND i.created_at < ?";
            case "PAYMENT_RECONCILIATION" -> "SELECT COUNT(*) FROM payment_reconciliation_jobs WHERE order_id = ? AND state = 'REVIEW_REQUIRED'";
            case "WALLET_INTEGRITY" -> "SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND resolved_at IS NULL";
            default -> throw new IllegalArgumentException("Unsupported operations alert kind");
        };
        Integer count = problem.kind().endsWith("STALLED") ? jdbc.queryForObject(sql, Integer.class, problem.resource(), threshold(now))
                : jdbc.queryForObject(sql, Integer.class, problem.resource());
        return count != null && count > 0;
    }
    public record Problem(String kind, UUID resource) {}
    public record Lease(OperationsAlertSink.Alert alert, UUID token) {}
    private record Incident(UUID id, String state, int generation) {}
    private record Attempt(String state, int generation, int attempts, Instant next) {}
    private record AlertIncident(String kind, UUID resource, String state, int generation, Instant detected) {}
}
