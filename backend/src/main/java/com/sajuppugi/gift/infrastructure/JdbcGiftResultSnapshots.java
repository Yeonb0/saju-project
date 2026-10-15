package com.sajuppugi.gift.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Immutable, non-identifying result projection. The worker supplies only the already-generated result JSON. */
@Repository
public class JdbcGiftResultSnapshots {
    private final JdbcTemplate jdbc;

    public JdbcGiftResultSnapshots(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<Snapshot> find(UUID giftId) {
        return jdbc.query("""
                SELECT s.id,s.gift_id,s.receipt_id,s.content_id,s.product_code,s.content_version,s.generation_version,s.result_json,s.result_hash,s.created_at
                FROM gift_result_snapshots s
                JOIN gift_recipient_receipts r ON r.id = s.receipt_id
                JOIN gift_purchase_work w ON w.order_id = r.order_id AND w.gift_id = s.gift_id
                WHERE s.gift_id = ?
                """, (rs, row) -> new Snapshot(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getString(9), rs.getTimestamp(10).toInstant()), giftId).stream().findFirst();
    }

    /**
     * Called inside {@link com.sajuppugi.gift.application.GiftFulfillmentPort#provide}; duplicate workers may
     * repeat the same snapshot but can never replace a completed result with different content.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Snapshot snapshot) {
        Integer receiptMatchesGift = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gift_recipient_receipts r
                JOIN gift_purchase_work w ON w.order_id = r.order_id
                WHERE r.id = ? AND w.gift_id = ?
                """, Integer.class, snapshot.receiptId(), snapshot.giftId());
        if (receiptMatchesGift == null || receiptMatchesGift != 1) {
            throw new IllegalArgumentException("Gift result receipt does not belong to gift");
        }
        int inserted = jdbc.update("""
                INSERT INTO gift_result_snapshots
                    (id,gift_id,receipt_id,content_id,product_code,content_version,generation_version,result_json,result_hash,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (gift_id) DO NOTHING
                """, snapshot.id(), snapshot.giftId(), snapshot.receiptId(), snapshot.contentId(), snapshot.productCode(),
                snapshot.contentVersion(), snapshot.generationVersion(), snapshot.resultJson(), snapshot.resultHash(),
                Timestamp.from(snapshot.createdAt()));
        if (inserted == 0) {
            var existing = find(snapshot.giftId()).orElseThrow();
            if (!existing.resultHash().equals(snapshot.resultHash()) || !existing.contentId().equals(snapshot.contentId()))
                throw new IllegalStateException("Gift result snapshot conflict");
        }
    }

    public record Snapshot(UUID id, UUID giftId, UUID receiptId, UUID contentId, String productCode,
                           String contentVersion, String generationVersion, String resultJson, String resultHash,
                           Instant createdAt) {}
}
