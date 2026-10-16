package com.sajuppugi.fortune.talisman.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.talisman.application.TalismanAddonPurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/direct-payment-batches")
public class DirectPaymentTalismanAddonController {
    private final TalismanAddonPurchaseService purchases;
    public DirectPaymentTalismanAddonController(TalismanAddonPurchaseService purchases) { this.purchases = purchases; }
    @PostMapping("/{batchId}/talisman-fulfillment")
    public ApiResponse<TalismanAddonPurchaseService.View> fulfill(Authentication auth, @PathVariable UUID batchId,
            @Valid @RequestBody Request request) {
        try { return ApiResponse.of(purchases.purchaseDirect(UUID.fromString(auth.getName()), batchId, request.readingId(), request.quoteId())); }
        catch (IllegalArgumentException e) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
    public record Request(@NotNull UUID readingId, @NotNull UUID quoteId) {}
}
