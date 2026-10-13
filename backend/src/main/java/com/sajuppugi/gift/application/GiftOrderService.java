package com.sajuppugi.gift.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftOrders;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 5)
public class GiftOrderService {
    private final JdbcGiftOrders orders;
    private final GiftQuoteService quotes;
    private final CatalogUseCase catalog;
    private final GiftPayloadCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean enabled;
    public GiftOrderService(JdbcGiftOrders orders, GiftQuoteService quotes, CatalogUseCase catalog,
            GiftPayloadCipher cipher, ObjectMapper mapper, Clock clock,
            @Value("${app.gift.orders-enabled:false}") boolean enabled) {
        this.orders = orders; this.quotes = quotes; this.catalog = catalog;
        this.cipher = cipher; this.mapper = mapper; this.clock = clock; this.enabled = enabled;
    }

    public Order create(UUID user, UUID quoteId, Payload payload, String key) {
        requireEnabled();
        if (user == null || quoteId == null || payload == null || key == null || key.isBlank() || key.length() > 512)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        validate(payload);
        String keyHash = cipher.fingerprint("key|" + key);
        String requestHash = cipher.fingerprint("request|" + quoteId + "|" + json(payload));
        var replay = orders.byKey(user, keyHash);
        if (replay.isPresent()) return match(replay.get(), requestHash);
        Order order = orders.byQuote(user, quoteId).orElse(null);
        if (order == null) {
            var gift = quotes.get(user, quoteId);
            var payable = catalog.getPayableQuote(user, quoteId);
            var product = payable.productSnapshot();
            Instant now = clock.instant();
            order = new Order(UUID.randomUUID(), user, quoteId, requestHash, product.code(), product.productName(),
                    gift.funding().quote().productSnapshot().price().amount(), mask(payload.recipientPhone()),
                    now, payable.expiresAt());
            orders.insert(order, cipher.encrypt(json(payload), order.userId() + "|" + order.orderId()));
            // The unique quote wins before binding a key; a competing request must have identical content.
            order = orders.byQuote(user, quoteId).orElseThrow();
        }
        match(order, requestHash);
        orders.bind(user, keyHash, order.orderId());
        return match(orders.byKey(user, keyHash).orElseThrow(), requestHash);
    }

    @Transactional(readOnly = true)
    public Order get(UUID user, UUID id) {
        requireEnabled();
        return orders.owned(user, id).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private void requireEnabled() {
        if (!enabled) throw new ApiException(ErrorCode.GIFT_FULFILLMENT_UNAVAILABLE);
    }
    private Order match(Order order, String hash) {
        if (!order.requestHash().equals(hash)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        return order;
    }
    private void validate(Payload payload) {
        if (payload.recipientPhone() == null || !payload.recipientPhone().matches("010[0-9]{8}"))
            throw new ApiException(ErrorCode.RECIPIENT_PHONE_INVALID);
        if (!validName(payload.recipientName()) || !validName(payload.senderName())
                || payload.message() == null || payload.message().codePointCount(0, payload.message().length()) > 200
                || payload.message().codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n')
                || payload.message().contains("\r")) throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    private boolean validName(String name) {
        return name != null && !name.isBlank() && name.codePointCount(0, name.length()) <= 40
                && name.codePoints().noneMatch(Character::isISOControl);
    }
    private String mask(String phone) { return "010-****-" + phone.substring(7); }
    private String json(Payload payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Gift payload serialization failed"); }
    }
    public record Payload(String recipientName, String recipientPhone, String senderName, String message) {
        @Override public String toString() { return "GiftPayload[REDACTED]"; }
    }
    public record Order(UUID orderId, UUID userId, UUID quoteId, String requestHash, String productCode,
                        String productName, int amount, String recipientPhoneMasked, Instant createdAt, Instant expiresAt) {}
}
