package com.sajuppugi.fortune.talisman.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.FiveElements;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.port.TalismanAddonPurchaseRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTalismanAddonPurchaseRepository implements TalismanAddonPurchaseRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcTalismanAddonPurchaseRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override public Optional<Claim> findByReadingId(UUID readingId) {
        return jdbc.query("SELECT * FROM talisman_addon_purchases WHERE reading_id = ?", (rs, row) -> claim(rs), readingId)
                .stream().findFirst();
    }

    @Override public Claim insert(Claim claim) {
        int inserted = jdbc.update("""
                    INSERT INTO talisman_addon_purchases (id, reading_id, owner_user_id, reading_only_product_code,
                    bundled_product_code, quoted_amount, quote_id, wallet_transaction_id, state, talisman_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (reading_id) DO NOTHING
                    """, claim.id(), claim.readingId(), claim.ownerUserId(), claim.readingOnlyProductCode(),
                    claim.bundledProductCode(), claim.quotedAmount(), claim.quoteId(), claim.walletTransactionId(),
                    claim.state(), claim.talismanId(), Timestamp.from(claim.createdAt()), Timestamp.from(claim.updatedAt()));
        return inserted == 1 ? claim : findByReadingId(claim.readingId()).orElseThrow();
    }

    @Override public void markPendingTalisman(UUID purchaseId, UUID talismanId, Instant updatedAt) {
        int linked = jdbc.update("UPDATE readings SET talisman_id = ?, talisman_status = 'PENDING' WHERE id = ? "
                        + "AND talisman_id IS NULL", talismanId,
                jdbc.queryForObject("SELECT reading_id FROM talisman_addon_purchases WHERE id = ?", UUID.class, purchaseId));
        if (linked != 1) throw new IllegalStateException("Reading changed before talisman was linked");
        int count = jdbc.update("""
                UPDATE talisman_addon_purchases SET state = 'PENDING_TALISMAN', talisman_id = ?, updated_at = ?
                WHERE id = ? AND state = 'DEBITED' AND talisman_id IS NULL
                """, talismanId, Timestamp.from(updatedAt), purchaseId);
        if (count != 1) throw new IllegalStateException("Addon purchase changed before talisman was linked");
    }

    @Override public void markDebited(UUID purchaseId, UUID walletTransactionId, Instant updatedAt) {
        int count = jdbc.update("""
                UPDATE talisman_addon_purchases SET state = 'DEBITED', wallet_transaction_id = ?, updated_at = ?
                WHERE id = ? AND state = 'QUOTED' AND wallet_transaction_id IS NULL
                """, walletTransactionId, Timestamp.from(updatedAt), purchaseId);
        if (count != 1) throw new IllegalStateException("Addon purchase changed before debit was linked");
    }
    @Override public void markDirectPaid(UUID purchaseId, Instant updatedAt) {
        int count = jdbc.update("UPDATE talisman_addon_purchases SET state='DEBITED', updated_at=? WHERE id=? AND state='QUOTED' AND wallet_transaction_id IS NULL",
                Timestamp.from(updatedAt), purchaseId);
        if (count != 1) throw new IllegalStateException("Addon purchase changed before direct payment was linked");
    }

    @Override public Optional<StoredReading> findOwnedEligibleReading(UUID ownerUserId, UUID readingId) {
        return jdbc.query("""
                SELECT r.id, r.owner_user_id, r.fortune_type, r.content_version, rr.facts_json
                FROM readings r JOIN reading_results rr ON rr.id = r.reading_result_id
                WHERE r.id = ? AND r.owner_user_id = ? AND r.product_option = 'READING_ONLY'
                  AND r.talisman_id IS NULL AND rr.status = 'SUCCEEDED'
                FOR UPDATE OF r
                """, (rs, row) -> storedReading(rs), readingId, ownerUserId).stream().findFirst();
    }

    private Claim claim(ResultSet rs) throws SQLException {
        return new Claim(rs.getObject("id", UUID.class), rs.getObject("reading_id", UUID.class),
                rs.getObject("owner_user_id", UUID.class), rs.getString("reading_only_product_code"),
                rs.getString("bundled_product_code"), rs.getInt("quoted_amount"), rs.getObject("quote_id", UUID.class),
                rs.getObject("wallet_transaction_id", UUID.class), rs.getString("state"), rs.getObject("talisman_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private StoredReading storedReading(ResultSet rs) throws SQLException {
        try {
            FiveElements fiveElements = mapper.treeToValue(mapper.readTree(rs.getString("facts_json")).path("fiveElements"),
                    FiveElements.class);
            return new StoredReading(rs.getObject("id", UUID.class), rs.getObject("owner_user_id", UUID.class),
                    FortuneType.valueOf(rs.getString("fortune_type")), rs.getString("content_version"), fiveElements);
        } catch (Exception invalid) {
            throw new SQLException("Invalid stored reading facts", invalid);
        }
    }
}
