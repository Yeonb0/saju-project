package com.sajuppugi.fortune.talisman.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.talisman.application.TalismanAddonPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/readings/{readingId}/talisman-addon-purchases", produces = "application/json")
public class TalismanAddonPurchaseController {
    private final TalismanAddonPurchaseService purchases;
    public TalismanAddonPurchaseController(TalismanAddonPurchaseService purchases) { this.purchases = purchases; }

    @PostMapping
    @Operation(operationId = "purchaseTalismanAddon")
    public ApiResponse<TalismanAddonPurchaseService.View> purchase(Authentication auth, @PathVariable UUID readingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody Request request) {
        try { return ApiResponse.of(purchases.purchase(UUID.fromString(auth.getName()), readingId, request.quoteId(),
                new IdempotencyKey(idempotencyKey))); }
        catch (IllegalArgumentException invalid) { throw new ApiException(ErrorCode.INVALID_REQUEST); }
    }
    public record Request(@NotNull UUID quoteId) {}
}
