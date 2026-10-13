package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftAccessPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verification does not mark content as provided. B must coordinate first provision with cancellation. */
@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class JdbcGiftAccess implements GiftAccessPort {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public JdbcGiftAccess(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Override
    @Transactional(readOnly = true, timeout = 5)
    public GiftAccess verify(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        var rows = jdbc.query("""
                SELECT w.gift_id,w.access_expires_at FROM gift_purchase_tokens t
                JOIN gift_purchase_work w ON w.order_id = t.order_id
                WHERE t.token_hash = ? AND t.revoked = FALSE AND w.status = 'ACTIVE' AND w.access_expires_at > ?
                """, (rs, row) -> new GiftAccess(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                hash(rawToken), Timestamp.from(clock.instant()));
        return rows.stream().findFirst().orElseThrow(this::unavailable);
    }
    @Override
    public GiftAccess verifyTalismanAccess(String rawToken, UUID talismanId) {
        // No asset association exists yet; a valid gift token alone must never authorize arbitrary assets.
        throw unavailable();
    }
    private ApiException unavailable() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND); }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
