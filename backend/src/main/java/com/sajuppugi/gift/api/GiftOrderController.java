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
    public GiftOrderController(GiftOrderService orders) { this.orders = orders; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGiftOrder", description = "Local/test CREATED draft only. No debit, token or delivery; charged is null.")
    public ApiResponse<GiftOrderResponse> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request request) {
        return ApiResponse.of(response(orders.create(user(authentication), request.quoteId(),
                new GiftOrderService.Payload(request.recipientName(), request.recipientPhone(), request.senderName(), request.message()), key)));
    }
    @GetMapping("/{orderId}")
    @Operation(operationId = "getGiftOrder")
    public ApiResponse<GiftOrderResponse> get(Authentication authentication, @PathVariable UUID orderId) {
        return ApiResponse.of(response(orders.get(user(authentication), orderId)));
    }
    private UUID user(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException invalid) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
    private GiftOrderResponse response(GiftOrderService.Order order) {
        return new GiftOrderResponse(order.orderId(), order.quoteId(), Status.CREATED, order.productCode(),
                order.productName(), new Price(Price.Currency.TURTLE_SHELL, order.amount()), null,
                order.recipientPhoneMasked(), order.createdAt(), order.expiresAt());
    }
    public enum Status { CREATED }
    @Schema(name = "GiftOrderRequest", requiredProperties = {"quoteId", "recipientName", "recipientPhone", "senderName", "message"})
    public record Request(@NotNull UUID quoteId, @NotNull String recipientName, @NotNull String recipientPhone,
                          @NotNull String senderName, @NotNull String message) {
        @Override public String toString() { return "GiftOrderRequest[REDACTED]"; }
    }
    @Schema(requiredProperties = {"orderId", "quoteId", "status", "productCode", "productName", "plannedCharge",
            "charged", "recipientPhoneMasked", "createdAt", "expiresAt"})
    public record GiftOrderResponse(UUID orderId, UUID quoteId, Status status, String productCode, String productName,
            Price plannedCharge, @Schema(nullable = true) Price charged, String recipientPhoneMasked,
            Instant createdAt, Instant expiresAt) {}
}
