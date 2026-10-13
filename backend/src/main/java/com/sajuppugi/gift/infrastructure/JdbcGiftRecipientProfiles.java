package com.sajuppugi.gift.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcGiftRecipientProfiles {
    private final JdbcTemplate jdbc;

    public JdbcGiftRecipientProfiles(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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
}
