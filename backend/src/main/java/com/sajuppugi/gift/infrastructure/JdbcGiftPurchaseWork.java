package com.sajuppugi.gift.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcGiftPurchaseWork {
    private final JdbcTemplate jdbc;
    public JdbcGiftPurchaseWork(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID order, Instant now) {
        jdbc.update("INSERT INTO gift_purchase_work(order_id, status, next_attempt_at) VALUES (?, 'PENDING', ?) ON CONFLICT DO NOTHING",
                order, Timestamp.from(now));
    }
    public List<Target> due(Instant now) {
        return jdbc.query("""
                SELECT d.user_id, w.order_id FROM gift_purchase_work w JOIN gift_order_drafts d ON d.id = w.order_id
                WHERE w.status = 'PENDING' AND w.next_attempt_at <= ? ORDER BY w.next_attempt_at, w.order_id LIMIT 20
                """, (rs, row) -> new Target(rs.getObject("user_id", UUID.class), rs.getObject("order_id", UUID.class)), Timestamp.from(now));
    }
    public Optional<Work> get(UUID order, boolean lock) {
        return jdbc.query("SELECT * FROM gift_purchase_work WHERE order_id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Work(order, rs.getString("status"), rs.getInt("failures"),
                        rs.getTimestamp("next_attempt_at").toInstant(), rs.getObject("gift_id", UUID.class),
                        rs.getObject("wallet_transaction_id", UUID.class),
                        rs.getObject("charged_amount", Integer.class),
                        rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toInstant()), order)
                .stream().findFirst();
    }
    public String payload(UUID order) {
        return jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order);
    }
    public void activate(UUID order, UUID transaction, int amount, Instant approved, UUID gift, Instant expiry) {
        jdbc.update("""
                UPDATE gift_purchase_work SET status = 'ACTIVE', wallet_transaction_id = ?, charged_amount = ?,
                    approved_at = ?, gift_id = ?, access_expires_at = ?, failure_code = NULL WHERE order_id = ?
                """, transaction, amount, Timestamp.from(approved), gift, Timestamp.from(expiry), order);
    }
    public void token(UUID order, String hash, String ciphertext, Instant issuedAt, Instant revealUntil) {
        jdbc.update("INSERT INTO gift_purchase_tokens(token_hash, order_id, token_ciphertext, issued_at, reveal_until) VALUES (?, ?, ?, ?, ?)",
                hash, order, ciphertext, Timestamp.from(issuedAt), Timestamp.from(revealUntil));
    }
    public void enqueue(UUID id, UUID order, String encryptedPayload, Instant now) {
        jdbc.update("INSERT INTO gift_delivery_outbox(id, order_id, private_payload, status, created_at) VALUES (?, ?, ?, 'PENDING', ?)",
                id, order, encryptedPayload, Timestamp.from(now));
        jdbc.update("INSERT INTO gift_delivery_jobs(id,order_id,state,next_attempt_at,created_at) VALUES (?,?,'PENDING',?,?)",
                id, order, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("UPDATE gift_delivery_outbox SET current_job_id = ? WHERE id = ?", id, id);
    }
    public void stop(UUID order, String status, String code) {
        jdbc.update("UPDATE gift_purchase_work SET status = ?, failure_code = ? WHERE order_id = ?", status, code, order);
    }
    public void failedAttempt(Work work, Instant now) {
        int failures = work.failures() + 1;
        jdbc.update("UPDATE gift_purchase_work SET failures = ?, status = ?, failure_code = 'GIFT_ACTIVATION_FAILED', next_attempt_at = ? WHERE order_id = ?",
                failures, failures >= 5 ? "REVIEW_REQUIRED" : "PENDING",
                Timestamp.from(now.plusSeconds(Math.min(600, 30L << (failures - 1)))), work.orderId());
    }
    public record Work(UUID orderId, String status, int failures, Instant nextAttemptAt, UUID giftId,
                       UUID walletTransactionId, Integer chargedAmount, Instant approvedAt) {}
    public record Target(UUID userId, UUID orderId) {}
}
