package com.sajuppugi.gift.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcGiftRecipientProfiles {
    private final JdbcTemplate jdbc;
    private final GiftPayloadCipher cipher;

    public JdbcGiftRecipientProfiles(JdbcTemplate jdbc, GiftPayloadCipher cipher) {
        this.jdbc = jdbc; this.cipher = cipher;
    }

    public void insert(UUID profileId, UUID giftId, String ciphertext, String keyId, String snapshotHash,
                       String productCode, Instant now) {
        jdbc.update("""
                INSERT INTO gift_recipient_profiles
                    (id,gift_id,profile_ciphertext,encryption_key_id,snapshot_hash,submitted_at)
                VALUES (?,?,?,?,?,?)
                """, profileId, giftId, ciphertext, keyId, snapshotHash, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO gift_fulfillment_jobs
                    (id,gift_id,profile_id,product_code,state,next_attempt_at,created_at,updated_at)
                VALUES (?,?,?,?,'PENDING',?,?,?)
                """, UUID.randomUUID(), giftId, profileId, productCode, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now));
    }

    public String productCode(UUID giftId) {
        return jdbc.query("""
                SELECT d.product_code FROM gift_purchase_work w
                JOIN gift_order_drafts d ON d.id = w.order_id
                WHERE w.gift_id = ?
                """, (rs, row) -> rs.getString(1), giftId).stream().findFirst().orElseThrow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public Optional<WorkerProfile> loadForWorker(UUID profileId, UUID giftId) {
        return jdbc.query("""
                SELECT p.id,p.gift_id,p.profile_ciphertext,w.product_code,r.id
                FROM gift_recipient_profiles p
                JOIN gift_fulfillment_jobs j ON j.profile_id = p.id AND j.gift_id = p.gift_id
                JOIN gift_purchase_work w ON w.gift_id = p.gift_id AND w.status = 'ACTIVE'
                JOIN gift_recipient_receipts r ON r.order_id = w.order_id AND r.state IN ('ACCEPTED','REVIEW_REQUIRED')
                WHERE p.id = ? AND p.gift_id = ? AND p.profile_ciphertext IS NOT NULL
                """, (rs, row) -> new WorkerProfile(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class)), profileId, giftId).stream().findFirst();
    }

    /**
     * The surrounding privacy transaction has established that the gift's purpose has ended. Retain only
     * non-personal operational state and make the encrypted recipient snapshot unavailable to any worker.
     */
    public void purgeForGift(UUID giftId, Instant now) {
        jdbc.update("""
                UPDATE gift_fulfillment_jobs
                SET state = 'PURGED', lease_token = NULL, lease_until = NULL, updated_at = ?
                WHERE gift_id = ? AND state NOT IN ('READY','FAILED','CANCELED','PURGED')
                """, Timestamp.from(now), giftId);
        jdbc.update("""
                UPDATE gift_recipient_profiles
                SET profile_ciphertext = NULL, purged_at = ?, purge_policy_version = 'gift-purpose-ended-v1'
                WHERE gift_id = ? AND profile_ciphertext IS NOT NULL
                """, Timestamp.from(now), giftId);
    }

    /** Called by the existing key-rotation transaction; never revives a purged profile. */
    public boolean reencryptForOrder(UUID orderId) {
        if (!cipher.versionedActive()) return false;
        var rows = jdbc.query("""
                SELECT p.id,p.gift_id,p.profile_ciphertext FROM gift_recipient_profiles p
                JOIN gift_purchase_work w ON w.gift_id = p.gift_id
                WHERE w.order_id = ? AND p.profile_ciphertext IS NOT NULL AND p.profile_ciphertext <> ''
                FOR UPDATE
                """, (rs, row) -> new Profile(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)), orderId);
        if (rows.isEmpty()) return false;
        var profile = rows.getFirst();
        if (profile.ciphertext().startsWith(cipher.activePrefix())) return false;
        String context = "gift-recipient-profile|" + profile.giftId() + "|" + profile.id();
        jdbc.update("UPDATE gift_recipient_profiles SET profile_ciphertext = ?, encryption_key_id = ? WHERE id = ?",
                cipher.encrypt(cipher.decrypt(profile.ciphertext(), context), context), cipher.activeKeyId(), profile.id());
        return true;
    }

    private record Profile(UUID id, UUID giftId, String ciphertext) {}
    public record WorkerProfile(UUID profileId, UUID giftId, String ciphertext, String productCode, UUID receiptId) {}
}
