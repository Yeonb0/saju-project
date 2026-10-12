package com.sajuppugi.payment.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.CashRefundService;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/top-up-orders/{orderId}/refund")
public class CashRefundController {
    private final CashRefundService refunds;
    public CashRefundController(CashRefundService refunds) { this.refunds = refunds; }
    @PostMapping
    @Operation(operationId = "requestTopUpRefund", description = "Full unused top-up only, policy-gated. REFUNDED alone proves verified cash cancellation; otherwise poll this resource.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resolved or review required", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Reserved or processing; poll refund", useReturnTypeSchema = true)})
    public ResponseEntity<ApiResponse<JdbcCashRefunds.View>> request(Authentication auth, @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String key) {
        var view = refunds.request(user(auth), orderId, key);
        return ResponseEntity.status("REFUNDED".equals(view.state()) || "REVIEW_REQUIRED".equals(view.state()) ? 200 : 202).body(ApiResponse.of(view));
    }
    @GetMapping
    @Operation(operationId = "getTopUpRefund")
    public ApiResponse<JdbcCashRefunds.View> get(Authentication auth, @PathVariable UUID orderId) { return ApiResponse.of(refunds.get(user(auth), orderId)); }
    private UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); }
        catch (RuntimeException failure) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
