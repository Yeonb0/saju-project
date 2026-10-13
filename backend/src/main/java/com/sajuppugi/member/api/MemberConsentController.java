package com.sajuppugi.member.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.member.application.MemberConsentService;
import com.sajuppugi.member.application.MemberConsentService.ConsentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/consents")
public class MemberConsentController {
    private final MemberConsentService service;

    public MemberConsentController(MemberConsentService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "getMemberConsents")
    public ApiResponse<ConsentStatus> get(Authentication authentication) {
        return ApiResponse.of(service.status(userId(authentication)));
    }

    @PostMapping
    @Operation(operationId = "acceptMemberConsents")
    public ApiResponse<ConsentStatus> accept(Authentication authentication,
            @Valid @RequestBody ConsentRequest request) {
        return ApiResponse.of(service.accept(userId(authentication), request.ageOver14Confirmed(),
                request.termsAgreed(), request.privacyAgreed(), request.marketingAgreed()));
    }

    private UUID userId(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException exception) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }

    public record ConsentRequest(@NotNull Boolean ageOver14Confirmed, @NotNull Boolean termsAgreed,
            @NotNull Boolean privacyAgreed, @NotNull Boolean marketingAgreed) {}
}
