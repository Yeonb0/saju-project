package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.application.GiftLinkService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@RequestMapping(value = "/api/v1/gift-orders/{orderId}", produces = "application/json")
public class GiftLinkController {
    private final GiftLinkService links;
    public GiftLinkController(GiftLinkService links) { this.links = links; }
    @GetMapping("/link")
    @Operation(operationId = "getGiftLink", description = "Buyer-only retrieval during the bounded token reveal window.")
    public ApiResponse<GiftLinkService.Link> get(Authentication auth, @PathVariable UUID orderId) {
        return ApiResponse.of(links.get(user(auth), orderId));
    }
    @PostMapping("/link-reissues")
    @Operation(operationId = "reissueGiftLink", description = "Explicitly rotates the bearer token without extending its original expiry.")
    public ApiResponse<GiftLinkService.Link> reissue(Authentication auth, @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.of(links.reissue(user(auth), orderId, new IdempotencyKey(key)));
    }
    private UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); }
        catch (RuntimeException invalid) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
