package com.sajuppugi.admin.api;

import com.sajuppugi.admin.application.AdminPaymentRecoveryService;
import com.sajuppugi.admin.infrastructure.JdbcAdminRecovery;
import com.sajuppugi.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(name = {"app.admin.enabled", "app.admin.recovery-enabled"}, havingValue = "true")
public class AdminRecoveryController {
    private final AdminPaymentRecoveryService service;
    private final JdbcAdminRecovery commands;
    public AdminRecoveryController(AdminPaymentRecoveryService service, JdbcAdminRecovery commands) { this.service = service; this.commands = commands; }
    @PostMapping("/top-up-orders/{orderId}/reconcile")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminReconcileTopUpOrder", description = "Privileged lookup-only recovery; no provider approval/cancel POST, arbitrary balance adjustment or wallet unblock.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resolved or review required", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Processing; poll command", useReturnTypeSchema = true)})
    public ResponseEntity<ApiResponse<JdbcAdminRecovery.View>> reconcile(Authentication auth, @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request request) {
        var view = service.request(UUID.fromString(auth.getName()), orderId, key, request.reasonCode());
        return ResponseEntity.status(java.util.List.of("COMPLETED","REVIEW_REQUIRED").contains(view.state()) ? 200 : 202).body(ApiResponse.of(view));
    }
    @GetMapping("/recovery-commands/{commandId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminGetRecoveryCommand")
    public ApiResponse<JdbcAdminRecovery.View> get(Authentication auth, @PathVariable UUID commandId) { return ApiResponse.of(commands.view(UUID.fromString(auth.getName()), commandId)); }
    public record Request(@NotNull JdbcAdminRecovery.Reason reasonCode) {}
}
