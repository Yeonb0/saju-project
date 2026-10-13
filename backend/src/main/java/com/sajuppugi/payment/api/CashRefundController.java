package com.sajuppugi.payment.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.CashRefundService;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/top-up-orders/{orderId}/refund")
public class CashRefundController {
    private final CashRefundService refunds;
    public CashRefundController(CashRefundService refunds) { this.refunds = refunds; }
    @GetMapping
    @Operation(operationId = "getTopUpRefund")
    public ApiResponse<JdbcCashRefunds.View> get(Authentication auth, @PathVariable UUID orderId) { return ApiResponse.of(refunds.get(user(auth), orderId)); }
    private UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); }
        catch (RuntimeException failure) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
