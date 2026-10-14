package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftFulfillmentPort;
import com.sajuppugi.gift.domain.GiftLifetimePolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(timeout = 10)
public class JdbcGiftFulfillment implements GiftFulfillmentPort {
    private final JdbcTemplate jdbc;
    private final JdbcGiftPurchaseWork work;
    private final JdbcGiftDeliveryWork audit;
    private final GiftPayloadCipher cipher;
    private final Clock clock;
    public JdbcGiftFulfillment(JdbcTemplate jdbc, JdbcGiftPurchaseWork work, JdbcGiftDeliveryWork audit, GiftPayloadCipher cipher, Clock clock) {
        this.jdbc = jdbc; this.work = work; this.audit = audit; this.cipher = cipher; this.clock = clock;
    }
    @Override public Receipt accept(String token, UUID request, String snapshotHash, Runnable saveRecipient) {
        if (request == null || snapshotHash == null || !snapshotHash.matches("[a-f0-9]{64}") || saveRecipient == null)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        UUID order = tokenOrder(token);
        var grant = work.get(order, true).orElseThrow(this::unavailable);
        assertToken(token, order);
        assertActive(grant);
        String binding = cipher.fingerprint("gift-recipient|" + request + "|" + snapshotHash);
        var previous = receipts(order);
        if (!previous.isEmpty()) {
            if (!previous.getFirst().hash().equals(binding)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            var existing = view(order);
            if ("PROVIDED".equals(existing.state()) && !clock.instant().isBefore(existing.accessExpiresAt())) throw unavailable();
            return existing;
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_work WHERE order_id = ? AND first_provided_at IS NOT NULL", Integer.class, order) != 0)
            throw unavailable();
        Instant now = clock.instant();
        if (!now.isBefore(GiftLifetimePolicy.unusedExpiresAt(grant.approvedAt()))) throw unavailable();
        UUID id = UUID.randomUUID();
        saveRecipient.run();
        jdbc.update("INSERT INTO gift_recipient_receipts(id,order_id,request_hash,submitted_at,review_at,state) VALUES (?,?,?,?,?,'ACCEPTED')",
                id, order, binding, Timestamp.from(now), Timestamp.from(now.plus(GiftLifetimePolicy.RECOVERY_REVIEW_AFTER)));
        audit.audit(order, null, null, "RECIPIENT_ACCEPTED", now);
        return view(order);
    }
    @Override public Receipt provide(UUID id, UUID content, Runnable saveContent) {
        if (id == null || content == null || saveContent == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var orders = jdbc.query("SELECT order_id FROM gift_recipient_receipts WHERE id = ?", (rs,row) -> rs.getObject(1, UUID.class), id);
        UUID order = orders.stream().findFirst().orElseThrow(this::unavailable);
        var grant = work.get(order, true).orElseThrow(this::unavailable);
        assertActive(grant);
        var receipt = receipts(order).getFirst();
        if ("PROVIDED".equals(receipt.state())) {
            if (!content.equals(receipt.content())) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            return view(order);
        }
        // Only a durable, timely receipt can resume after the 90-day deadline, including operational review.
        if (!GiftLifetimePolicy.canStart(grant.approvedAt(), receipt.submitted(), clock.instant())) throw unavailable();
        saveContent.run();
        Instant provided = clock.instant();
        jdbc.update("UPDATE gift_purchase_work SET first_provided_at = ?,access_expires_at = ? WHERE order_id = ? AND first_provided_at IS NULL",
                Timestamp.from(provided), Timestamp.from(GiftLifetimePolicy.usedExpiresAt(provided)), order);
        jdbc.update("UPDATE gift_recipient_receipts SET state = 'PROVIDED',content_id = ? WHERE id = ?", content, id);
        audit.audit(order, null, null, "CONTENT_FIRST_PROVIDED", provided);
        return view(order);
    }
    @Override public Receipt link(String token, UUID user) {
        if (user == null || !activeUser(user)) throw unavailable();
        UUID order = tokenOrder(token);
        var grant = work.get(order, true).orElseThrow(this::unavailable);
        assertToken(token, order); assertActive(grant);
        var rows = receipts(order);
        if (rows.isEmpty() || !"PROVIDED".equals(rows.getFirst().state())) throw unavailable();
        var current = view(order);
        if (!clock.instant().isBefore(current.accessExpiresAt())) throw unavailable();
        var linked = rows.getFirst().user();
        if (linked != null && !linked.equals(user)) throw unavailable();
        if (linked == null) {
            jdbc.update("UPDATE gift_recipient_receipts SET recipient_user_id = ?,linked_at = ? WHERE order_id = ?", user, Timestamp.from(clock.instant()), order);
            audit.audit(order, user, null, "RECIPIENT_ACCOUNT_LINKED", clock.instant());
        }
        return current;
    }
    @Override @Transactional(readOnly = true, timeout = 5)
    public Receipt owned(UUID user, UUID gift) {
        if (user == null || gift == null || !activeUser(user)) throw unavailable();
        var orders = jdbc.query("SELECT r.order_id FROM gift_recipient_receipts r JOIN gift_purchase_work w ON w.order_id = r.order_id WHERE r.recipient_user_id = ? AND w.gift_id = ? AND r.state = 'PROVIDED' AND w.status = 'ACTIVE'",
                (rs,row) -> rs.getObject(1, UUID.class), user, gift);
        return view(orders.stream().findFirst().orElseThrow(this::unavailable));
    }
    @Transactional(readOnly = true, timeout = 5)
    public List<UUID> reviewCandidates() {
        return jdbc.query("SELECT order_id FROM gift_recipient_receipts WHERE state = 'ACCEPTED' AND review_at <= ? ORDER BY review_at,order_id LIMIT 20",
                (rs,row) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()));
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, timeout = 10)
    public void review(UUID order) {
        var grant = work.get(order, true).orElseThrow();
        if (!"ACTIVE".equals(grant.status())) return;
        var now = clock.instant();
        int changed = jdbc.update("UPDATE gift_recipient_receipts SET state = 'REVIEW_REQUIRED' WHERE order_id = ? AND state = 'ACCEPTED' AND review_at <= ?", order, Timestamp.from(now));
        if (changed == 1) audit.audit(order, null, null, "CONTENT_RECOVERY_REVIEW", now);
    }
    private Receipt view(UUID order) {
        return jdbc.query("SELECT r.id,w.order_id,w.gift_id,r.state,r.content_id,r.submitted_at,w.first_provided_at,w.access_expires_at FROM gift_recipient_receipts r JOIN gift_purchase_work w ON w.order_id = r.order_id WHERE w.order_id = ?",
                (rs,row) -> new Receipt(rs.getObject(1, UUID.class), order, rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, UUID.class),
                rs.getTimestamp(6).toInstant(), rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant()), order).getFirst();
    }
    private List<Row> receipts(UUID order) {
        return jdbc.query("SELECT request_hash,state,content_id,submitted_at,recipient_user_id FROM gift_recipient_receipts WHERE order_id = ?",
                (rs,row) -> new Row(rs.getString(1),rs.getString(2),rs.getObject(3,UUID.class),rs.getTimestamp(4).toInstant(),rs.getObject(5,UUID.class)), order);
    }
    private UUID tokenOrder(String token) {
        return jdbc.query("SELECT order_id FROM gift_purchase_tokens WHERE token_hash = ? AND revoked = FALSE",
                (rs,row) -> rs.getObject(1, UUID.class), hash(token)).stream().findFirst().orElseThrow(this::unavailable);
    }
    private void assertToken(String token, UUID order) { if (!tokenOrder(token).equals(order)) throw unavailable(); }
    private void assertActive(JdbcGiftPurchaseWork.Work grant) { if (!"ACTIVE".equals(grant.status())) throw unavailable(); }
    private boolean activeUser(UUID user) { return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND status = 'ACTIVE'", Integer.class, user) == 1; }
    private String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private ApiException unavailable() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND); }
    private record Row(String hash,String state,UUID content,Instant submitted,UUID user) {}
}
