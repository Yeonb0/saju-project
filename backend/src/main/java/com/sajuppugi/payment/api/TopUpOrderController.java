package com.sajuppugi.payment.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.TopUpOrderService;
import com.sajuppugi.payment.domain.TopUpOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/top-up-orders")
public class TopUpOrderController {
    private final TopUpOrderService orders;

    public TopUpOrderController(TopUpOrderService orders) { this.orders = orders; }

    @PostMapping
    @Operation(operationId = "createTopUpOrder")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(Authentication authentication,
            @Parameter(required = true, schema = @Schema(minLength = 1, maxLength = 512))
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateRequest request) {
        return ApiResponse.of(response(orders.create(user(authentication), request.productCode(), key)));
    }

    @GetMapping("/{orderId}")
    @Operation(operationId = "getTopUpOrder")
    public ApiResponse<OrderResponse> get(Authentication authentication, @PathVariable UUID orderId) {
        return ApiResponse.of(response(orders.get(user(authentication), orderId)));
    }

    private UUID user(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException exception) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }

    private OrderResponse response(TopUpOrder order) {
        return new OrderResponse(order.id(), order.productCode(),
                "등껍질 " + order.paidShells() + " + 보너스 " + order.bonusShells() + "개",
                order.amountKrw(), "KRW", order.paidShells(), order.bonusShells(),
                Math.addExact(order.paidShells(), order.bonusShells()), order.status());
    }

    public record CreateRequest(@NotBlank @Size(max = 100) String productCode) {}
    @Schema(requiredProperties = {"orderId", "productCode", "orderName", "amount", "currency",
            "paidShellAmount", "bonusShellAmount", "creditedShellAmount", "status"})
    public record OrderResponse(UUID orderId, String productCode, String orderName, int amount, String currency,
            int paidShellAmount, int bonusShellAmount, int creditedShellAmount, TopUpOrder.Status status) {}
}
