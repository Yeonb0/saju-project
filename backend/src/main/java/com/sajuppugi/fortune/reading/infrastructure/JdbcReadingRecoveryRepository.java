package com.sajuppugi.fortune.reading.infrastructure;

import com.sajuppugi.fortune.reading.port.ReadingRecoveryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReadingRecoveryRepository implements ReadingRecoveryRepository {
    private final JdbcTemplate jdbc;
    public JdbcReadingRecoveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findReadyFailed(Instant now, int maxAttempts, int limit) {
        return jdbc.query("""
                SELECT id FROM reading_purchases WHERE status = 'FAILED' AND wallet_transaction_id IS NOT NULL
                AND recovery_attempts < ? AND (recovery_next_attempt_at IS NULL OR recovery_next_attempt_at <= ?)
                ORDER BY created_at, id LIMIT ?
                """, (rs, row) -> rs.getObject(1, UUID.class), maxAttempts, Timestamp.from(now), limit);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FailedPurchase> lockReadyFailed(UUID id, Instant now, int maxAttempts) {
        return jdbc.query("""
                SELECT * FROM reading_purchases WHERE id = ? AND status = 'FAILED' AND wallet_transaction_id IS NOT NULL
                AND recovery_attempts < ? AND (recovery_next_attempt_at IS NULL OR recovery_next_attempt_at <= ?)
                FOR UPDATE
                """, (rs, row) -> new FailedPurchase(id, rs.getObject("buyer_user_id", UUID.class),
                rs.getObject("quote_id", UUID.class), rs.getObject("wallet_transaction_id", UUID.class),
                rs.getInt("recovery_attempts")), id, maxAttempts, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markRefunded(UUID id) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET status = 'REFUNDED', recovery_attempts = recovery_attempts + 1,
                recovery_next_attempt_at = NULL, recovery_last_error_code = NULL WHERE id = ? AND status = 'FAILED'
                """, id));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailure(UUID id, Instant nextAttemptAt, String code) {
        requireOne(jdbc.update("""
                UPDATE reading_purchases SET recovery_attempts = recovery_attempts + 1,
                recovery_next_attempt_at = ?, recovery_last_error_code = ? WHERE id = ? AND status = 'FAILED'
                """, nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt), code, id));
    }

    private void requireOne(int count) {
        if (count != 1) throw new IllegalStateException("Failed purchase changed during recovery");
    }
}
