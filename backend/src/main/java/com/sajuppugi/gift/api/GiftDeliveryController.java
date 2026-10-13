package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.application.GiftDeliveryService;
import com.sajuppugi.gift.application.GiftDeliveryUseCase.DeliveryView;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@RequestMapping(value = "/api/v1/gift-orders/{orderId}", produces = "application/json")
public class GiftDeliveryController {
    private final GiftDeliveryService deliveries;
    public GiftDeliveryController(GiftDeliveryService deliveries) { this.deliveries = deliveries; }
    @GetMapping("/delivery")
    @Operation(operationId = "getGiftDelivery")
    public ApiResponse<DeliveryView> get(Authentication auth, @PathVariable UUID orderId) { return ApiResponse.of(deliveries.get(user(auth), orderId)); }
    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "resendGiftAlimtalk", description = "Queue a new token and delivery job, not proof of sending. Daily manual quota shared with reissues.")
    public ApiResponse<DeliveryView> resend(Authentication auth, @PathVariable UUID orderId, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.of(deliveries.resend(user(auth), orderId, new IdempotencyKey(key)));
    }
    @PostMapping("/link-reissues")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "reissueGiftLink", description = "Revoke old token and queue the replacement without extending gift expiry. Raw token is never returned.")
    public ApiResponse<DeliveryView> reissue(Authentication auth, @PathVariable UUID orderId, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.of(deliveries.reissueLink(user(auth), orderId, new IdempotencyKey(key)));
    }
    private UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); }
        catch (RuntimeException invalid) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
