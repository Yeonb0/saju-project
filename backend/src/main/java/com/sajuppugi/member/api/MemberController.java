package com.sajuppugi.member.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.member.application.MemberService;
import com.sajuppugi.member.domain.Member;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MemberController {
    private final MemberService service;

    public MemberController(MemberService service) { this.service = service; }

    @GetMapping
    public ApiResponse<MemberResponse> get(Authentication authentication) {
        return ApiResponse.of(MemberResponse.from(service.profile(userId(authentication))));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<MemberResponse>> withdraw(Authentication authentication) {
        UUID userId = userId(authentication);
        service.requestWithdrawal(userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(MemberResponse.from(service.profile(userId))));
    }

    private UUID userId(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException exception) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }

    public record MemberResponse(UUID id, String nickname, String status, int walletBalance,
                                 boolean hasPrimaryPerson, Instant createdAt,
                                 Instant withdrawalRequestedAt, Instant scheduledDeletionAt) {
        static MemberResponse from(MemberService.MemberProfile profile) {
            Member member = profile.member();
            return response(member, profile.walletBalance(), profile.hasPrimaryPerson());
        }

        private static MemberResponse response(Member member, int walletBalance, boolean hasPrimaryPerson) {
            Instant deletion = member.withdrawalRequestedAt() == null ? null
                    : member.withdrawalRequestedAt().plus(MemberService.WITHDRAWAL_RETENTION);
            return new MemberResponse(member.id(), member.nickname(), member.status().name(), walletBalance,
                    hasPrimaryPerson, member.createdAt(), member.withdrawalRequestedAt(), deletion);
        }
    }
}
