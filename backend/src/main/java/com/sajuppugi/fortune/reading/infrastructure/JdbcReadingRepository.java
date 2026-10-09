package com.sajuppugi.fortune.reading.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReadingRepository implements ReadingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcReadingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void createPurchase(ReadingPurchase purchase) {
        jdbc.update("""
                INSERT INTO reading_purchases (id, buyer_user_id, product_id, quote_id, subject_person_id,
                counterpart_person_id, relation_type, question_key, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, purchase.id(), purchase.buyerUserId(), purchase.productId(), purchase.quoteId(),
                purchase.subjectPersonId(), purchase.counterpartPersonId(), purchase.relationType(),
                purchase.questionKey(), purchase.status().name(), Timestamp.from(purchase.createdAt()));
    }

    @Override
    public Optional<ReadingPurchase> findPurchaseByQuote(UUID buyerUserId, UUID quoteId) {
        return jdbc.query("SELECT * FROM reading_purchases WHERE buyer_user_id = ? AND quote_id = ?",
                (rs, row) -> purchase(rs), buyerUserId, quoteId).stream().findFirst();
    }

    @Override
    public boolean tryClaimDebit(UUID purchaseId, UUID claimToken, Instant claimedAt, Instant staleBefore) {
        return jdbc.update("""
                UPDATE reading_purchases SET debit_claim_token = ?, debit_claimed_at = ?
                WHERE id = ? AND status = 'CREATED'
                AND (debit_claim_token IS NULL OR debit_claimed_at < ?)
                """, claimToken, Timestamp.from(claimedAt), purchaseId, Timestamp.from(staleBefore)) == 1;
    }

    @Override
    public void releaseDebitClaim(UUID purchaseId, UUID claimToken) {
        jdbc.update("""
                UPDATE reading_purchases SET debit_claim_token = NULL, debit_claimed_at = NULL
                WHERE id = ? AND status = 'CREATED' AND debit_claim_token = ?
                """, purchaseId, claimToken);
    }

    @Override
    public void markDebited(UUID purchaseId, UUID claimToken, UUID walletTransactionId) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'DEBITED', wallet_transaction_id = ?,
                debit_claim_token = NULL, debit_claimed_at = NULL
                WHERE id = ? AND status = 'CREATED' AND debit_claim_token = ?
                """, walletTransactionId, purchaseId, claimToken), "mark purchase debited");
    }

    @Override
    public void markGenerating(UUID purchaseId) {
        requireOne(jdbc.update("UPDATE reading_purchases SET status = 'GENERATING' WHERE id = ? AND status = 'DEBITED'",
                purchaseId), "mark purchase generating");
    }

    @Override
    @Transactional
    public void fulfill(ReadingPurchase purchase, OwnedReading reading) {
        jdbc.update("""
                INSERT INTO readings (id, reading_result_id, owner_user_id, purchase_id, fortune_type,
                product_option, subject_person_id, subject_display_name, counterpart_person_id,
                counterpart_display_name, relation_type, question_key, event_date, public_snapshot,
                calculation_version, generation_version, content_version, generation_mode, talisman_id,
                talisman_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reading.id(), reading.resultId(), reading.ownerUserId(), reading.purchaseId(),
                reading.fortuneType().name(), reading.productOption().name(), reading.subjectPersonId(),
                reading.subjectDisplayName(), reading.counterpartPersonId(), reading.counterpartDisplayName(),
                reading.relationType(), reading.questionKey(), reading.eventDate(), json(reading.sections()), reading.calculationVersion(),
                reading.generationVersion(), reading.contentVersion(), reading.generationMode().name(),
                reading.talismanId(), reading.talismanStatus(),
                Timestamp.from(reading.createdAt()));
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'FULFILLED', reading_id = ?, fulfilled_at = ?
                WHERE id = ? AND status = 'GENERATING'
                """, reading.id(), Timestamp.from(reading.createdAt()), purchase.id()), "fulfill purchase");
    }

    @Override
    public void markFailed(UUID purchaseId) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'FAILED'
                WHERE id = ? AND status IN ('DEBITED', 'GENERATING')
                """, purchaseId), "mark purchase failed");
    }

    @Override
    public void markRefunded(UUID purchaseId) {
        requireOne(jdbc.update("UPDATE reading_purchases SET status = 'REFUNDED' WHERE id = ? AND status IN ('FAILED', 'REFUNDED')",
                purchaseId), "mark purchase refunded");
    }

    @Override
    public Optional<OwnedReading> findOwnedReading(UUID ownerUserId, UUID readingId) {
        return jdbc.query("""
                SELECT r.*, t.status AS current_talisman_status
                FROM readings r LEFT JOIN talismans t ON t.id = r.talisman_id
                WHERE r.owner_user_id = ? AND r.id = ?
                """,
                (rs, row) -> reading(rs), ownerUserId, readingId).stream().findFirst();
    }

    @Override
    public List<OwnedReading> findOwnedReadings(UUID ownerUserId, FortuneType fortuneType, UUID personId,
                                                Instant beforeCreatedAt, UUID beforeId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.*, t.status AS current_talisman_status
                FROM readings r LEFT JOIN talismans t ON t.id = r.talisman_id
                WHERE r.owner_user_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(ownerUserId);
        if (fortuneType != null) {
            sql.append(" AND r.fortune_type = ?");
            args.add(fortuneType.name());
        }
        if (personId != null) {
            sql.append(" AND r.subject_person_id = ?");
            args.add(personId);
        }
        if (beforeCreatedAt != null) {
            sql.append(" AND (r.created_at < ? OR (r.created_at = ? AND r.id < ?))");
            Timestamp cursorTime = Timestamp.from(beforeCreatedAt);
            args.add(cursorTime);
            args.add(cursorTime);
            args.add(beforeId);
        }
        sql.append(" ORDER BY r.created_at DESC, r.id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, row) -> reading(rs), args.toArray());
    }

    private ReadingPurchase purchase(ResultSet rs) throws SQLException {
        return new ReadingPurchase(rs.getObject("id", UUID.class), rs.getObject("buyer_user_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getObject("subject_person_id", UUID.class), rs.getObject("counterpart_person_id", UUID.class),
                rs.getString("relation_type"), rs.getString("question_key"),
                rs.getObject("wallet_transaction_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getObject("reading_id", UUID.class),
                instant(rs, "created_at"), instant(rs, "fulfilled_at"));
    }

    private OwnedReading reading(ResultSet rs) throws SQLException {
        try {
            String currentTalismanStatus = rs.getString("current_talisman_status");
            return new OwnedReading(rs.getObject("id", UUID.class), rs.getObject("reading_result_id", UUID.class),
                    rs.getObject("owner_user_id", UUID.class), rs.getObject("purchase_id", UUID.class),
                    FortuneType.valueOf(rs.getString("fortune_type")), ProductOption.valueOf(rs.getString("product_option")),
                    rs.getObject("subject_person_id", UUID.class), rs.getString("subject_display_name"),
                    rs.getObject("counterpart_person_id", UUID.class), rs.getString("counterpart_display_name"),
                    rs.getString("relation_type"), rs.getString("question_key"),
                    rs.getObject("event_date", java.time.LocalDate.class),
                    mapper.readValue(rs.getString("public_snapshot"), LinerResponse.class),
                    rs.getString("calculation_version"), rs.getString("generation_version"),
                    rs.getString("content_version"), GenerationMode.valueOf(rs.getString("generation_mode")),
                    rs.getObject("talisman_id", UUID.class),
                    currentTalismanStatus == null ? rs.getString("talisman_status") : currentTalismanStatus,
                    instant(rs, "created_at"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid reading snapshot", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize reading snapshot", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private void requireOne(int updated, String operation) {
        if (updated != 1) throw new IllegalStateException("Cannot " + operation + " from current state");
    }
}
