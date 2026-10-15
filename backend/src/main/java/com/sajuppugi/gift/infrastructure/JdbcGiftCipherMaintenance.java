package com.sajuppugi.gift.infrastructure;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "app.gift.orders-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class JdbcGiftCipherMaintenance {
    private final JdbcTemplate jdbc;
    private final JdbcGiftPurchaseWork work;
    private final JdbcGiftRecipientProfiles recipientProfiles;
    private final GiftPayloadCipher cipher;
    private final Clock clock;
    public JdbcGiftCipherMaintenance(JdbcTemplate jdbc, JdbcGiftPurchaseWork work,
            JdbcGiftRecipientProfiles recipientProfiles, GiftPayloadCipher cipher, Clock clock) {
        this.jdbc = jdbc; this.work = work; this.recipientProfiles = recipientProfiles; this.cipher = cipher; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<UUID> candidates() {
        if (!cipher.versionedActive()) return List.of();
        String prefix = cipher.activePrefix();
        return jdbc.query("""
                SELECT d.id FROM gift_order_drafts d LEFT JOIN gift_delivery_outbox o ON o.order_id = d.id
                LEFT JOIN gift_purchase_work w ON w.order_id = d.id
                LEFT JOIN gift_recipient_profiles p ON p.gift_id = w.gift_id
                LEFT JOIN gift_purchase_tokens t ON t.order_id = d.id
                LEFT JOIN gift_payload_reencryption_work r ON r.order_id = d.id
                WHERE ((d.private_payload <> '' AND LEFT(d.private_payload,?) <> ?)
                OR (o.private_payload <> '' AND LEFT(o.private_payload,?) <> ?)
                OR (p.profile_ciphertext IS NOT NULL AND p.profile_ciphertext <> '' AND LEFT(p.profile_ciphertext,?) <> ?)
                OR (t.token_ciphertext IS NOT NULL AND LEFT(t.token_ciphertext,?) <> ?))
                AND (r.order_id IS NULL OR r.active_key_id <> ? OR (r.state = 'RETRY' AND r.next_attempt_at <= ?))
                ORDER BY d.created_at,d.id LIMIT 20
                """, (rs, row) -> rs.getObject(1, UUID.class), prefix.length(), prefix, prefix.length(), prefix,
                prefix.length(), prefix, prefix.length(), prefix, cipher.activeKeyId(), Timestamp.from(clock.instant()));
    }
    public boolean reencrypt(UUID order) {
        if (!cipher.versionedActive()) return false;
        var grant = work.get(order, true).orElse(null);
        var rows = jdbc.query("SELECT user_id,private_payload FROM gift_order_drafts WHERE id = ? FOR NO KEY UPDATE",
                (rs, row) -> new Draft(rs.getObject(1, UUID.class), rs.getString(2)), order);
        if (rows.isEmpty()) return false;
        var draft = rows.getFirst(); boolean changed = false;
        if (!draft.payload().isEmpty() && !draft.payload().startsWith(cipher.activePrefix())) {
            jdbc.update("UPDATE gift_order_drafts SET private_payload = ? WHERE id = ?",
                    cipher.encrypt(cipher.decrypt(draft.payload(), draft.owner() + "|" + order), draft.owner() + "|" + order), order);
            changed = true;
        }
        var outboxes = jdbc.query("SELECT id,private_payload FROM gift_delivery_outbox WHERE order_id = ? FOR UPDATE",
                (rs, row) -> new Box(rs.getObject(1, UUID.class), rs.getString(2)), order);
        if (!outboxes.isEmpty()) {
            var box = outboxes.getFirst();
            if (!box.payload().isEmpty() && !box.payload().startsWith(cipher.activePrefix())) {
                String context = "gift-delivery|" + box.id();
                jdbc.update("UPDATE gift_delivery_outbox SET private_payload = ? WHERE id = ?",
                        cipher.encrypt(cipher.decrypt(box.payload(), context), context), box.id()); changed = true;
            }
        }
        changed = recipientProfiles.reencryptForOrder(order) || changed;
        var tokens = jdbc.query("SELECT token_version,token_ciphertext FROM gift_purchase_tokens WHERE order_id = ? FOR UPDATE",
                (rs, row) -> new Token(rs.getLong(1), rs.getString(2)), order);
        if (!tokens.isEmpty()) {
            var token = tokens.getFirst();
            if (token.payload() != null && !token.payload().startsWith(cipher.activePrefix())) {
                String context = "gift-link|" + order + "|" + token.version();
                jdbc.update("UPDATE gift_purchase_tokens SET token_ciphertext = ? WHERE order_id = ?",
                        cipher.encrypt(cipher.decrypt(token.payload(), context), context), order);
                changed = true;
            }
        }
        if (changed && grant != null) jdbc.update("INSERT INTO gift_delivery_events(id,order_id,action,created_at) VALUES (?,?,'PAYLOAD_REENCRYPTED',CURRENT_TIMESTAMP)", UUID.randomUUID(), order);
        jdbc.update("DELETE FROM gift_payload_reencryption_work WHERE order_id = ?", order);
        return changed;
    }
    public void failed(UUID order) {
        if (!cipher.versionedActive()) return;
        var ids = jdbc.query("SELECT id FROM gift_order_drafts WHERE id = ? FOR NO KEY UPDATE", (rs, row) -> rs.getObject(1, UUID.class), order);
        if (ids.isEmpty()) return;
        var rows = jdbc.query("SELECT active_key_id,failures,next_attempt_at,state FROM gift_payload_reencryption_work WHERE order_id = ? FOR UPDATE",
                (rs, row) -> new Failure(rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toInstant(), rs.getString(4)), order);
        int failures = 0;
        if (!rows.isEmpty() && rows.getFirst().key().equals(cipher.activeKeyId())) {
            var previous = rows.getFirst();
            if ("REVIEW_REQUIRED".equals(previous.state()) || previous.next().isAfter(clock.instant())) return;
            failures = previous.failures();
        }
        failures++;
        jdbc.update("""
                INSERT INTO gift_payload_reencryption_work(order_id,active_key_id,failures,state,next_attempt_at,updated_at)
                VALUES (?,?,?,?,?,?) ON CONFLICT (order_id) DO UPDATE SET active_key_id = EXCLUDED.active_key_id,
                failures = EXCLUDED.failures,state = EXCLUDED.state,next_attempt_at = EXCLUDED.next_attempt_at,updated_at = EXCLUDED.updated_at
                """, order, cipher.activeKeyId(), failures, failures >= 5 ? "REVIEW_REQUIRED" : "RETRY",
                Timestamp.from(clock.instant().plusSeconds(Math.min(600, 30L << (failures - 1)))), Timestamp.from(clock.instant()));
    }
    private record Draft(UUID owner, String payload) {}
    private record Box(UUID id, String payload) {}
    private record Token(long version, String payload) {}
    private record Failure(String key, int failures, java.time.Instant next, String state) {}
}
