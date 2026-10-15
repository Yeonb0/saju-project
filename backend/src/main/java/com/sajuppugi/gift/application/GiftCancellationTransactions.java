package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork;
import com.sajuppugi.wallet.application.WalletPurchaseReceiptPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class GiftCancellationTransactions {
    private final JdbcTemplate jdbc;
    private final JdbcGiftPurchaseWork work;
    private final GiftCommandTransactions commands;
    private final WalletPurchaseReceiptPort receipts;
    private final Clock clock;
    public GiftCancellationTransactions(JdbcTemplate jdbc, JdbcGiftPurchaseWork work,
            GiftCommandTransactions commands, WalletPurchaseReceiptPort receipts, Clock clock) {
        this.jdbc = jdbc; this.work = work; this.commands = commands; this.receipts = receipts; this.clock = clock;
    }
    public void reserve(UUID user, UUID id, String key) {
        commands.owned(user, id);
        var current = work.get(id, true).orElseThrow(() -> new ApiException(ErrorCode.GIFT_CANCEL_NOT_ALLOWED));
        commands.bind(user, id, "CANCEL", key);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM gift_cancellations WHERE order_id = ?", Integer.class, id) != 0) return;
        Instant now = clock.instant();
        Timestamp provided = jdbc.queryForObject("SELECT first_provided_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, id);
        if (!"ACTIVE".equals(current.status()) || provided != null || current.approvedAt() == null
                || now.isBefore(current.approvedAt()) || now.isAfter(current.approvedAt().plus(Duration.ofDays(7))))
            throw new ApiException(ErrorCode.GIFT_CANCEL_NOT_ALLOWED);
        // Revocation and durable return intent commit before the independent wallet reversal.
        jdbc.update("INSERT INTO gift_cancellations(order_id,state,next_attempt_at,requested_at) VALUES (?,'PENDING',?,?)",
                id, Timestamp.from(now), Timestamp.from(now));
        work.stop(id, "CANCEL_PENDING", null);
        jdbc.update("UPDATE gift_purchase_tokens SET revoked = TRUE,token_ciphertext = NULL WHERE order_id = ?", id);
        jdbc.update("UPDATE gift_delivery_outbox SET status = 'CANCELED', private_payload = '' WHERE order_id = ?", id);
        jdbc.update("UPDATE gift_delivery_jobs SET state = 'CANCELED', lease_token = NULL, lease_until = NULL WHERE order_id = ? AND state NOT IN ('SENT','FAILED','CANCELED')", id);
    }
    public Target ready(UUID user, UUID id) {
        var order = commands.owned(user, id);
        var current = work.get(id, true).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var rows = jdbc.query("SELECT state,next_attempt_at FROM gift_cancellations WHERE order_id = ?",
                (rs, row) -> "PENDING".equals(rs.getString(1)) && !rs.getTimestamp(2).toInstant().isAfter(clock.instant()), id);
        if (rows.isEmpty() || !rows.getFirst() || !"CANCEL_PENDING".equals(current.status())) return null;
        return new Target(user, id, order.quoteId(), current.walletTransactionId());
    }
    public void complete(Target target) {
        work.get(target.orderId(), true).orElseThrow();
        var returned = receipts.findCompensation(target.userId(), target.quoteId(), target.transactionId())
                .orElseThrow(() -> new IllegalStateException("Gift return has no durable receipt"));
        int changed = jdbc.update("""
                UPDATE gift_cancellations SET state = 'COMPLETED', completed_at = ?, return_transaction_id = ?
                WHERE order_id = ? AND state = 'PENDING'
                """, Timestamp.from(clock.instant()), returned.transactionId(), target.orderId());
        if (changed == 1) work.stop(target.orderId(), "CANCELED", null);
    }
    public void failed(UUID user, UUID id) {
        commands.owned(user, id);
        work.get(id, true).orElseThrow();
        var rows = jdbc.query("SELECT attempts FROM gift_cancellations WHERE order_id = ? AND state = 'PENDING' AND next_attempt_at <= ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), id, Timestamp.from(clock.instant()));
        if (rows.isEmpty()) return;
        int attempts = rows.getFirst() + 1;
        jdbc.update("UPDATE gift_cancellations SET attempts = ?, state = ?, next_attempt_at = ? WHERE order_id = ?",
                attempts, attempts >= 5 ? "REVIEW_REQUIRED" : "PENDING",
                Timestamp.from(clock.instant().plusSeconds(Math.min(600, 30L << (attempts - 1)))), id);
        if (attempts >= 5) work.stop(id, "REVIEW_REQUIRED", "GIFT_RETURN_REVIEW_REQUIRED");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<JdbcGiftPurchaseWork.Target> due() {
        return jdbc.query("""
                SELECT d.user_id,c.order_id FROM gift_cancellations c JOIN gift_order_drafts d ON d.id = c.order_id
                WHERE c.state = 'PENDING' AND c.next_attempt_at <= ? ORDER BY c.next_attempt_at,c.order_id LIMIT 20
                """, (rs, row) -> new JdbcGiftPurchaseWork.Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), Timestamp.from(clock.instant()));
    }
    public record Target(UUID userId, UUID orderId, UUID quoteId, UUID transactionId) {}
}
