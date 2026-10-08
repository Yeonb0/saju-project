package com.sajuppugi.fortune.reading.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
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
                status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, purchase.id(), purchase.buyerUserId(), purchase.productId(), purchase.quoteId(),
                purchase.subjectPersonId(), purchase.status().name(), Timestamp.from(purchase.createdAt()));
    }

    @Override
    public Optional<ReadingPurchase> findPurchaseByQuote(UUID buyerUserId, UUID quoteId) {
        return jdbc.query("SELECT * FROM reading_purchases WHERE buyer_user_id = ? AND quote_id = ?",
                (rs, row) -> purchase(rs), buyerUserId, quoteId).stream().findFirst();
    }

    @Override
    public void markDebited(UUID purchaseId, UUID walletTransactionId) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'DEBITED', wallet_transaction_id = ?
                WHERE id = ? AND status = 'CREATED'
                """, walletTransactionId, purchaseId), "mark purchase debited");
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
                product_option, subject_display_name, event_date, public_snapshot, calculation_version,
                generation_version, content_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reading.id(), reading.resultId(), reading.ownerUserId(), reading.purchaseId(),
                reading.fortuneType().name(), reading.productOption().name(), reading.subjectDisplayName(),
                reading.eventDate(), json(reading.sections()), reading.calculationVersion(),
                reading.generationVersion(), reading.contentVersion(), Timestamp.from(reading.createdAt()));
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
        requireOne(jdbc.update("UPDATE reading_purchases SET status = 'REFUNDED' WHERE id = ? AND status = 'FAILED'",
                purchaseId), "mark purchase refunded");
    }

    @Override
    public Optional<OwnedReading> findOwnedReading(UUID ownerUserId, UUID readingId) {
        return jdbc.query("SELECT * FROM readings WHERE owner_user_id = ? AND id = ?",
                (rs, row) -> reading(rs), ownerUserId, readingId).stream().findFirst();
    }

    private ReadingPurchase purchase(ResultSet rs) throws SQLException {
        return new ReadingPurchase(rs.getObject("id", UUID.class), rs.getObject("buyer_user_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getObject("subject_person_id", UUID.class), rs.getObject("wallet_transaction_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getObject("reading_id", UUID.class),
                instant(rs, "created_at"), instant(rs, "fulfilled_at"));
    }

    private OwnedReading reading(ResultSet rs) throws SQLException {
        try {
            return new OwnedReading(rs.getObject("id", UUID.class), rs.getObject("reading_result_id", UUID.class),
                    rs.getObject("owner_user_id", UUID.class), rs.getObject("purchase_id", UUID.class),
                    FortuneType.valueOf(rs.getString("fortune_type")), ProductOption.valueOf(rs.getString("product_option")),
                    rs.getString("subject_display_name"), rs.getObject("event_date", java.time.LocalDate.class),
                    mapper.readValue(rs.getString("public_snapshot"), LinerResponse.class),
                    rs.getString("calculation_version"), rs.getString("generation_version"),
                    rs.getString("content_version"), instant(rs, "created_at"));
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
