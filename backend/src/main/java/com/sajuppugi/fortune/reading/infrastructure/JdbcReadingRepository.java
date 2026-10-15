package com.sajuppugi.fortune.reading.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
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
    private final GenerationContextProtector contextProtector;

    public JdbcReadingRepository(JdbcTemplate jdbc, ObjectMapper mapper, GenerationContextProtector contextProtector) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.contextProtector = contextProtector;
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
    public void saveGenerationContext(UUID purchaseId, GenerationResumeContext context) {
        int updated = jdbc.update("""
                UPDATE reading_purchases SET generation_context = ?
                WHERE id = ? AND status IN ('CREATED', 'DEBITED') AND generation_context IS NULL
                """, contextProtector.encrypt(json(context)), purchaseId);
        if (updated == 1) return;
        GenerationResumeContext existing = jdbc.query("""
                SELECT generation_context FROM reading_purchases
                WHERE id = ? AND status IN ('CREATED', 'DEBITED') AND generation_context IS NOT NULL
                """, (rs, row) -> context(rs.getString(1)), purchaseId).stream().findFirst().orElse(null);
        if (!context.equals(existing)) throw new IllegalStateException("Generation context changed for purchase");
    }

    @Override
    public Optional<GenerationResumeContext> findGenerationContext(UUID buyerUserId, UUID quoteId) {
        return jdbc.query("""
                SELECT generation_context FROM reading_purchases
                WHERE buyer_user_id = ? AND quote_id = ? AND generation_context IS NOT NULL
                """, (rs, row) -> context(rs.getString(1)), buyerUserId, quoteId).stream().findFirst();
    }

    @Override
    public List<UUID> findReadyGenerationRecoveries(Instant now, int maxAttempts, int limit) {
        return jdbc.query("""
                SELECT id FROM reading_purchases
                WHERE status IN ('DEBITED', 'GENERATING') AND generation_context IS NOT NULL
                AND generation_attempts < ?
                AND (status = 'DEBITED' OR generation_lease_token IS NULL OR generation_lease_until < ?)
                ORDER BY created_at, id LIMIT ?
                """, (rs, row) -> rs.getObject(1, UUID.class), maxAttempts, Timestamp.from(now), limit);
    }

    @Override
    public List<UUID> findExhaustedGenerationRecoveries(Instant now, int maxAttempts, int limit) {
        return jdbc.query("""
                SELECT id FROM reading_purchases
                WHERE status = 'GENERATING' AND generation_attempts >= ?
                AND (generation_lease_token IS NULL OR generation_lease_until < ?)
                ORDER BY created_at, id LIMIT ?
                """, (rs, row) -> rs.getObject(1, UUID.class), maxAttempts, Timestamp.from(now), limit);
    }

    @Override
    public boolean markGenerationExhausted(UUID purchaseId, Instant now, int maxAttempts) {
        return jdbc.update("""
                UPDATE reading_purchases SET status = 'FAILED', failure_code = 'GENERATION_RETRY_EXHAUSTED',
                compensation_status = CASE WHEN wallet_transaction_id IS NULL THEN 'NOT_REQUIRED' ELSE 'PENDING' END,
                generation_lease_token = NULL, generation_lease_until = NULL
                WHERE id = ? AND status = 'GENERATING' AND generation_attempts >= ?
                AND (generation_lease_token IS NULL OR generation_lease_until < ?)
                """, purchaseId, maxAttempts, Timestamp.from(now)) == 1;
    }

    @Override
    public Optional<GenerationRecoveryCandidate> findGenerationRecoveryCandidate(UUID purchaseId) {
        return jdbc.query("""
                SELECT * FROM reading_purchases WHERE id = ? AND status IN ('DEBITED', 'GENERATING')
                AND generation_context IS NOT NULL
                """, (rs, row) -> new GenerationRecoveryCandidate(purchase(rs), context(rs.getString("generation_context"))),
                purchaseId).stream().findFirst();
    }

    @Override
    public Optional<GenerationLease> tryClaimGeneration(UUID purchaseId, UUID leaseToken, Instant now,
                                                          Instant leaseUntil) {
        int updated = jdbc.update("""
                UPDATE reading_purchases SET status = 'GENERATING', generation_lease_token = ?,
                generation_lease_until = ?, generation_fence = generation_fence + 1,
                generation_attempts = generation_attempts + 1
                WHERE id = ? AND status IN ('DEBITED', 'GENERATING')
                AND (generation_lease_token IS NULL OR generation_lease_until < ?)
                """, leaseToken, Timestamp.from(leaseUntil), purchaseId, Timestamp.from(now));
        if (updated != 1) return Optional.empty();
        return jdbc.query("SELECT generation_fence FROM reading_purchases WHERE id = ? AND generation_lease_token = ?",
                (rs, row) -> new GenerationLease(leaseToken, rs.getLong("generation_fence")), purchaseId, leaseToken)
                .stream().findFirst();
    }

    @Override
    public void releaseGenerationLease(UUID purchaseId, GenerationLease lease) {
        jdbc.update("""
                UPDATE reading_purchases SET generation_lease_token = NULL, generation_lease_until = NULL
                WHERE id = ? AND status = 'GENERATING' AND generation_lease_token = ? AND generation_fence = ?
                """, purchaseId, lease.token(), lease.fence());
    }

    @Override
    @Transactional
    public void fulfill(ReadingPurchase purchase, OwnedReading reading, GenerationLease lease) {
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
        String sql = """
                UPDATE reading_purchases SET status = 'FULFILLED', reading_id = ?, fulfilled_at = ?,
                generation_lease_token = NULL, generation_lease_until = NULL
                WHERE id = ? AND status = 'GENERATING' AND generation_lease_token = ? AND generation_fence = ?
                """;
        int updated = jdbc.update(sql, reading.id(), Timestamp.from(reading.createdAt()), purchase.id(),
                lease.token(), lease.fence());
        requireOne(updated, "fulfill purchase");
    }

    @Override
    public void markDebitMismatch(UUID purchaseId) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'FAILED', failure_code = 'PURCHASE_DEBIT_MISMATCH',
                compensation_status = CASE WHEN wallet_transaction_id IS NULL THEN 'NOT_REQUIRED' ELSE 'PENDING' END
                WHERE id = ? AND status = 'DEBITED'
                """, purchaseId), "mark purchase debit mismatch");
    }

    @Override
    public void markFailed(UUID purchaseId, String failureCode, GenerationLease lease) {
        if (failureCode == null || failureCode.isBlank()) throw new IllegalArgumentException("failureCode is required");
        String sql = """
                UPDATE reading_purchases SET status = 'FAILED', failure_code = ?,
                compensation_status = CASE WHEN wallet_transaction_id IS NULL THEN 'NOT_REQUIRED' ELSE 'PENDING' END,
                generation_lease_token = NULL, generation_lease_until = NULL
                WHERE id = ? AND status = 'GENERATING' AND generation_lease_token = ? AND generation_fence = ?
                """;
        int updated = jdbc.update(sql, failureCode, purchaseId, lease.token(), lease.fence());
        requireOne(updated, "mark purchase failed");
    }

    @Override
    public void markRefunded(UUID purchaseId) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'REFUNDED', failure_code = NULL,
                compensation_status = 'COMPLETED'
                WHERE id = ? AND status IN ('FAILED', 'REFUNDED')
                """,
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
                Status.valueOf(rs.getString("status")), rs.getString("failure_code"),
                ReadingPurchase.CompensationStatus.valueOf(rs.getString("compensation_status")),
                rs.getObject("reading_id", UUID.class),
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

    private GenerationResumeContext context(String value) throws SQLException {
        try {
            return mapper.readValue(contextProtector.decrypt(value), GenerationResumeContext.class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid reading generation context", exception);
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
