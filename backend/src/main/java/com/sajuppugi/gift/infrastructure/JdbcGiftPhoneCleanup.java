package com.sajuppugi.gift.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.gift.domain.GiftDeliveryPolicy;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class JdbcGiftPhoneCleanup {
    private final JdbcTemplate jdbc;
    private final JdbcGiftPurchaseWork work;
    private final JdbcGiftDeliveryWork delivery;
    private final GiftPayloadCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean enabled;
    public JdbcGiftPhoneCleanup(JdbcTemplate jdbc, JdbcGiftPurchaseWork work, JdbcGiftDeliveryWork delivery,
            GiftPayloadCipher cipher, ObjectMapper mapper, Clock clock,
            @Value("${app.gift.phone-cleanup-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.work = work; this.delivery = delivery; this.cipher = cipher;
        this.mapper = mapper; this.clock = clock; this.enabled = enabled;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<UUID> candidates() {
        if (!enabled) return List.of();
        return jdbc.query("""
                SELECT d.id FROM gift_order_drafts d JOIN gift_purchase_work w ON w.order_id = d.id
                WHERE d.recipient_phone_masked IS NOT NULL AND d.phone_purged_at IS NULL AND (w.approved_at <= ? OR w.status IN ('CANCELED','REFUNDED','FAILED'))
                AND d.phone_cleanup_failures < 5 AND (d.phone_cleanup_next_attempt_at IS NULL OR d.phone_cleanup_next_attempt_at <= ?)
                ORDER BY d.created_at,d.id LIMIT 20
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant().minus(GiftDeliveryPolicy.DELIVERY_VALIDITY)), Timestamp.from(clock.instant()));
    }
    public boolean purge(UUID order) {
        if (!enabled) return false;
        var grant = work.get(order, true).orElse(null);
        if (grant == null) return false;
        var now = clock.instant();
        boolean terminal = List.of("CANCELED", "REFUNDED", "FAILED").contains(grant.status());
        if (!terminal && (grant.approvedAt() == null || grant.approvedAt().plus(GiftDeliveryPolicy.DELIVERY_VALIDITY).isAfter(now))) return false;
        var rows = jdbc.query("SELECT user_id,private_payload FROM gift_order_drafts WHERE id = ? AND recipient_phone_masked IS NOT NULL AND phone_purged_at IS NULL FOR NO KEY UPDATE",
                (rs, row) -> new Draft(rs.getObject(1, UUID.class), rs.getString(2)), order);
        if (rows.isEmpty()) return false;
        var draft = rows.getFirst(); String cleaned = draft.payload();
        if (!cleaned.isEmpty()) {
            String context = draft.owner() + "|" + order;
            try {
                ObjectNode payload = (ObjectNode) mapper.readTree(cipher.decrypt(cleaned, context));
                payload.remove("recipientPhone");
                cleaned = cipher.encrypt(mapper.writeValueAsString(payload), context);
            } catch (Exception invalid) { throw new IllegalStateException("Gift phone cleanup failed"); }
        }
        // Submitted jobs still need outcome lookup; no raw recipient data is needed for lookup by job ID.
        jdbc.update("UPDATE gift_delivery_jobs SET state = 'CANCELED',completed_at = ?,lease_token = NULL,lease_until = NULL WHERE order_id = ? AND submission_started = FALSE AND state IN ('PENDING','RETRY')",
                Timestamp.from(now), order);
        jdbc.update("UPDATE gift_delivery_outbox SET private_payload = '' WHERE order_id = ?", order);
        jdbc.update("UPDATE gift_order_drafts SET private_payload = ?,recipient_phone_masked = 'REDACTED',phone_purged_at = ? WHERE id = ?",
                cleaned, Timestamp.from(now), order);
        delivery.audit(order, null, null, "RECIPIENT_PHONE_PURGED", now);
        return true;
    }
    public void failed(UUID order) {
        if (!enabled) return;
        var now = clock.instant();
        var rows = jdbc.query("SELECT phone_cleanup_failures FROM gift_order_drafts WHERE id = ? AND recipient_phone_masked IS NOT NULL AND phone_purged_at IS NULL AND phone_cleanup_failures < 5 AND (phone_cleanup_next_attempt_at IS NULL OR phone_cleanup_next_attempt_at <= ?) FOR NO KEY UPDATE",
                (rs, row) -> rs.getInt(1), order, Timestamp.from(now));
        if (rows.isEmpty()) return;
        int failures = rows.getFirst() + 1;
        jdbc.update("UPDATE gift_order_drafts SET phone_cleanup_failures = ?,phone_cleanup_next_attempt_at = ? WHERE id = ?",
                failures, Timestamp.from(now.plusSeconds(Math.min(600, 30L << (failures - 1)))), order);
    }
    private record Draft(UUID owner, String payload) {}
}
