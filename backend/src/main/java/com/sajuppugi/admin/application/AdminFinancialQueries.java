package com.sajuppugi.admin.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.logging.TraceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 5)
public class AdminFinancialQueries {
    private final JdbcTemplate jdbc;
    private final AdminAccessService access;
    private final AuditLogPort audit;
    private final Clock clock;
    public AdminFinancialQueries(JdbcTemplate jdbc, AdminAccessService access, AuditLogPort audit, Clock clock) { this.jdbc = jdbc; this.access = access; this.audit = audit; this.clock = clock; }
    public Page<AdminOrder> orders(UUID actor, UUID after, int limit) {
        authorize(actor, limit);
        var rows = jdbc.query("""
                SELECT o.id, o.user_id, o.amount_krw, o.status, o.created_at, r.state AS refund_state, r.resolution
                FROM top_up_orders o LEFT JOIN top_up_refunds r ON r.order_id = o.id
                WHERE (CAST(? AS UUID) IS NULL OR o.id > CAST(? AS UUID)) ORDER BY o.id LIMIT ?
                """, (rs, row) -> new AdminOrder(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), "CANCEL_REJECTED".equals(rs.getString(7)) ? "REJECTED" : rs.getString(6)),
                after == null ? null : after.toString(), after == null ? null : after.toString(), limit + 1);
        append(actor, "ADMIN_ORDER_VIEW", "ORDER_LIST", actor);
        return page(rows, limit, v -> v.orderId());
    }
    public Page<AdminRefund> refunds(UUID actor, UUID after, int limit) {
        authorize(actor, limit);
        var rows = jdbc.query("SELECT * FROM top_up_refunds WHERE (CAST(? AS UUID) IS NULL OR id > CAST(? AS UUID)) ORDER BY id LIMIT ?",
                (rs, row) -> new AdminRefund(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getObject("user_id", UUID.class),
                        "CANCEL_REJECTED".equals(rs.getString("resolution")) ? "REJECTED" : rs.getString("state"), rs.getInt("amount_krw"), rs.getInt("attempts"),
                        rs.getTimestamp("created_at").toInstant()), after == null ? null : after.toString(), after == null ? null : after.toString(), limit + 1);
        append(actor, "ADMIN_REFUND_VIEW", "REFUND_LIST", actor);
        return page(rows, limit, v -> v.refundId());
    }
    public Page<AdminLedger> ledger(UUID actor, UUID user, UUID after, int limit) {
        authorize(actor, limit);
        var rows = jdbc.query("SELECT * FROM wallet_transactions WHERE wallet_user_id = ? AND (CAST(? AS UUID) IS NULL OR id > CAST(? AS UUID)) ORDER BY id LIMIT ?",
                (rs, row) -> new AdminLedger(rs.getObject("id", UUID.class), rs.getString("type"), rs.getInt("total_amount"),
                        rs.getObject("reference_id", UUID.class), rs.getObject("reversal_of_id", UUID.class), rs.getTimestamp("created_at").toInstant()),
                user, after == null ? null : after.toString(), after == null ? null : after.toString(), limit + 1);
        append(actor, "ADMIN_LEDGER_VIEW", "WALLET", user);
        return page(rows, limit, v -> v.transactionId());
    }
    public Page<AdminIncident> incidents(UUID actor, UUID after, int limit) {
        authorize(actor, limit);
        var rows = jdbc.query("SELECT * FROM operations_incidents WHERE (CAST(? AS UUID) IS NULL OR id > CAST(? AS UUID)) ORDER BY id LIMIT ?",
                (rs, row) -> new AdminIncident(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getObject("resource_id", UUID.class),
                        rs.getString("state"), rs.getInt("generation"), rs.getTimestamp("detected_at").toInstant()),
                after == null ? null : after.toString(), after == null ? null : after.toString(), limit + 1);
        append(actor, "ADMIN_INCIDENT_VIEW", "INCIDENT_LIST", actor);
        return page(rows, limit, v -> v.incidentId());
    }
    private void authorize(UUID actor, int limit) {
        access.require(actor, false);
        if (limit < 1 || limit > 100) throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    public Page<AdminAuditEvent> audits(UUID actor, UUID after, int limit) {
        authorize(actor, limit);
        var rows = jdbc.query("SELECT * FROM admin_audit_events WHERE (CAST(? AS UUID) IS NULL OR id > CAST(? AS UUID)) ORDER BY id LIMIT ?",
                (rs, row) -> new AdminAuditEvent(rs.getObject("id", UUID.class), rs.getObject("operator_id", UUID.class), rs.getString("action"),
                        rs.getString("target_type"), rs.getObject("target_id", UUID.class), rs.getString("reason_code"), rs.getTimestamp("occurred_at").toInstant()),
                after == null ? null : after.toString(), after == null ? null : after.toString(), limit + 1);
        append(actor, "ADMIN_AUDIT_VIEW", "AUDIT_LIST", actor);
        return page(rows, limit, v -> v.eventId());
    }
    private void append(UUID actor, String action, String type, UUID target) {
        audit.append(new AuditLogPort.Entry(actor, action, type, target, "SUPPORT_VIEW", TraceContext.currentId(), clock.instant()));
    }
    private <T> Page<T> page(List<T> rows, int limit, java.util.function.Function<T, UUID> id) {
        boolean hasNext = rows.size() > limit;
        var values = rows.stream().limit(limit).toList();
        return new Page<>(values, hasNext, hasNext ? id.apply(values.getLast()) : null);
    }
    public record Page<T>(List<T> items, boolean hasNext, UUID nextCursor) {}
    public record AdminOrder(UUID orderId, UUID userId, int amount, String status, Instant createdAt, String refundState) {}
    public record AdminRefund(UUID refundId, UUID orderId, UUID userId, String state, int amount, int attempts, Instant createdAt) {}
    public record AdminLedger(UUID transactionId, String type, int amount, UUID referenceId, UUID reversalOfId, Instant createdAt) {}
    public record AdminIncident(UUID incidentId, String kind, UUID resourceId, String state, int generation, Instant detectedAt) {}
    public record AdminAuditEvent(UUID eventId, UUID operatorId, String action, String targetType, UUID targetId, String reasonCode, Instant occurredAt) {}
}
