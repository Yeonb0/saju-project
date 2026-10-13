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
            return view(current, true);
        }
        if (!"ACTIVE".equals(current.status()) || current.revoked() || !clock.instant().isBefore(current.expiresAt()))
            throw new ApiException(ErrorCode.GIFT_CANCEL_NOT_ALLOWED);
        long version = current.version() + 1;
        String raw = newToken();
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE gift_purchase_tokens SET token_hash = ?,token_version = ?,token_ciphertext = ?,
                    issued_at = ?,reveal_until = ? WHERE order_id = ? AND token_version = ? AND revoked = FALSE
                """, hash(raw), version, cipher.encrypt(raw, context(orderId, version)), Timestamp.from(now),
                Timestamp.from(now.plus(REVEAL_WINDOW)), orderId, current.version());
        jdbc.update("INSERT INTO gift_link_commands(user_id,key_hash,order_id,token_version,created_at) VALUES (?,?,?,?,?)",
                buyer, keyHash, orderId, version, Timestamp.from(now));
        return new Link(orderId, current.giftId(), version, current.expiresAt(), shareUrl(raw), false);
    }

    @Transactional(timeout = 5)
    public int purgeExpired() {
        return jdbc.update("UPDATE gift_purchase_tokens SET token_ciphertext = NULL WHERE token_ciphertext IS NOT NULL AND reveal_until <= ?",
                Timestamp.from(clock.instant()));
    }

    private Token owned(UUID buyer, UUID orderId, boolean lock) {
        var rows = jdbc.query("""
                SELECT w.gift_id,w.status,w.access_expires_at,t.revoked,t.token_version,t.token_ciphertext,
                    t.issued_at,t.reveal_until FROM gift_order_drafts d
                JOIN gift_purchase_work w ON w.order_id=d.id JOIN gift_purchase_tokens t ON t.order_id=w.order_id
                WHERE d.user_id=? AND d.id=?
                """ + (lock ? " FOR UPDATE OF t" : ""), (rs, row) -> new Token(orderId,
                rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getBoolean(4),
                rs.getLong(5), rs.getString(6), instant(rs.getTimestamp(7)), instant(rs.getTimestamp(8))), buyer, orderId);
        return rows.stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Link view(Token token, boolean reused) {
        String url = null;
        if (token.ciphertext() != null && token.revealUntil() != null && clock.instant().isBefore(token.revealUntil()) && !token.revoked()
                && "ACTIVE".equals(token.status()) && clock.instant().isBefore(token.expiresAt())) {
            url = shareUrl(cipher.decrypt(token.ciphertext(), context(token.orderId(), token.version())));
        }
        return new Link(token.orderId(), token.giftId(), token.version(), token.expiresAt(), url, reused);
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
                         long version, String ciphertext, Instant issuedAt, Instant revealUntil) {}
    private record Command(UUID orderId, long version) {}
    public record Link(UUID orderId, UUID giftId, long tokenVersion, Instant expiresAt, String shareUrl, boolean reused) {}
}
