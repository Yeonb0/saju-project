package com.sajuppugi.gift.infrastructure;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable claim/fencing boundary for a future gift result worker. This class deliberately does not invoke
 * Liner, rendering, or storage: those calls must happen only after a claim has committed and outside this
 * short transaction.
 */
@Repository
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class JdbcGiftFulfillmentJobs {
    private final JdbcTemplate jdbc;

    public JdbcGiftFulfillmentJobs(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, timeout = 5)
    public List<UUID> due(Instant now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid batch size");
        return jdbc.query("""
                SELECT id FROM gift_fulfillment_jobs
                WHERE (state IN ('PENDING','RETRY') AND next_attempt_at <= ?)
                   OR (state = 'GENERATING' AND lease_until <= ?)
                ORDER BY next_attempt_at,id LIMIT ?
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(now), Timestamp.from(now), limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<Lease> claim(UUID jobId, Instant now, Duration duration, int maxAttempts) {
        if (jobId == null || duration == null || duration.isNegative() || duration.isZero()
                || duration.compareTo(Duration.ofMinutes(10)) > 0
                || maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("Invalid claim");
        jdbc.update("""
                UPDATE gift_fulfillment_jobs SET state = 'REVIEW_REQUIRED', lease_token = NULL, lease_until = NULL,
                    last_failure_code = 'FULFILLMENT_RETRY_EXHAUSTED', updated_at = ?
                WHERE id = ? AND attempt_count >= ? AND (
                    (state IN ('PENDING','RETRY') AND next_attempt_at <= ?)
                    OR (state = 'GENERATING' AND lease_until <= ?)
                )
                """, Timestamp.from(now), jobId, maxAttempts, Timestamp.from(now), Timestamp.from(now));
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                UPDATE gift_fulfillment_jobs SET state = 'GENERATING', attempt_count = attempt_count + 1,
                    lease_token = ?, lease_until = ?, fencing_version = fencing_version + 1, updated_at = ?
                WHERE id = ? AND attempt_count < ? AND (
                    (state IN ('PENDING','RETRY') AND next_attempt_at <= ?)
                    OR (state = 'GENERATING' AND lease_until <= ?)
                )
                RETURNING id,gift_id,profile_id,product_code,attempt_count,lease_token,lease_until,fencing_version
                """, (rs, row) -> new Lease(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getInt(5), rs.getObject(6, UUID.class),
                rs.getTimestamp(7).toInstant(), rs.getLong(8)), token, Timestamp.from(now.plus(duration)),
                Timestamp.from(now), jobId, maxAttempts, Timestamp.from(now), Timestamp.from(now)).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean complete(Lease lease, State state, Instant now) {
        if (lease == null || state == null || now == null || !state.completion())
            throw new IllegalArgumentException("Invalid completion");
        return jdbc.update("""
                UPDATE gift_fulfillment_jobs SET state = ?, lease_token = NULL, lease_until = NULL, updated_at = ?
                WHERE id = ? AND state = 'GENERATING' AND lease_token = ? AND fencing_version = ?
                """, state.name(), Timestamp.from(now), lease.jobId(), lease.token(), lease.fence()) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY, timeout = 10)
    public void requireCurrent(Lease lease) {
        int current = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gift_fulfillment_jobs
                WHERE id = ? AND gift_id = ? AND state = 'GENERATING' AND lease_token = ? AND fencing_version = ?
                """, Integer.class, lease.jobId(), lease.giftId(), lease.token(), lease.fence());
        if (current != 1) throw new IllegalStateException("Gift fulfillment lease is stale");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean retry(Lease lease, Instant now, Duration delay) {
        if (delay == null || delay.isNegative() || delay.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Invalid retry delay");
        return jdbc.update("""
                UPDATE gift_fulfillment_jobs SET state = 'RETRY', lease_token = NULL, lease_until = NULL,
                    next_attempt_at = ?, last_failure_code = 'FULFILLMENT_GENERATION_FAILED', updated_at = ?
                WHERE id = ? AND state = 'GENERATING' AND lease_token = ? AND fencing_version = ?
                """, Timestamp.from(now.plus(delay)), Timestamp.from(now), lease.jobId(), lease.token(), lease.fence()) == 1;
    }

    public enum State {
        WAITING_TALISMAN, READY, RETRY, FAILED, REVIEW_REQUIRED, CANCELED, PURGED;
        boolean completion() { return this == WAITING_TALISMAN || this == READY || this == FAILED || this == REVIEW_REQUIRED; }
    }

    public record Lease(UUID jobId, UUID giftId, UUID profileId, String productCode, int attempt,
                        UUID token, Instant until, long fence) {}
}
