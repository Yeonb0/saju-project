package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class GiftLinkService {
    private static final Duration REVEAL_WINDOW = Duration.ofHours(24);
    private static final Duration REISSUE_WINDOW = Duration.ofDays(30);
    private static final Duration REISSUE_INTERVAL = Duration.ofMinutes(1);
    private static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DAILY_REISSUE_LIMIT = 3;
    private final JdbcTemplate jdbc;
    private final GiftPayloadCipher cipher;
    private final Clock clock;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public GiftLinkService(JdbcTemplate jdbc, GiftPayloadCipher cipher, Clock clock,
            @Value("${app.gift.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true, timeout = 5)
    public Link get(UUID buyer, UUID orderId) {
        Token token = owned(buyer, orderId, false);
        return view(token, false);
    }

    @Transactional(timeout = 5)
    public Link reissue(UUID buyer, UUID orderId, IdempotencyKey key) {
        if (key == null || key.value() == null || key.value().isBlank() || key.value().length() > 512)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        String keyHash = cipher.fingerprint("gift-link-key|" + key.value());
        Token current = owned(buyer, orderId, true);
        var prior = jdbc.query("SELECT order_id,token_version FROM gift_link_commands WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> new Command(rs.getObject(1, UUID.class), rs.getLong(2)), buyer, keyHash);
        if (!prior.isEmpty()) {
            Command command = prior.getFirst();
            if (!command.orderId().equals(orderId)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            return replay(current, command);
        }
        if (!reissueOpen(current)) throw new ApiException(ErrorCode.GIFT_LINK_REISSUE_NOT_ALLOWED);
        if (!availability(current).allowed()) throw new ApiException(ErrorCode.GIFT_LINK_REISSUE_LIMIT_EXCEEDED);
        long version = current.version() + 1;
        // Reserve the cross-order key before changing the token. A losing command must not rotate it.
        int reserved = jdbc.update("""
                INSERT INTO gift_link_commands(user_id,key_hash,order_id,token_version,created_at) VALUES (?,?,?,?,?)
                ON CONFLICT (user_id,key_hash) DO NOTHING
                """, buyer, keyHash, orderId, version, Timestamp.from(clock.instant()));
        if (reserved == 0) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        String raw = newToken();
        Instant now = clock.instant();
        int changed = jdbc.update("""
                UPDATE gift_purchase_tokens SET token_hash = ?,token_version = ?,token_ciphertext = ?,
                    issued_at = ?,reveal_until = ? WHERE order_id = ? AND token_version = ? AND revoked = FALSE
                """, hash(raw), version, cipher.encrypt(raw, context(orderId, version)), Timestamp.from(now),
                Timestamp.from(now.plus(REVEAL_WINDOW)), orderId, current.version());
        if (changed != 1) throw new IllegalStateException("Gift link rotation did not update its locked token");
        return get(buyer, orderId);
    }

    @Transactional(timeout = 5)
    public int purgeExpired() {
        return jdbc.update("""
                UPDATE gift_purchase_tokens t SET token_ciphertext = NULL
                WHERE t.token_ciphertext IS NOT NULL AND (t.reveal_until <= ? OR t.revoked = TRUE OR EXISTS
                    (SELECT 1 FROM gift_purchase_work w WHERE w.order_id = t.order_id
                        AND (w.status <> 'ACTIVE' OR w.access_expires_at <= ?)))
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }

    private Token owned(UUID buyer, UUID orderId, boolean lock) {
        // Match cancellation, first provision and privacy maintenance: work before token.
        if (lock) {
            var grants = jdbc.query("""
                    SELECT w.order_id FROM gift_purchase_work w JOIN gift_order_drafts d ON d.id = w.order_id
                    WHERE d.user_id = ? AND d.id = ? FOR UPDATE OF w
                    """, (rs, row) -> rs.getObject(1, UUID.class), buyer, orderId);
            if (grants.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        var rows = jdbc.query("""
                SELECT w.gift_id,w.status,w.access_expires_at,t.revoked,t.token_version,t.token_ciphertext,
                    t.issued_at,t.reveal_until,w.approved_at FROM gift_order_drafts d
                JOIN gift_purchase_work w ON w.order_id=d.id JOIN gift_purchase_tokens t ON t.order_id=w.order_id
                WHERE d.user_id=? AND d.id=?
                """ + (lock ? " FOR UPDATE OF t" : ""), (rs, row) -> new Token(orderId,
                rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getBoolean(4),
                rs.getLong(5), rs.getString(6), instant(rs.getTimestamp(7)), instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9))), buyer, orderId);
        return rows.stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Link view(Token token, boolean reused) {
        String url = null;
        if (token.ciphertext() != null && token.revealUntil() != null && clock.instant().isBefore(token.revealUntil()) && !token.revoked()
                && "ACTIVE".equals(token.status()) && clock.instant().isBefore(token.expiresAt())) {
            url = shareUrl(cipher.decrypt(token.ciphertext(), context(token.orderId(), token.version())));
        }
        var available = availability(token);
        return new Link(token.orderId(), token.giftId(), token.version(), token.expiresAt(), url, reused,
                available.allowed(), available.remaining(), available.next());
    }

    private Link replay(Token current, Command command) {
        // An old command must never return a newer command's bearer credential or restore a purged one.
        var available = availability(current);
        return command.version() == current.version() ? view(current, true)
                : new Link(current.orderId(), current.giftId(), command.version(), current.expiresAt(), null, true,
                    available.allowed(), available.remaining(), available.next());
    }

    private boolean reissueOpen(Token token) {
        Instant now = clock.instant();
        return "ACTIVE".equals(token.status()) && !token.revoked() && now.isBefore(token.expiresAt())
                && token.approvedAt() != null && !now.isBefore(token.approvedAt())
                && now.isBefore(token.approvedAt().plus(REISSUE_WINDOW));
    }

    private Availability availability(Token token) {
        if (!reissueOpen(token)) return new Availability(false, 0, null);
        Instant now = clock.instant();
        var today = now.atZone(DAY_ZONE).toLocalDate();
        Instant start = today.atStartOfDay(DAY_ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(DAY_ZONE).toInstant();
        var usage = jdbc.query("""
                SELECT COUNT(*) FILTER (WHERE created_at >= ? AND created_at < ?),MAX(created_at)
                FROM gift_link_commands WHERE order_id = ?
                """, (rs, row) -> new Usage(rs.getInt(1), instant(rs.getTimestamp(2))),
                Timestamp.from(start), Timestamp.from(end), token.orderId()).getFirst();
        int remaining = Math.max(0, DAILY_REISSUE_LIMIT - usage.count());
        Instant next = usage.last() == null ? null : usage.last().plus(REISSUE_INTERVAL);
        if (remaining == 0 && (next == null || end.isAfter(next))) next = end;
        if (next != null && !next.isAfter(now)) next = null;
        return new Availability(remaining > 0 && next == null, remaining, next);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String shareUrl(String token) { return frontendBaseUrl + "/g#token=" + token; }
    private String context(UUID orderId, long version) { return "gift-link|" + orderId + "|" + version; }
    private Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Token(UUID orderId, UUID giftId, String status, Instant expiresAt, boolean revoked,
                         long version, String ciphertext, Instant issuedAt, Instant revealUntil, Instant approvedAt) {}
    private record Command(UUID orderId, long version) {}
    private record Usage(int count, Instant last) {}
    private record Availability(boolean allowed, int remaining, Instant next) {}
    public record Link(UUID orderId, UUID giftId, long tokenVersion, Instant expiresAt, String shareUrl, boolean reused,
                       boolean canReissue, int remainingReissues, Instant nextReissueAt) {
        @Override public String toString() { return "GiftLink[REDACTED]"; }
    }
}
