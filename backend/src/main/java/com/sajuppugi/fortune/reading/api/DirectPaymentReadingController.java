package com.sajuppugi.fortune.reading.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.reading.application.GeneralReadingService;
import com.sajuppugi.fortune.reading.application.SuneungReadingService.PurchaseResult;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.RelationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** New-sale endpoint.  The legacy wallet purchase endpoints remain unchanged for historical use. */
@RestController
@RequestMapping("/api/v1/direct-payment-batches")
public class DirectPaymentReadingController {
    private final GeneralReadingService readings;
    public DirectPaymentReadingController(GeneralReadingService readings) { this.readings = readings; }

    @PostMapping("/{batchId}/reading-fulfillment")
    public ResponseEntity<ApiResponse<Response>> fulfill(Authentication authentication, @PathVariable UUID batchId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request request) {
        PurchaseResult result = readings.purchaseDirect(user(authentication), batchId, request.quoteId(), request.fortuneType(),
                request.productOption(), request.personId(), request.counterpartPersonId(), request.relationType(),
                request.questionKey(), request.personalizedConcern(), new IdempotencyKey(key));
        return ResponseEntity.status(result.status().name().equals("FULFILLED") ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(ApiResponse.of(new Response(result.purchaseId(), result.readingId(), result.status().name(), result.reused())));
    }
    private UUID user(Authentication auth) { try { return UUID.fromString(auth.getName()); } catch (RuntimeException e) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); } }
    public record Request(@NotNull UUID quoteId, @NotNull UUID personId, @NotNull FortuneType fortuneType,
            @NotNull ProductOption productOption, @NotBlank String questionKey, UUID counterpartPersonId,
            RelationType relationType, @Size(max=100) String personalizedConcern) {}
    public record Response(UUID purchaseId, UUID readingId, String status, boolean reused) {}
}
