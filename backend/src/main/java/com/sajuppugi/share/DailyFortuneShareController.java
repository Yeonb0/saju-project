package com.sajuppugi.share;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DailyFortuneShareController {
    private final DailyFortuneShareService shares;
    public DailyFortuneShareController(DailyFortuneShareService shares) { this.shares = shares; }

    @PostMapping("/daily-fortunes/today/share")
    @Operation(operationId = "createDailyFortuneShare", description = "Creates or reuses the active 24-hour anonymized share for the owned person and KST date.")
    public ApiResponse<DailyFortuneShareService.Created> create(Authentication authentication,
            @RequestParam UUID personId) {
        return ApiResponse.of(shares.create(user(authentication), personId));
    }

    @GetMapping("/daily-fortune-shares/{token}")
    @Operation(operationId = "getDailyFortuneShare", description = "Public anonymized snapshot. No person or user identifiers are returned.")
    public ApiResponse<DailyFortuneShareService.Snapshot> get(@PathVariable String token) {
        return ApiResponse.of(shares.get(token));
    }

    @DeleteMapping("/daily-fortune-shares/{shareId}")
    @Operation(operationId = "revokeDailyFortuneShare")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication, @PathVariable UUID shareId) {
        shares.revoke(user(authentication), shareId);
    }

    private UUID user(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException exception) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
