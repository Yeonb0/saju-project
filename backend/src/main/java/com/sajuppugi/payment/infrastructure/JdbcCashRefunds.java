package com.sajuppugi.payment.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.sajuppugi.payment.application.CashRefundPolicy;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.wallet.application.WalletOperationsService;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class JdbcCashRefunds {
    private final JdbcTemplate jdbc;
    private final WalletOperationsService wallets;
    private final CashRefundPolicy policy;
    public JdbcCashRefunds(JdbcTemplate jdbc, WalletOperationsService wallets, CashRefundPolicy policy) {
        this.jdbc = jdbc; this.wallets = wallets; this.policy = policy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public View prepare(UUID user, UUID order, String key, Instant now) {
        if (key == null || key.isBlank() || key.length() > 512) throw new ApiException(ErrorCode.INVALID_REQUEST);
        String hash = hash(key);
        var owner = lockOrder(order, user);
        var keys = jdbc.query("SELECT order_id FROM top_up_refund_requests WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> rs.getObject(1, UUID.class), user, hash);
        if (!keys.isEmpty() && !keys.getFirst().equals(order)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        var existing = find(order);
        if (existing.isPresent()) {
            bind(user, hash, order, now);
            return existing.get();
        }
        if (!policy.allowsFullUnused(owner.created(), now) || !"CREDITED".equals(owner.status()))
            throw new ApiException(ErrorCode.REFUND_NOT_ELIGIBLE);
        Integer verified = jdbc.queryForObject("SELECT COUNT(*) FROM top_up_confirmation_intents WHERE order_id = ? AND amount_krw = ?", Integer.class, order, owner.amount());
        if (verified == null || verified != 1) throw new ApiException(ErrorCode.REFUND_NOT_ELIGIBLE);
        UUID refund = UUID.randomUUID();
        if (!wallets.reclaimUnusedTopUp(user, order, refund, owner.paid(), owner.bonus()))
            throw new ApiException(ErrorCode.REFUND_NOT_ELIGIBLE);
        insert(refund, order, user, hash, "RESERVED", owner.amount(), "UNUSED_TOP_UP", now);
        bind(user, hash, order, now);
        return find(order).orElseThrow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public View owned(UUID user, UUID order) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM top_up_orders WHERE id = ? AND user_id = ?", Integer.class, order, user);
        if (count == null || count != 1) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        return find(order).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<Lease> claim(UUID order, Instant now) {
        lockOrder(order, null);
        var rows = jdbc.query("SELECT * FROM top_up_refunds WHERE order_id = ? FOR UPDATE", (rs, row) ->
                new Attempt(rs.getObject("id", UUID.class), rs.getString("state"), rs.getInt("attempts"),
                        rs.getTimestamp("next_attempt_at").toInstant()), order);
        if (rows.isEmpty()) return Optional.empty();
        var attempt = rows.getFirst();
        if (List.of("REFUNDED", "REVIEW_REQUIRED").contains(attempt.state()) || attempt.next().isAfter(now)) return Optional.empty();
        if (attempt.attempts() >= 5) {
            jdbc.update("UPDATE top_up_refunds SET state = 'REVIEW_REQUIRED', lease_token = NULL, lease_until = NULL WHERE order_id = ?", order);
            return Optional.empty();
        }
        var intents = jdbc.query("SELECT * FROM top_up_confirmation_intents WHERE order_id = ?", (rs, row) ->
                new TopUpConfirmationIntent(order, rs.getString("payment_key_hash"), rs.getInt("amount_krw"), rs.getTimestamp("created_at").toInstant()), order);
        if (intents.isEmpty()) throw new IllegalStateException("Refund has no verified payment intent");
        UUID token = UUID.randomUUID();
        jdbc.update("""
                UPDATE top_up_refunds SET state = 'IN_FLIGHT', attempts = attempts + 1, lease_token = ?, lease_until = ?, next_attempt_at = ? WHERE order_id = ?
                """, token, Timestamp.from(now.plusSeconds(180)), Timestamp.from(now.plusSeconds(180)), order);
        return Optional.of(new Lease(attempt.id(), order, token, intents.getFirst(), attempt.attempts() == 0));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void apply(Lease lease, PaymentProviderPort.Result result, Instant now) {
        lockOrder(lease.order(), null);
        var rows = jdbc.query("""
                SELECT attempts FROM top_up_refunds WHERE order_id = ? AND state = 'IN_FLIGHT' AND lease_token = ? AND lease_until > ? FOR UPDATE
                """, (rs, row) -> rs.getInt(1), lease.order(), lease.token(), Timestamp.from(now));
        if (rows.isEmpty()) return;
        boolean refunded = result != null && result.matches(lease.intent()) && result.outcome() == PaymentProviderPort.Outcome.CANCELED;
        String state = refunded ? "REFUNDED" : rows.getFirst() >= 5 ? "REVIEW_REQUIRED" : "RETRY";
        jdbc.update("""
                UPDATE top_up_refunds SET state = ?, completed_at = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL WHERE order_id = ?
                """, state, refunded ? Timestamp.from(now) : null, Timestamp.from(now.plusSeconds(30)), lease.order());
        if (refunded) jdbc.update("UPDATE top_up_orders SET status = 'REFUNDED' WHERE id = ?", lease.order());
    }

    // Provider-only evidence, never the webhook body, reaches this boundary.
    @Transactional(timeout = 10)
    public boolean reconcileCanceled(UUID order, PaymentProviderPort.Result evidence, Instant now) {
        var owner = lockOrder(order, null);
        var intent = jdbc.query("SELECT * FROM top_up_confirmation_intents WHERE order_id = ?", (rs, row) ->
                new TopUpConfirmationIntent(order, rs.getString("payment_key_hash"), rs.getInt("amount_krw"), rs.getTimestamp("created_at").toInstant()), order);
        if (intent.isEmpty() || evidence == null || !evidence.matches(intent.getFirst()) || evidence.outcome() != PaymentProviderPort.Outcome.CANCELED) return false;
        var existing = find(order);
        if (existing.isPresent()) {
            if ("REVIEW_REQUIRED".equals(existing.get().state())) return false;
            jdbc.update("UPDATE top_up_refunds SET state = 'REFUNDED', completed_at = ?, lease_token = NULL, lease_until = NULL WHERE order_id = ?",
                    Timestamp.from(now), order);
            jdbc.update("UPDATE top_up_orders SET status = 'REFUNDED' WHERE id = ?", order);
            return true;
        }
        if (!"CREDITED".equals(owner.status())) return "REFUNDED".equals(owner.status());
        UUID refund = UUID.randomUUID();
        boolean reclaimed = wallets.reclaimUnusedTopUp(owner.user(), order, refund, owner.paid(), owner.bonus());
        if (!reclaimed) wallets.flagExternalCancellation(owner.user());
        insert(refund, order, owner.user(), hash("external-cancel-" + order), reclaimed ? "REFUNDED" : "REVIEW_REQUIRED",
                owner.amount(), "EXTERNAL_CANCEL", now);
        if (reclaimed) {
            jdbc.update("UPDATE top_up_refunds SET completed_at = ? WHERE order_id = ?", Timestamp.from(now), order);
            jdbc.update("UPDATE top_up_orders SET status = 'REFUNDED' WHERE id = ?", order);
        }
        return reclaimed;
    }

    public List<UUID> due(Instant now) {
        return jdbc.query("SELECT order_id FROM top_up_refunds WHERE state IN ('RESERVED','IN_FLIGHT','RETRY') AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(now));
    }
    @Transactional(timeout = 10)
    public void flagPartialCancellation(UUID order, Instant now) {
        var owner = lockOrder(order, null);
        wallets.flagExternalCancellation(owner.user());
        if (find(order).isEmpty()) insert(UUID.randomUUID(), order, owner.user(), hash("partial-cancel-" + order),
                "REVIEW_REQUIRED", owner.amount(), "PARTIAL_CANCEL", now);
        else jdbc.update("UPDATE top_up_refunds SET state = 'REVIEW_REQUIRED', completed_at = NULL, lease_token = NULL, lease_until = NULL WHERE order_id = ?", order);
    }
    private void insert(UUID id, UUID order, UUID user, String hash, String state, int amount, String reason, Instant now) {
        jdbc.update("""
                INSERT INTO top_up_refunds(id, order_id, user_id, key_hash, state, amount_krw, reason_code, next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, order, user, hash, state, amount, reason, Timestamp.from(now), Timestamp.from(now));
    }
    private void bind(UUID user, String key, UUID order, Instant now) {
        jdbc.update("INSERT INTO top_up_refund_requests(user_id, key_hash, order_id, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                user, key, order, Timestamp.from(now));
        UUID bound = jdbc.queryForObject("SELECT order_id FROM top_up_refund_requests WHERE user_id = ? AND key_hash = ?", UUID.class, user, key);
        if (!order.equals(bound)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }
    private Optional<View> find(UUID order) {
        return jdbc.query("SELECT * FROM top_up_refunds WHERE order_id = ?", (rs, row) -> view(rs), order).stream().findFirst();
    }
    private String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private View view(ResultSet rs) throws SQLException {
        String state = rs.getString("state");
        return new View(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), state,
                rs.getInt("amount_krw"), "REFUNDED".equals(state) ? "NONE" : "REVIEW_REQUIRED".equals(state) ? "CONTACT_SUPPORT" : "POLL",
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
    }
    private Order lockOrder(UUID order, UUID user) {
        var rows = jdbc.query("SELECT * FROM top_up_orders WHERE id = ? FOR UPDATE", (rs, row) -> new Order(
                rs.getObject("user_id", UUID.class), rs.getString("status"), rs.getInt("amount_krw"), rs.getInt("paid_shells"),
                rs.getInt("bonus_shells"), rs.getTimestamp("created_at").toInstant()), order);
        if (rows.isEmpty() || user != null && !user.equals(rows.getFirst().user())) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }
    @io.swagger.v3.oas.annotations.media.Schema(name = "CashRefundResponse", requiredProperties = {"refundId", "orderId", "state", "amount", "nextAction", "completedAt"})
    public record View(UUID refundId, UUID orderId, String state, int amount, String nextAction,
            @io.swagger.v3.oas.annotations.media.Schema(types = {"string", "null"}, format = "date-time") Instant completedAt) {}
    public record Lease(UUID refund, UUID order, UUID token, TopUpConfirmationIntent intent, boolean first) {}
    private record Attempt(UUID id, String state, int attempts, Instant next) {}
    private record Order(UUID user, String status, int amount, int paid, int bonus, Instant created) {}
}
