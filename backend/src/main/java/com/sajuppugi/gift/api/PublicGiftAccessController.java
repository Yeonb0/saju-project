package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.gift.application.GiftAccessPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping(value = "/api/v1/public/gifts", produces = "application/json")
public class PublicGiftAccessController {
    private final GiftAccessPort access;

    public PublicGiftAccessController(GiftAccessPort access) { this.access = access; }

    @PostMapping(value = "/access", consumes = "application/json")
    @Operation(operationId = "getPublicGiftAccess",
            description = "Returns minimal gift guidance. The bearer token is accepted only in the JSON body and must not be logged.")
    public ApiResponse<GiftAccessPort.PublicGift> access(@Valid @RequestBody Request request) {
        return ApiResponse.of(access.describe(request.token()));
    }

    @Schema(name = "PublicGiftAccessRequest", requiredProperties = "token")
    public record Request(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "PublicGiftAccessRequest[token=REDACTED]"; }
    }
}
