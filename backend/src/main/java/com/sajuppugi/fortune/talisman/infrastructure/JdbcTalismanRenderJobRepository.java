package com.sajuppugi.fortune.talisman.infrastructure;

import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTalismanRenderJobRepository implements TalismanRenderJobRepository {
    private final JdbcTemplate jdbc;

    public JdbcTalismanRenderJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void enqueue(UUID talismanId, Instant now) {
        if (isPostgres()) {
            jdbc.update("""
                INSERT INTO talisman_render_jobs (talisman_id, state, next_attempt_at, created_at, updated_at)
                VALUES (?, 'PENDING', ?, ?, ?)
                ON CONFLICT (talisman_id) DO NOTHING
                """, talismanId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbc.update("""
                    MERGE INTO talisman_render_jobs target
                    USING (VALUES (?, ?, ?, ?)) incoming (talisman_id, next_attempt_at, created_at, updated_at)
                    ON target.talisman_id = incoming.talisman_id
                    WHEN NOT MATCHED THEN INSERT (talisman_id, state, next_attempt_at, created_at, updated_at)
                    VALUES (incoming.talisman_id, 'PENDING', incoming.next_attempt_at, incoming.created_at, incoming.updated_at)
                    """, talismanId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        }
    }

    @Override
    @Transactional
    public List<Lease> claimReady(Instant now, int maxAttempts, int batchSize, Duration leaseDuration) {
        List<UUID> candidates = jdbc.query("""
                SELECT talisman_id FROM talisman_render_jobs
                WHERE attempts < ? AND ((state = 'PENDING' AND next_attempt_at <= ?)
                    OR (state = 'IN_FLIGHT' AND lease_until < ?))
                ORDER BY next_attempt_at, talisman_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class), maxAttempts,
                Timestamp.from(now), Timestamp.from(now), batchSize);
        List<Lease> leases = new ArrayList<>();
        Instant until = now.plus(leaseDuration);
        for (UUID id : candidates) {
            UUID token = UUID.randomUUID();
            int updated = jdbc.update("""
                    UPDATE talisman_render_jobs SET state = 'IN_FLIGHT', attempts = attempts + 1,
                    lease_token = ?, lease_until = ?, fence = fence + 1, updated_at = ? WHERE talisman_id = ?
                    """, token, Timestamp.from(until), Timestamp.from(now), id);
            if (updated != 1) throw new IllegalStateException("Cannot claim talisman render job");
            Long fence = jdbc.queryForObject("SELECT fence FROM talisman_render_jobs WHERE talisman_id = ? AND lease_token = ?",
                    Long.class, id, token);
            leases.add(new Lease(id, token, fence));
        }
        return List.copyOf(leases);
    }

    @Override
    @Transactional
    public void complete(Lease lease, AssetKeys assets, Instant now) {
        if (!ownsLease(lease, now)) return;
        int ready = jdbc.update("""
                UPDATE talismans SET status = 'READY', original_object_key = ?, thumbnail_object_key = ?,
                share_object_key = ?, ready_at = ? WHERE id = ? AND status = 'PENDING'
                """, assets.original(), assets.thumbnail(), assets.share(), Timestamp.from(now), lease.talismanId());
        if (ready != 1) throw new IllegalStateException("Cannot publish talisman from current state");
        jdbc.update("""
                UPDATE talisman_render_jobs SET state = 'COMPLETED', lease_token = NULL, lease_until = NULL,
                last_failure_code = NULL, updated_at = ? WHERE talisman_id = ? AND lease_token = ? AND fence = ?
                """, Timestamp.from(now), lease.talismanId(), lease.token(), lease.fence());
    }

    @Override
    @Transactional
    public void fail(Lease lease, String failureCode, Instant now, int maxAttempts, Duration retryDelay) {
        if (!ownsLease(lease, now)) return;
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM talisman_render_jobs WHERE talisman_id = ? AND lease_token = ? AND fence = ?",
                Integer.class, lease.talismanId(), lease.token(), lease.fence());
        boolean exhausted = attempts >= maxAttempts;
        if (exhausted) {
            int failed = jdbc.update("UPDATE talismans SET status = 'FAILED', failure_code = ? WHERE id = ? AND status = 'PENDING'",
                    failureCode, lease.talismanId());
            if (failed != 1) throw new IllegalStateException("Cannot fail talisman from current state");
        }
        jdbc.update("""
                UPDATE talisman_render_jobs SET state = ?, lease_token = NULL, lease_until = NULL,
                next_attempt_at = ?, last_failure_code = ?, updated_at = ?
                WHERE talisman_id = ? AND lease_token = ? AND fence = ?
                """, exhausted ? "EXHAUSTED" : "PENDING", Timestamp.from(now.plus(retryDelay)), failureCode,
                Timestamp.from(now), lease.talismanId(), lease.token(), lease.fence());
    }

    private boolean ownsLease(Lease lease, Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM talisman_render_jobs WHERE talisman_id = ? AND state = 'IN_FLIGHT'
                AND lease_token = ? AND fence = ? AND lease_until > ?
                """, Integer.class, lease.talismanId(), lease.token(), lease.fence(), Timestamp.from(now));
        return count != null && count == 1;
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
    }
}
