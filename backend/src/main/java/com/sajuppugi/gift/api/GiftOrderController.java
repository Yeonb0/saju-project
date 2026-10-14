package com.sajuppugi.gift.api;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gift-orders")
public class GiftOrderController {
    private final GiftOrderService orders;
    private final com.sajuppugi.gift.application.GiftOrderQueryService queries;
    private final org.springframework.beans.factory.ObjectProvider<com.sajuppugi.gift.application.GiftPurchaseTransactions> purchases;
    public GiftOrderController(GiftOrderService orders,
            org.springframework.beans.factory.ObjectProvider<com.sajuppugi.gift.application.GiftPurchaseTransactions> purchases,
            com.sajuppugi.gift.application.GiftOrderQueryService queries) {
        this.orders = orders; this.purchases = purchases; this.queries = queries;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGiftOrder", description = "Create or replay local/test metadata. A new draft has no debit; replay reflects current financial status. expiresAt is the original quote deadline, not gift access expiry.")
    public ApiResponse<GiftOrderResponse> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request request) {
        return ApiResponse.of(response(orders.create(user(authentication), request.quoteId(),
                new GiftOrderService.Payload(request.recipientName(), request.senderName(), request.message()), key)));
    }
    @GetMapping("/{orderId}")
    @Operation(operationId = "getGiftOrder")
    public ApiResponse<GiftOrderResponse> get(Authentication authentication, @PathVariable UUID orderId) {
        return ApiResponse.of(response(orders.get(user(authentication), orderId)));
    }
    @GetMapping
    @Operation(operationId = "listGiftOrders", description = "Owner-only newest-first cursor page. Maximum size 50. No raw phone, message or token.")
    public ApiResponse<com.sajuppugi.gift.application.GiftOrderQueryService.Page> list(Authentication auth,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
        return ApiResponse.of(queries.list(user(auth), cursor, size));
    }
    private UUID user(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException invalid) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
    private GiftOrderResponse response(GiftOrderService.Order order) {
        var service = purchases.getIfAvailable();
        var work = service == null ? null : service.find(order.userId(), order.orderId()).orElse(null);
        return new GiftOrderResponse(order.orderId(), order.quoteId(), work == null ? Status.CREATED : Status.valueOf(work.status()), order.productCode(),
                order.productName(), new Price(Price.Currency.TURTLE_SHELL, order.amount()),
                work == null || work.chargedAmount() == null ? null : new Price(Price.Currency.TURTLE_SHELL, work.chargedAmount()),
                order.createdAt(), order.expiresAt());
    }
    public enum Status { CREATED, PENDING, ACTIVE, REFUNDED, FAILED, REVIEW_REQUIRED, CANCEL_PENDING, CANCELED }
    @Schema(name = "GiftOrderRequest", requiredProperties = {"quoteId", "recipientName", "senderName", "message"})
    public record Request(@NotNull UUID quoteId, @NotNull String recipientName,
                          @NotNull String senderName, @NotNull String message) {
        @Override public String toString() { return "GiftOrderRequest[REDACTED]"; }
    }
    @Schema(requiredProperties = {"orderId", "quoteId", "status", "productCode", "productName", "plannedCharge",
            "charged", "createdAt", "expiresAt"})
    public record GiftOrderResponse(UUID orderId, UUID quoteId, Status status, String productCode, String productName,
            Price plannedCharge, @Schema(nullable = true) Price charged,
            Instant createdAt, Instant expiresAt) {}
}
