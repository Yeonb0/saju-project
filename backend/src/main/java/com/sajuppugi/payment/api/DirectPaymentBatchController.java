package com.sajuppugi.payment.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.DirectPaymentBatchService;
import com.sajuppugi.payment.application.DirectPaymentConfirmationService;
import com.sajuppugi.payment.domain.DirectPaymentBatch;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** New KRW checkout API.  It intentionally has no wallet balance or top-up suggestion fields. */
@RestController
@RequestMapping("/api/v1/direct-payment-batches")
public class DirectPaymentBatchController {
    private final DirectPaymentBatchService batches; private final DirectPaymentConfirmationService confirmations;
    public DirectPaymentBatchController(DirectPaymentBatchService batches, DirectPaymentConfirmationService confirmations) { this.batches=batches; this.confirmations=confirmations; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(operationId="createDirectPaymentBatch")
    public ApiResponse<Response> create(Authentication auth, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateRequest request) {
        return ApiResponse.of(view(batches.create(user(auth),request.quoteId(),request.quantity(),key)));
    }
    @GetMapping("/{batchId}") @Operation(operationId="getDirectPaymentBatch")
    public ApiResponse<Response> get(Authentication auth,@PathVariable UUID batchId) { return ApiResponse.of(view(batches.get(user(auth),batchId))); }
    @PostMapping("/{batchId}/confirm") @Operation(operationId="confirmDirectPaymentBatch")
    public ResponseEntity<ApiResponse<Response>> confirm(Authentication auth,@PathVariable UUID batchId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ConfirmRequest request) {
        if (!batchId.equals(request.orderId())) throw new ApiException(ErrorCode.INVALID_REQUEST);
        DirectPaymentBatch batch=confirmations.confirm(user(auth),batchId,request.paymentKey(),request.amount(),key);
        return ResponseEntity.status(batch.status()==DirectPaymentBatch.Status.PAYMENT_PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK).body(ApiResponse.of(view(batch)));
    }
    private UUID user(Authentication auth) { try{return UUID.fromString(auth.getName());}catch(RuntimeException e){throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);} }
    private Response view(DirectPaymentBatch b) { return new Response(b.id(),b.productCode(),b.productName(),b.totalAmountKrw(),"KRW",b.status(),b.createdAt()); }
    public record CreateRequest(@NotNull UUID quoteId,@NotNull @Positive Integer quantity) {}
    public record ConfirmRequest(@NotBlank @Size(max=200) String paymentKey,@NotNull UUID orderId,@NotNull @Positive Integer amount) { @Override public String toString(){return "ConfirmRequest[paymentKey=REDACTED,orderId="+orderId+",amount="+amount+"]";} }
    public record Response(UUID orderId,String productCode,String orderName,int amount,String currency,DirectPaymentBatch.Status status,Instant createdAt) {}
}
