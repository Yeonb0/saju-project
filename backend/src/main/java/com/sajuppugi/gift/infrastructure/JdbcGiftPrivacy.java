package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.gift.domain.GiftLifetimePolicy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
    private final Clock clock;
    private final boolean enabled;
    private final Duration period;
    public JdbcGiftPrivacy(JdbcTemplate jdbc, JdbcGiftPurchaseWork work, JdbcGiftDeliveryWork deliveries, Clock clock,
            @Value("${app.gift.retention-enabled:false}") boolean enabled,
            @Value("${app.gift.retention-period:PT0S}") Duration period) {
        this.jdbc = jdbc; this.work = work; this.deliveries = deliveries; this.clock = clock; this.enabled = enabled; this.period = period;
        if (enabled && (period.isZero() || period.isNegative())) throw new IllegalStateException("Gift retention requires an explicitly approved positive period");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<UUID> candidates() {
        if (!enabled) return List.of();
        return jdbc.query("""
                SELECT d.id FROM gift_order_drafts d LEFT JOIN gift_purchase_work w ON w.order_id = d.id
                WHERE d.private_payload_purged_at IS NULL AND d.created_at <= ?
                AND ((w.order_id IS NULL AND d.expires_at <= ?) OR w.status IN ('CANCELED','REFUNDED','FAILED')
                    OR (w.status = 'ACTIVE' AND w.access_expires_at <= ?))
                AND NOT EXISTS (SELECT 1 FROM gift_delivery_jobs j WHERE j.order_id = d.id AND j.state = 'IN_FLIGHT' AND j.lease_until > ?)
                ORDER BY d.created_at,d.id LIMIT 20
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant().minus(period)),
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant().minus(GiftLifetimePolicy.FIRST_ACCESS_GRACE)), Timestamp.from(clock.instant()));
    }
    public boolean purge(UUID order) {
        if (!enabled) return false;
        Instant now = clock.instant();
        var grant = work.get(order, true).orElse(null);
        var drafts = jdbc.query("""
                SELECT created_at,expires_at FROM gift_order_drafts WHERE id = ? AND private_payload_purged_at IS NULL
                FOR NO KEY UPDATE
                """, (rs, row) -> new Draft(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()), order);
        if (drafts.isEmpty() || drafts.getFirst().created().isAfter(now.minus(period))) return false;
        if (grant == null) {
            if (drafts.getFirst().expires().isAfter(now)) return false;
        } else if ("ACTIVE".equals(grant.status())) {
            Timestamp expiry = jdbc.queryForObject("SELECT access_expires_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order);
            if (expiry == null || expiry.toInstant().plus(GiftLifetimePolicy.FIRST_ACCESS_GRACE).isAfter(now)) return false;
            Integer inFlight = jdbc.queryForObject("SELECT COUNT(*) FROM gift_delivery_jobs WHERE order_id = ? AND state = 'IN_FLIGHT' AND lease_until > ?",
                    Integer.class, order, Timestamp.from(now));
            if (inFlight != null && inFlight > 0) return false;
        } else if (!List.of("CANCELED", "REFUNDED", "FAILED").contains(grant.status())) return false;
        jdbc.update("UPDATE gift_purchase_tokens SET revoked = TRUE WHERE order_id = ?", order);
        jdbc.update("UPDATE gift_delivery_jobs SET state = 'CANCELED',lease_token = NULL,lease_until = NULL WHERE order_id = ? AND state NOT IN ('SENT','FAILED','CANCELED')", order);
        jdbc.update("UPDATE gift_delivery_outbox SET private_payload = '',status = 'CANCELED' WHERE order_id = ?", order);
        jdbc.update("UPDATE gift_order_drafts SET private_payload = '',recipient_phone_masked = 'REDACTED',private_payload_purged_at = ? WHERE id = ?", Timestamp.from(now), order);
        if (grant != null) deliveries.audit(order, null, null, "PRIVATE_PAYLOAD_PURGED", now);
        return true;
    }
    private record Draft(Instant created, Instant expires) {}
}
