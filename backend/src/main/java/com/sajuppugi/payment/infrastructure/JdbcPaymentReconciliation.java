package com.sajuppugi.payment.infrastructure;

import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import com.sajuppugi.payment.port.PaymentProviderPort;
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
public class JdbcPaymentReconciliation {
    private final JdbcTemplate jdbc;
    private final JdbcCashRefunds refunds;
    public JdbcPaymentReconciliation(JdbcTemplate jdbc, JdbcCashRefunds refunds) { this.jdbc = jdbc; this.refunds = refunds; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void signal(UUID order, Instant now) {
        // One durable row per known order; duplicates cannot reset retries or force provider calls.
        jdbc.update("""
                INSERT INTO payment_reconciliation_jobs(order_id, state, next_attempt_at)
                SELECT id, 'READY', ? FROM top_up_orders WHERE id = ? AND status IN ('PAYMENT_PENDING','PAID','CREDITED','REFUNDED')
                ON CONFLICT (order_id) DO NOTHING
                """, Timestamp.from(now), order);
    }
    public List<UUID> scanAfter(UUID cursor) {
        return cursor == null ? jdbc.query("SELECT id FROM top_up_orders WHERE status IN ('PAYMENT_PENDING','PAID','CREDITED','REFUNDED') ORDER BY id LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class)) : jdbc.query("SELECT id FROM top_up_orders WHERE id > ? AND status IN ('PAYMENT_PENDING','PAID','CREDITED','REFUNDED') ORDER BY id LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class), cursor);
    }
    public List<UUID> due(Instant now) {
        return jdbc.query("SELECT order_id FROM payment_reconciliation_jobs WHERE state <> 'REVIEW_REQUIRED' AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(now));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<Lease> claim(UUID order, Instant now) {
        jdbc.query("SELECT id FROM top_up_orders WHERE id = ? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), order);
        var jobs = jdbc.query("SELECT state, attempts, next_attempt_at FROM payment_reconciliation_jobs WHERE order_id = ? FOR UPDATE",
                (rs, row) -> new Job(rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toInstant()), order);
        if (jobs.isEmpty()) return Optional.empty();
        var job = jobs.getFirst();
        if ("REVIEW_REQUIRED".equals(job.state()) || job.next().isAfter(now)) return Optional.empty();
        if (job.attempts() >= 5) {
            jdbc.update("UPDATE payment_reconciliation_jobs SET state = 'REVIEW_REQUIRED', finding_code = 'PAYMENT_LOOKUP_EXHAUSTED' WHERE order_id = ?", order);
            return Optional.empty();
        }
        var intents = jdbc.query("SELECT * FROM top_up_confirmation_intents WHERE order_id = ?", (rs, row) ->
                new TopUpConfirmationIntent(order, rs.getString("payment_key_hash"), rs.getInt("amount_krw"), rs.getTimestamp("created_at").toInstant()), order);
        if (intents.isEmpty()) {
            jdbc.update("UPDATE payment_reconciliation_jobs SET next_attempt_at = ? WHERE order_id = ?", Timestamp.from(now.plusSeconds(3600)), order);
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        jdbc.update("UPDATE payment_reconciliation_jobs SET state = 'IN_FLIGHT', attempts = attempts + 1, lease_token = ?, lease_until = ?, next_attempt_at = ? WHERE order_id = ?",
                token, Timestamp.from(now.plusSeconds(180)), Timestamp.from(now.plusSeconds(180)), order);
        return Optional.of(new Lease(order, token, intents.getFirst()));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void apply(Lease lease, PaymentProviderPort.Result result, Instant now) {
        String orderState = jdbc.queryForObject("SELECT status FROM top_up_orders WHERE id = ? FOR UPDATE", String.class, lease.order());
        var jobs = jdbc.query("SELECT attempts FROM payment_reconciliation_jobs WHERE order_id = ? AND state = 'IN_FLIGHT' AND lease_token = ? AND lease_until > ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), lease.order(), lease.token(), Timestamp.from(now));
        if (jobs.isEmpty()) return;
        boolean matches = result != null && result.matches(lease.intent());
        boolean checked = matches && result.approves(lease.intent()) && "CREDITED".equals(orderState);
        checked |= matches && result.outcome() == PaymentProviderPort.Outcome.CANCELED && "CANCELED".equals(orderState);
        checked |= matches && result.outcome() == PaymentProviderPort.Outcome.DECLINED && "FAILED".equals(orderState);
        String finding = null;
        if (matches && "PAYMENT_PARTIAL_CANCEL_REVIEW".equals(result.safeFailureCode()) && "CREDITED".equals(orderState)) {
            refunds.flagPartialCancellation(lease.order(), now);
            finding = "PARTIAL_CANCEL_REVIEW";
        } else if (matches && result.outcome() == PaymentProviderPort.Outcome.CANCELED && List.of("CREDITED", "REFUNDED").contains(orderState)) {
            checked = refunds.reconcileCanceled(lease.order(), result, now);
            if (!checked) finding = "EXTERNAL_CANCEL_REVIEW";
        } else if (matches && result.approves(lease.intent()) && "REFUNDED".equals(orderState)) finding = "REFUND_PROVIDER_MISMATCH";
        boolean review = finding != null || jobs.getFirst() >= 5;
        jdbc.update("""
                UPDATE payment_reconciliation_jobs SET state = ?, attempts = ?, next_attempt_at = ?, checked_at = ?,
                finding_code = ?, lease_token = NULL, lease_until = NULL WHERE order_id = ?
                """, checked ? "CHECKED" : review ? "REVIEW_REQUIRED" : "RETRY", checked ? 0 : jobs.getFirst(),
                Timestamp.from(now.plusSeconds(checked ? 3600 : 30)), checked ? Timestamp.from(now) : null,
                checked ? null : finding != null ? finding : review ? "PAYMENT_LOOKUP_EXHAUSTED" : null, lease.order());
    }
    public record Lease(UUID order, UUID token, TopUpConfirmationIntent intent) {}
    private record Job(String state, int attempts, Instant next) {}
}
