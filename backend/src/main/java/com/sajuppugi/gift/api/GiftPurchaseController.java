package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftCancellationService;
import com.sajuppugi.gift.application.GiftCommandTransactions;
import com.sajuppugi.gift.application.GiftPurchaseService;
import com.sajuppugi.gift.application.GiftPurchaseTransactions;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork.Work;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@RequestMapping(value = "/api/v1/gift-orders/{orderId}", produces = "application/json")
public class GiftPurchaseController {
    private final GiftCommandTransactions commands;
    private final GiftPurchaseService purchases;
    private final GiftPurchaseTransactions transactions;
    private final GiftCancellationService cancellations;
    public GiftPurchaseController(GiftCommandTransactions commands, GiftPurchaseService purchases,
            GiftPurchaseTransactions transactions, GiftCancellationService cancellations) {
        this.commands = commands; this.purchases = purchases; this.transactions = transactions; this.cancellations = cancellations;
    }
    @PostMapping("/purchase")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resolved financial state; ACTIVE does not imply delivery")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Purchase pending; poll the same order")
    @Operation(operationId = "purchaseGiftOrder", description = "ACTIVE means a debit-backed gift grant, not delivery or content completion.")
    public ResponseEntity<ApiResponse<GiftPurchaseResponse>> purchase(Authentication auth, @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String key) {
        UUID user = user(auth);
        commands.purchase(user, orderId, key);
        return response(purchases.resume(user, orderId));
    }
    @GetMapping("/purchase")
    @Operation(operationId = "getGiftPurchase")
    public ApiResponse<GiftPurchaseResponse> get(Authentication auth, @PathVariable UUID orderId) {
        return ApiResponse.of(view(transactions.get(user(auth), orderId)));
    }
    @PostMapping("/cancel")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resolved return or review state; inspect status")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Shell return pending; poll the same order")
    @Operation(operationId = "cancelGiftOrder", description = "Unused gift within seven days of committed shell debit; returns shells, never cash.")
    public ResponseEntity<ApiResponse<GiftPurchaseResponse>> cancel(Authentication auth, @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String key) {
        UUID user = user(auth);
        cancellations.cancel(user, orderId, key);
        return response(transactions.get(user, orderId));
    }
    private ResponseEntity<ApiResponse<GiftPurchaseResponse>> response(Work work) {
        return ResponseEntity.status(java.util.List.of("PENDING", "CANCEL_PENDING").contains(work.status()) ? 202 : 200)
                .body(ApiResponse.of(view(work)));
    }
    private GiftPurchaseResponse view(Work work) {
        boolean returned = java.util.List.of("CANCELED", "REFUNDED").contains(work.status());
        String next = java.util.List.of("PENDING", "CANCEL_PENDING").contains(work.status()) ? "POLL"
                : "REVIEW_REQUIRED".equals(work.status()) ? "CONTACT_SUPPORT" : "NONE";
        return new GiftPurchaseResponse(work.orderId(), work.status(), work.giftId(), work.chargedAmount(),
                returned ? work.chargedAmount() : null, work.approvedAt(), next);
    }
    private UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); }
        catch (RuntimeException invalid) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
    @Schema(requiredProperties = {"orderId","status","giftId","chargedShells","returnedShells","approvedAt","nextAction"})
    public record GiftPurchaseResponse(UUID orderId, String status,
            @Schema(types = {"string","null"}, format = "uuid") UUID giftId,
            @Schema(types = {"integer","null"}) Integer chargedShells,
            @Schema(types = {"integer","null"}) Integer returnedShells,
            @Schema(types = {"string","null"}, format = "date-time") Instant approvedAt, String nextAction) {}
}
