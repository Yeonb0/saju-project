package com.sajuppugi.gift.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final GiftPayloadCipher cipher;
    private final ObjectMapper mapper;
    public JdbcGiftAccess(JdbcTemplate jdbc, Clock clock, GiftPayloadCipher cipher, ObjectMapper mapper) {
        this.jdbc = jdbc; this.clock = clock; this.cipher = cipher; this.mapper = mapper;
    }
    @Override
    @Transactional(readOnly = true, timeout = 5)
    public GiftAccess verify(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        var rows = jdbc.query("""
                SELECT w.gift_id,w.access_expires_at FROM gift_purchase_tokens t
                JOIN gift_purchase_work w ON w.order_id = t.order_id
                WHERE t.token_hash = ? AND t.revoked = FALSE AND w.status = 'ACTIVE'
                AND (w.access_expires_at > ? OR EXISTS (SELECT 1 FROM gift_recipient_receipts r
                    WHERE r.order_id = w.order_id AND r.state IN ('ACCEPTED','REVIEW_REQUIRED') AND r.submitted_at < w.access_expires_at))
                """, (rs, row) -> new GiftAccess(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                hash(rawToken), Timestamp.from(clock.instant()));
        return rows.stream().findFirst().orElseThrow(this::unavailable);
    }
    @Override
    @Transactional(readOnly = true, timeout = 5)
    public PublicGift describe(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        var rows = jdbc.query("""
                SELECT d.user_id,d.id,d.private_payload,d.product_code,w.access_expires_at,r.state
                FROM gift_purchase_tokens t
                JOIN gift_purchase_work w ON w.order_id = t.order_id
                JOIN gift_order_drafts d ON d.id = w.order_id
                LEFT JOIN gift_recipient_receipts r ON r.order_id = w.order_id
                WHERE t.token_hash = ? AND t.revoked = FALSE AND w.status = 'ACTIVE'
                AND (w.access_expires_at > ? OR (r.state IN ('ACCEPTED','REVIEW_REQUIRED')
                    AND r.submitted_at < w.access_expires_at))
                """, (rs, row) -> {
            UUID userId = rs.getObject(1, UUID.class), orderId = rs.getObject(2, UUID.class);
            DraftPayload payload = decrypt(rs.getString(3), userId + "|" + orderId);
            return new PublicGift(payload.recipientName(), payload.senderName(), payload.message(),
                    option(rs.getString(4)), state(rs.getString(6)), rs.getTimestamp(5).toInstant());
        }, hash(rawToken), Timestamp.from(clock.instant()));
        return rows.stream().findFirst().orElseThrow(this::unavailable);
    }
    @Override
    public GiftAccess verifyTalismanAccess(String rawToken, UUID talismanId) {
        // No asset association exists yet; a valid gift token alone must never authorize arbitrary assets.
        throw unavailable();
    }
    private ApiException unavailable() { return new ApiException(ErrorCode.RESOURCE_NOT_FOUND); }
    private DraftPayload decrypt(String ciphertext, String context) {
        try { return mapper.readValue(cipher.decrypt(ciphertext, context), DraftPayload.class); }
        catch (Exception invalid) { throw new IllegalStateException("Gift public payload could not be read"); }
    }
    private PublicGift.ProductOption option(String productCode) {
        return switch (productCode) {
            case "GIFT_SUNEUNG_TALISMAN_ONLY" -> PublicGift.ProductOption.TALISMAN_ONLY;
            case "GIFT_SUNEUNG_READING_WITH_TALISMAN" -> PublicGift.ProductOption.READING_WITH_TALISMAN;
            default -> throw new IllegalStateException("Gift product is not supported");
        };
    }
    private PublicGift.AccessState state(String receiptState) {
        if (receiptState == null) return PublicGift.AccessState.INPUT_REQUIRED;
        return "PROVIDED".equals(receiptState) ? PublicGift.AccessState.READY : PublicGift.AccessState.PROCESSING;
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private record DraftPayload(String recipientName, String senderName, String message) {}
}
