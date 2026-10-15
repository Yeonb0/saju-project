package com.sajuppugi.gift.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GiftOrderQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final GiftPayloadCipher cipher;
    private final Clock clock;
    private final boolean enabled;
    public GiftOrderQueryService(JdbcTemplate jdbc, ObjectMapper mapper, GiftPayloadCipher cipher, Clock clock,
            @Value("${app.gift.orders-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.mapper = mapper; this.cipher = cipher; this.clock = clock; this.enabled = enabled;
    }
    @Transactional(readOnly = true, timeout = 5)
    public Page list(UUID owner, String cursor, Integer size) {
        if (!enabled) throw new ApiException(ErrorCode.GIFT_FULFILLMENT_UNAVAILABLE);
        int limit = size == null ? 20 : size;
        if (owner == null || limit < 1 || limit > 50) throw new ApiException(ErrorCode.INVALID_REQUEST);
        Cursor boundary = decode(cursor);
        String sql = """
                SELECT d.id,d.quote_id,d.product_code,d.product_name,d.shell_amount,
                d.created_at,d.private_payload,COALESCE(w.status,'CREATED') AS financial_status,w.charged_amount,w.access_expires_at,
                w.approved_at,w.first_provided_at,r.state AS receipt_state
                FROM gift_order_drafts d LEFT JOIN gift_purchase_work w ON w.order_id = d.id
                LEFT JOIN gift_recipient_receipts r ON r.order_id = d.id
                WHERE d.user_id = ?
                """;
        List<Object> args = new ArrayList<>(List.of(owner));
        if (boundary != null) {
            sql += " AND (d.created_at < ? OR (d.created_at = ? AND d.id < ?))";
            args.add(Timestamp.from(boundary.createdAt())); args.add(Timestamp.from(boundary.createdAt())); args.add(boundary.orderId());
        }
        sql += " ORDER BY d.created_at DESC,d.id DESC LIMIT ?"; args.add(limit + 1);
        Instant now = clock.instant();
        var rows = jdbc.query(sql, (rs, row) -> new Item(rs.getObject("id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getString("product_code"), rs.getString("product_name"), rs.getInt("shell_amount"),
                rs.getString("financial_status"), rs.getObject("charged_amount", Integer.class), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("access_expires_at") == null ? null : rs.getTimestamp("access_expires_at").toInstant(),
                recipient(rs.getString("private_payload"), owner, rs.getObject("id", UUID.class)), option(rs.getString("product_code")),
                fulfillmentState(rs.getString("receipt_state")), rs.getTimestamp("first_provided_at") == null ? null : rs.getTimestamp("first_provided_at").toInstant(),
                cancelEligible(rs.getString("financial_status"), rs.getTimestamp("approved_at"), rs.getString("receipt_state"), now)), args.toArray());
        boolean hasNext = rows.size() > limit;
        var items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        return new Page(items, hasNext ? encode(new Cursor(items.getLast().createdAt(), items.getLast().orderId())) : null, hasNext);
    }
    private Cursor decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.length() > 1024) throw new IllegalArgumentException();
            Cursor cursor = mapper.readValue(Base64.getUrlDecoder().decode(raw), Cursor.class);
            if (cursor.createdAt() == null || cursor.orderId() == null) throw new IllegalArgumentException();
            return cursor;
        } catch (Exception invalid) { throw new ApiException(ErrorCode.INVALID_CURSOR); }
    }
    private String encode(Cursor cursor) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException("Gift cursor serialization failed"); }
    }
    private String recipient(String payload, UUID owner, UUID order) {
        if (payload == null || payload.isBlank()) return null;
        try { return mapper.readValue(cipher.decrypt(payload, owner + "|" + order), DraftPayload.class).recipientName(); }
        catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException unavailable) { return null; }
    }
    private String option(String code) { return switch (code) { case "GIFT_SUNEUNG_TALISMAN_ONLY" -> "TALISMAN_ONLY";
        case "GIFT_SUNEUNG_READING_WITH_TALISMAN" -> "READING_WITH_TALISMAN"; default -> null; }; }
    private String fulfillmentState(String receipt) { return receipt == null ? "INPUT_REQUIRED" : "PROVIDED".equals(receipt) ? "READY" : "PROCESSING"; }
    private boolean cancelEligible(String status, Timestamp approved, String receipt, Instant now) {
        return "ACTIVE".equals(status) && approved != null && !now.isBefore(approved.toInstant())
                && !now.isAfter(approved.toInstant().plus(Duration.ofDays(7))) && !"PROVIDED".equals(receipt);
    }
    private record Cursor(Instant createdAt, UUID orderId) {}
    private record DraftPayload(String recipientName, String senderName, String message) {}
    @Schema(requiredProperties = {"items","nextCursor","hasNext"})
    public record Page(List<Item> items, @Schema(types = {"string","null"}) String nextCursor, boolean hasNext) {}
    @Schema(requiredProperties = {"orderId","quoteId","productCode","productName","plannedShells","status","chargedShells","createdAt","accessExpiresAt","recipientName","option","fulfillmentState","firstProvidedAt","cancelEligible"})
    public record Item(UUID orderId, UUID quoteId, String productCode, String productName, int plannedShells, String status,
            @Schema(types = {"integer","null"}) Integer chargedShells, Instant createdAt,
            @Schema(types = {"string","null"}, format = "date-time") Instant accessExpiresAt,
            @Schema(types = {"string","null"}) String recipientName, @Schema(types = {"string","null"}) String option,
            String fulfillmentState, @Schema(types = {"string","null"}, format = "date-time") Instant firstProvidedAt,
            boolean cancelEligible) {}
}
