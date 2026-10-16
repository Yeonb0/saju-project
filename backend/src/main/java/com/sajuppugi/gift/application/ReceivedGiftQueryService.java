package com.sajuppugi.gift.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account-linked, already-provided gifts only; bearer links remain a separate public authorization path. */
@Service
public class ReceivedGiftQueryService {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final GiftPayloadCipher cipher; private final Clock clock;
    public ReceivedGiftQueryService(JdbcTemplate jdbc, ObjectMapper mapper, GiftPayloadCipher cipher, Clock clock) { this.jdbc = jdbc; this.mapper = mapper; this.cipher = cipher; this.clock = clock; }
    @Transactional(readOnly = true, timeout = 5)
    public Page list(UUID user, String rawCursor, Integer requestedSize) {
        int size = requestedSize == null ? 20 : requestedSize;
        if (user == null || size < 1 || size > 50) throw new ApiException(ErrorCode.INVALID_REQUEST);
        Cursor cursor = decode(rawCursor); String sql = """
                SELECT w.gift_id,d.product_code,w.first_provided_at,w.access_expires_at,d.user_id,d.id,d.private_payload
                FROM gift_recipient_receipts r
                JOIN gift_purchase_work w ON w.order_id = r.order_id
                JOIN gift_order_drafts d ON d.id = w.order_id
                JOIN users u ON u.id = r.recipient_user_id AND u.status = 'ACTIVE'
                WHERE r.recipient_user_id = ? AND r.state = 'PROVIDED' AND w.status = 'ACTIVE' AND w.access_expires_at > ?
                """;
        List<Object> args = new ArrayList<>(List.of(user, Timestamp.from(clock.instant())));
        if (cursor != null) { sql += " AND (w.first_provided_at < ? OR (w.first_provided_at = ? AND w.gift_id < ?))";
            args.add(Timestamp.from(cursor.providedAt())); args.add(Timestamp.from(cursor.providedAt())); args.add(cursor.giftId()); }
        sql += " ORDER BY w.first_provided_at DESC,w.gift_id DESC LIMIT ?"; args.add(size + 1);
        var rows = jdbc.query(sql, (rs, row) -> new Item(rs.getObject(1, UUID.class), option(rs.getString(2)),
                rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(), sender(rs.getObject(5, UUID.class),
                rs.getObject(6, UUID.class), rs.getString(7))), args.toArray());
        boolean hasNext = rows.size() > size; var items = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
        return new Page(items, hasNext ? encode(new Cursor(items.getLast().providedAt(), items.getLast().giftId())) : null, hasNext);
    }
    private String option(String code) { return switch (code) { case "GIFT_SUNEUNG_TALISMAN_ONLY" -> "TALISMAN_ONLY";
        case "GIFT_SUNEUNG_READING_WITH_TALISMAN" -> "READING_WITH_TALISMAN"; default -> throw new IllegalStateException("Unsupported gift product"); }; }
    private String sender(UUID owner, UUID order, String payload) { if (payload == null || payload.isBlank()) return null; try {
        return mapper.readValue(cipher.decrypt(payload, owner + "|" + order), Payload.class).senderName();
    } catch (Exception unavailable) { return null; } }
    private Cursor decode(String raw) { if (raw == null || raw.isBlank()) return null; try { if (raw.length() > 1024) throw new IllegalArgumentException();
        var value = mapper.readValue(Base64.getUrlDecoder().decode(raw), Cursor.class); if (value.providedAt() == null || value.giftId() == null) throw new IllegalArgumentException(); return value;
    } catch (Exception bad) { throw new ApiException(ErrorCode.INVALID_CURSOR); } }
    private String encode(Cursor cursor) { try { return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor)); }
        catch (Exception bad) { throw new IllegalStateException("Gift cursor serialization failed", bad); } }
    private record Cursor(Instant providedAt, UUID giftId) {}
    private record Payload(String recipientName, String senderName, String message) {}
    public record Page(List<Item> items, String nextCursor, boolean hasNext) {}
    public record Item(UUID giftId, String option, Instant providedAt, Instant accessExpiresAt, String senderName) {}
}
