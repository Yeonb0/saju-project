package com.sajuppugi.gift.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
public class JdbcGiftPrivacy {
    private final JdbcTemplate jdbc;
    private final JdbcGiftPurchaseWork work;
    private final JdbcGiftDeliveryWork deliveries;
    private final JdbcGiftRecipientProfiles recipientProfiles;
    private final Clock clock;
    private final boolean enabled;
    public JdbcGiftPrivacy(JdbcTemplate jdbc, JdbcGiftPurchaseWork work, JdbcGiftDeliveryWork deliveries,
            JdbcGiftRecipientProfiles recipientProfiles, Clock clock,
            @Value("${app.gift.retention-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.work = work; this.deliveries = deliveries; this.recipientProfiles = recipientProfiles;
        this.clock = clock; this.enabled = enabled;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<UUID> candidates() {
        if (!enabled) return List.of();
        return jdbc.query("""
                SELECT d.id FROM gift_order_drafts d LEFT JOIN gift_purchase_work w ON w.order_id = d.id
                WHERE d.private_payload_purged_at IS NULL
                AND d.private_cleanup_failures < 5 AND (d.private_cleanup_next_attempt_at IS NULL OR d.private_cleanup_next_attempt_at <= ?)
                AND ((w.order_id IS NULL AND d.expires_at <= ?) OR w.status IN ('CANCELED','REFUNDED','FAILED')
                    OR (w.status = 'ACTIVE' AND (w.access_expires_at <= ? OR EXISTS
                        (SELECT 1 FROM gift_recipient_receipts r JOIN users u ON u.id = r.recipient_user_id
                            WHERE r.order_id = d.id AND r.state = 'PROVIDED' AND u.status <> 'ACTIVE'))))
                AND (w.status <> 'ACTIVE' OR w.order_id IS NULL OR NOT EXISTS (SELECT 1 FROM gift_recipient_receipts r WHERE r.order_id = d.id
                    AND (r.state IN ('ACCEPTED','REVIEW_REQUIRED') OR EXISTS
                        (SELECT 1 FROM users u WHERE u.id = r.recipient_user_id AND u.status = 'ACTIVE'))))
                AND NOT EXISTS (SELECT 1 FROM gift_delivery_jobs j WHERE j.order_id = d.id AND j.state = 'IN_FLIGHT' AND j.lease_until > ?)
                ORDER BY d.created_at,d.id LIMIT 20
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }
    public boolean purge(UUID order) {
        if (!enabled) return false;
        Instant now = clock.instant();
        var grant = work.get(order, true).orElse(null);
        var drafts = jdbc.query("""
                SELECT created_at,expires_at FROM gift_order_drafts WHERE id = ? AND private_payload_purged_at IS NULL
                FOR NO KEY UPDATE
                """, (rs, row) -> new Draft(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()), order);
        if (drafts.isEmpty()) return false;
        if (grant == null) {
            if (drafts.getFirst().expires().isAfter(now)) return false;
        } else if ("ACTIVE".equals(grant.status())) {
            Timestamp expiry = jdbc.queryForObject("SELECT access_expires_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order);
            boolean withdrawn = jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_receipts r JOIN users u ON u.id = r.recipient_user_id WHERE r.order_id = ? AND r.state = 'PROVIDED' AND u.status <> 'ACTIVE'", Integer.class, order) > 0;
            if (!withdrawn && (expiry == null || expiry.toInstant().isAfter(now))) return false;
            Integer protectedReceipts = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM gift_recipient_receipts r WHERE r.order_id = ?
                    AND (r.state IN ('ACCEPTED','REVIEW_REQUIRED') OR EXISTS
                        (SELECT 1 FROM users u WHERE u.id = r.recipient_user_id AND u.status = 'ACTIVE'))
                    """, Integer.class, order);
            if (protectedReceipts != null && protectedReceipts > 0) return false;
            Integer inFlight = jdbc.queryForObject("SELECT COUNT(*) FROM gift_delivery_jobs WHERE order_id = ? AND state = 'IN_FLIGHT' AND lease_until > ?",
                    Integer.class, order, Timestamp.from(now));
            if (inFlight != null && inFlight > 0) return false;
        } else if (!List.of("CANCELED", "REFUNDED", "FAILED").contains(grant.status())) return false;
        if (grant != null && grant.giftId() != null) {
            Integer fulfillmentInFlight = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM gift_fulfillment_jobs
                    WHERE gift_id = ? AND state = 'GENERATING' AND lease_until > ?
                    """, Integer.class, grant.giftId(), Timestamp.from(now));
            if (fulfillmentInFlight != null && fulfillmentInFlight > 0) return false;
        }
        jdbc.update("UPDATE gift_purchase_tokens SET revoked = TRUE,token_ciphertext = NULL WHERE order_id = ?", order);
        jdbc.update("UPDATE gift_delivery_jobs SET state = 'CANCELED',lease_token = NULL,lease_until = NULL WHERE order_id = ? AND state NOT IN ('SENT','FAILED','CANCELED')", order);
        jdbc.update("UPDATE gift_delivery_outbox SET private_payload = '',status = 'CANCELED' WHERE order_id = ?", order);
        jdbc.update("UPDATE gift_order_drafts SET private_payload = '',recipient_phone_masked = 'REDACTED',private_payload_purged_at = ? WHERE id = ?", Timestamp.from(now), order);
        if (grant != null && grant.giftId() != null) recipientProfiles.purgeForGift(grant.giftId(), now);
        if (grant != null) deliveries.audit(order, null, null, "PRIVATE_PAYLOAD_PURGED", now);
        return true;
    }
    public void failed(UUID order) {
        if (!enabled) return;
        var now = clock.instant();
        var rows = jdbc.query("SELECT private_cleanup_failures FROM gift_order_drafts WHERE id = ? AND private_payload_purged_at IS NULL AND private_cleanup_failures < 5 AND (private_cleanup_next_attempt_at IS NULL OR private_cleanup_next_attempt_at <= ?) FOR NO KEY UPDATE",
                (rs,row) -> rs.getInt(1), order, Timestamp.from(now));
        if (rows.isEmpty()) return;
        int failures = rows.getFirst() + 1;
        jdbc.update("UPDATE gift_order_drafts SET private_cleanup_failures = ?,private_cleanup_next_attempt_at = ? WHERE id = ?",
                failures, Timestamp.from(now.plusSeconds(Math.min(600, 30L << (failures - 1)))), order);
    }
    private record Draft(Instant created, Instant expires) {}
}
