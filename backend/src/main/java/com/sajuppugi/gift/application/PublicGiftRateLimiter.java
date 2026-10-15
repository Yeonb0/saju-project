package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublicGiftRateLimiter {
    private static final int REQUESTS_PER_MINUTE = 30;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PublicGiftRateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void check(String token, String ip) {
        Instant windowStarted = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Integer requestCount = jdbc.queryForObject(
                """
                INSERT INTO public_gift_rate_limits(subject_hash, window_started, request_count)
                VALUES (?, ?, 1)
                ON CONFLICT(subject_hash, window_started)
                DO UPDATE SET request_count = public_gift_rate_limits.request_count + 1
                RETURNING request_count
                """,
                Integer.class,
                hash(token + "|" + (ip == null ? "" : ip)),
                Timestamp.from(windowStarted));
        if (requestCount == null || requestCount > REQUESTS_PER_MINUTE) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    /** Removes no more than {@code limit} old PostgreSQL buckets in one short transaction. */
    public int purgeOlderThan(Instant cutoff, int limit) {
        return jdbc.update(
                """
                DELETE FROM public_gift_rate_limits
                WHERE ctid IN (
                    SELECT ctid
                    FROM public_gift_rate_limits
                    WHERE window_started < ?
                    ORDER BY window_started
                    LIMIT ?
                )
                """,
                Timestamp.from(cutoff),
                limit);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
