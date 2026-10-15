package com.sajuppugi.admin.api;

import com.sajuppugi.admin.application.AdminGiftMaintenanceService;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.gift.application.GiftMaintenanceRecoveryPort.Kind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/gift-orders")
@ConditionalOnProperty(name = {"app.admin.enabled", "app.admin.recovery-enabled"}, havingValue = "true")
public class AdminGiftMaintenanceController {
    private final AdminGiftMaintenanceService maintenance;
    public AdminGiftMaintenanceController(AdminGiftMaintenanceService maintenance) { this.maintenance = maintenance; }
    @PostMapping("/{orderId}/maintenance-reopens")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminReopenGiftMaintenance", description = "Reopens an exhausted cleanup/key-rotation retry cycle only; REOPENED does not mean cleanup or rotation completed. No delivery, content or wallet mutation.")
    public ApiResponse<AdminGiftMaintenanceService.Reopened> reopen(Authentication auth, @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request request) {
        return ApiResponse.of(maintenance.request(UUID.fromString(auth.getName()), orderId, request.kind(), key, request.reasonCode()));
    }
    public record Request(@NotNull Kind kind, @NotNull AdminGiftMaintenanceService.Reason reasonCode) {}
}
