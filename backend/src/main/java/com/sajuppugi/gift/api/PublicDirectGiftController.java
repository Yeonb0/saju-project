package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.gift.application.DirectGiftFulfillmentService;
import com.sajuppugi.gift.application.GiftAccessPort;
import com.sajuppugi.gift.application.PublicGiftRateLimiter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@RequestMapping("/api/v1/public/direct-gifts")
public class PublicDirectGiftController {
    private final DirectGiftFulfillmentService service;
    private final GiftAccessPort access;
    private final PublicGiftRateLimiter limits;

    public PublicDirectGiftController(DirectGiftFulfillmentService service, GiftAccessPort access,
            PublicGiftRateLimiter limits) {
        this.service = service;
        this.access = access;
        this.limits = limits;
    }

    @PostMapping("/access")
    public ApiResponse<DirectGiftFulfillmentService.PublicGift> access(@Valid @RequestBody Request request,
            HttpServletRequest http) {
        limits.check(request.token(), http.getRemoteAddr());
        access.verify(request.token());
        return ApiResponse.of(service.access(request.token()));
    }

    @Schema(name = "PublicDirectGiftAccessRequest", requiredProperties = "token")
    public record Request(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "DirectGiftAccess[token=REDACTED]"; }
    }
}
