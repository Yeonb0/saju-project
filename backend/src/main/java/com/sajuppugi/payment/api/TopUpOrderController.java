package com.sajuppugi.payment.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.TopUpOrderService;
import com.sajuppugi.payment.application.TopUpPaymentService;
import com.sajuppugi.payment.application.TopUpUseCase;
import com.sajuppugi.payment.domain.TopUpOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/top-up-orders")
public class TopUpOrderController {
    private final TopUpOrderService orders;
    private final TopUpPaymentService payments;

    public TopUpOrderController(TopUpOrderService orders, TopUpPaymentService payments) {
        this.orders = orders; this.payments = payments;
    }

    @PostMapping
    @Operation(operationId = "createTopUpOrder")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(Authentication authentication,
            @Parameter(required = true, schema = @Schema(minLength = 1, maxLength = 512))
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateRequest request) {
        UUID user = user(authentication);
        var order = orders.create(user, request.productCode(), key);
        return ApiResponse.of(response(payments.get(user, order.id())));
    }

    @GetMapping("/{orderId}")
    @Operation(operationId = "getTopUpOrder")
    public ApiResponse<OrderResponse> get(Authentication authentication, @PathVariable UUID orderId) {
        return ApiResponse.of(response(payments.get(user(authentication), orderId)));
    }

    @GetMapping
    @Operation(operationId = "listTopUpOrders", description = "Lists only the authenticated user's top-up orders, newest first.")
    public ApiResponse<OrderListResponse> list(Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        UUID user = user(authentication);
        var page = orders.list(user, cursor, size);
        List<OrderResponse> items = page.items().stream()
                .map(order -> response(payments.get(user, order.id())))
                .toList();
        return ApiResponse.of(new OrderListResponse(items, page.nextCursor(), page.hasNext()));
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(operationId = "confirmTopUpOrder", description = "200 for resolved orders, 202 while processing. Only CREDITED proves a committed wallet grant.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resolved order", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Processing; poll owned order", useReturnTypeSchema = true)})
    public ResponseEntity<ApiResponse<OrderResponse>> confirm(Authentication authentication, @PathVariable UUID orderId,
            @Parameter(required = true, schema = @Schema(minLength = 1, maxLength = 512))
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ConfirmRequest request) {
        if (!orderId.equals(request.orderId())) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var view = payments.confirm(user(authentication), orderId,
                new TopUpUseCase.Confirmation(request.paymentKey(), request.amount()), key);
        return ResponseEntity.status(view.processing() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(ApiResponse.of(response(view)));
    }

    private UUID user(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException exception) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }

    private OrderResponse response(TopUpUseCase.OrderView view) {
        var order = view.order();
        return new OrderResponse(order.id(), order.productCode(),
                "등껍질 " + order.paidShells() + " + 보너스 " + order.bonusShells() + "개",
                order.amountKrw(), "KRW", order.paidShells(), order.bonusShells(),
                Math.addExact(order.paidShells(), order.bonusShells()), order.status(), view.processing(),
                view.walletBalance() == null ? null : view.walletBalance().balance(),
                order.status() == TopUpOrder.Status.CREDITED ? Math.addExact(order.paidShells(), order.bonusShells()) : null,
                view.nextAction(), view.failureCode(), order.createdAt());
    }

    public record CreateRequest(@NotBlank @Size(max = 100) String productCode) {}
    public record ConfirmRequest(@NotBlank @Size(max = 200) String paymentKey, @NotNull UUID orderId,
                                 @NotNull @Positive @JsonDeserialize(using = StrictAmountDeserializer.class) Integer amount) {
        @Override public String toString() { return "ConfirmRequest[paymentKey=REDACTED, orderId=" + orderId + ", amount=" + amount + "]"; }
    }
    public static class StrictAmountDeserializer extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return context.reportInputMismatch(Integer.class, "Payment amount must be an integer JSON number");
            }
            return parser.getIntValue();
        }
    }
    @Schema(requiredProperties = {"orderId", "productCode", "orderName", "amount", "currency",
            "paidShellAmount", "bonusShellAmount", "creditedShellAmount", "status", "processing", "walletBalance",
            "grantedShellAmount", "nextAction", "failureCode", "createdAt"})
    public record OrderResponse(UUID orderId, String productCode, String orderName, int amount, String currency,
            int paidShellAmount, int bonusShellAmount, int creditedShellAmount, TopUpOrder.Status status,
            boolean processing, @Schema(types = {"integer", "null"}) Integer walletBalance,
            @Schema(types = {"integer", "null"}) Integer grantedShellAmount, TopUpUseCase.NextAction nextAction,
            @Schema(types = {"string", "null"}) String failureCode, Instant createdAt) {}
    @Schema(requiredProperties = {"items", "nextCursor", "hasNext"})
    public record OrderListResponse(List<OrderResponse> items,
            @Schema(types = {"string", "null"}) String nextCursor, boolean hasNext) {}
}
