package com.sajuppugi.admin.api;

import com.sajuppugi.admin.application.AdminCashRefundService;
import com.sajuppugi.admin.infrastructure.JdbcAdminRefundCases;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.wallet.application.RemainingTopUpRefund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/refund-cases")
@ConditionalOnProperty(name = {"app.admin.enabled", "app.admin.recovery-enabled"}, havingValue = "true")
public class AdminCashRefundController {
    private final JdbcAdminRefundCases cases;
    private final AdminCashRefundService service;
    public AdminCashRefundController(JdbcAdminRefundCases cases, AdminCashRefundService service) { this.cases = cases; this.service = service; }
    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminOpenRefundCase", description = "Register a verified customer-center inquiry without calling PG or reclaiming credits.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Case registered or same-key replay", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<JdbcAdminRefundCases.CaseView>> open(Authentication auth,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Intake request) {
        var result = cases.open(UUID.fromString(auth.getName()), request.orderId(), request.customerUserId(), request.supportReference(), request.receivedAt(), key);
        return ResponseEntity.status(201).body(ApiResponse.of(result));
    }
    @GetMapping("/{caseId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminGetRefundCase")
    public ApiResponse<JdbcAdminRefundCases.CaseView> get(Authentication auth, @PathVariable UUID caseId) {
        return ApiResponse.of(cases.view(UUID.fromString(auth.getName()), caseId));
    }
    @GetMapping("/{caseId}/quote")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminQuoteRemainingRefund", description = "Current order-specific paid remainder, zero fee, remaining bonus reclamation. Does not reserve credits.")
    public ApiResponse<JdbcAdminRefundCases.Quote> quote(Authentication auth, @PathVariable UUID caseId) {
        return ApiResponse.of(cases.quote(UUID.fromString(auth.getName()), caseId));
    }
    @PostMapping("/{caseId}/execute")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminExecuteRemainingRefund", description = "Revalidate the exact quote and persist the financial intent and audit before provider cancellation. Unknown outcomes must be polled, not submitted with a new amount.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Refunded, rejected, or review required", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Reserved or processing", useReturnTypeSchema = true)})
    public ResponseEntity<ApiResponse<JdbcAdminRefundCases.CaseView>> execute(Authentication auth, @PathVariable UUID caseId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Execution request) {
        var result = service.execute(UUID.fromString(auth.getName()), caseId, key,
                new RemainingTopUpRefund(request.expectedAmount(), request.expectedPaidShells(), request.expectedBonusShells()));
        return ResponseEntity.status(List.of("REFUNDED", "REJECTED", "REVIEW_REQUIRED").contains(result.state()) ? 200 : 202).body(ApiResponse.of(result));
    }
    public record Intake(@NotNull UUID orderId, @NotNull UUID customerUserId,
                         @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String supportReference, @NotNull Instant receivedAt) {}
    public record Execution(@NotNull @Positive Integer expectedAmount, @NotNull @Positive Integer expectedPaidShells,
                            @NotNull @PositiveOrZero Integer expectedBonusShells) {}
}
