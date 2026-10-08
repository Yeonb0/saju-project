package com.sajuppugi.fortune.reading.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.reading.application.GeneralReadingService;
import com.sajuppugi.fortune.reading.application.ReadingQueryService;
import com.sajuppugi.fortune.reading.application.SuneungReadingService;
import com.sajuppugi.fortune.reading.application.SuneungReadingService.PurchaseResult;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.RelationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SuneungReadingController {
    private final SuneungReadingService service;
    private final GeneralReadingService generalService;
    private final ReadingQueryService queryService;

    public SuneungReadingController(SuneungReadingService service, GeneralReadingService generalService,
                                     ReadingQueryService queryService) {
        this.service = service;
        this.generalService = generalService;
        this.queryService = queryService;
    }

    @PostMapping("/reading-purchases")
    public ResponseEntity<ApiResponse<PurchaseResponse>> purchase(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PurchaseRequest request) {
        PurchaseResult result = request.fortuneType() == null
                ? service.purchase(userId(authentication), request.quoteId(), request.personId(),
                        new IdempotencyKey(idempotencyKey))
                : generalService.purchase(userId(authentication), request.quoteId(), request.fortuneType(),
                        request.productOption(), request.personId(), request.counterpartPersonId(),
                        request.relationType(), request.questionKey(), new IdempotencyKey(idempotencyKey));
        HttpStatus status = result.status() == com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status.FULFILLED
                ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.of(PurchaseResponse.from(result)));
    }

    @PostMapping("/quotes/fortune")
    public ResponseEntity<ApiResponse<QuoteResponse>> quote(
            Authentication authentication, @Valid @RequestBody QuoteRequest request) {
        PurchaseQuote quote = request.fortuneType() == null
                ? service.issueQuote(userId(authentication), request.personId())
                : generalService.issueQuote(userId(authentication), request.fortuneType(), request.productOption(),
                        request.personId(), request.counterpartPersonId(), request.relationType(), request.questionKey());
        Event event = request.fortuneType() == null
                ? new Event("CSAT", com.sajuppugi.fortune.reading.application.SuneungEventPolicy.EXAM_DATE) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(new QuoteResponse(
                quote.id(), quote.productSnapshot().code(), quote.productSnapshot().price().currency().name(),
                quote.productSnapshot().price().amount(), quote.expiresAt(), event)));
    }

    @GetMapping("/readings")
    public ApiResponse<ReadingListResponse> list(
            Authentication authentication,
            @RequestParam(required = false) FortuneType fortuneType,
            @RequestParam(required = false) UUID personId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        ReadingQueryService.Page page = queryService.list(userId(authentication), fortuneType, personId, cursor, size);
        return ApiResponse.of(new ReadingListResponse(page.items().stream().map(ReadingSummary::from).toList(),
                page.nextCursor(), page.hasNext()));
    }

    @GetMapping("/readings/{readingId}")
    public ApiResponse<ReadingResponse> get(Authentication authentication, @PathVariable UUID readingId) {
        return ApiResponse.of(ReadingResponse.from(queryService.get(userId(authentication), readingId)));
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    public record PurchaseRequest(@NotNull UUID quoteId, @NotNull UUID personId,
                                  FortuneType fortuneType, ProductOption productOption, String questionKey,
                                  UUID counterpartPersonId, RelationType relationType) {}

    public record QuoteRequest(@NotNull UUID personId, FortuneType fortuneType, ProductOption productOption,
                               String questionKey, UUID counterpartPersonId, RelationType relationType) {}

    public record QuoteResponse(UUID quoteId, String productCode, String currency, int amount,
                                Instant expiresAt, Event event) {}

    public record PurchaseResponse(UUID purchaseId, UUID readingId, String status, Integer balance,
                                   boolean reused, ReadingResponse reading) {
        static PurchaseResponse from(PurchaseResult result) {
            return new PurchaseResponse(result.purchaseId(), result.readingId(), result.status().name(),
                    result.balance(), result.reused(), result.reading() == null ? null : ReadingResponse.from(result.reading()));
        }
    }

    public record ReadingResponse(UUID id, String fortuneType, String productOption, String subjectDisplayName,
                                  Counterpart counterpart, String questionKey, Event event,
                                  List<GeneratedSection> sections, String calculationVersion,
                                  String generationVersion, String contentVersion, String generationMode, Instant createdAt,
                                  List<String> disclaimers, Talisman talisman) {
        static ReadingResponse from(OwnedReading reading) {
            return new ReadingResponse(reading.id(), reading.fortuneType().name(), reading.productOption().name(),
                    reading.subjectDisplayName(), reading.counterpartPersonId() == null ? null : new Counterpart(
                            reading.counterpartPersonId(), reading.counterpartDisplayName(), reading.relationType()),
                    reading.questionKey(), reading.eventDate() == null ? null : new Event("CSAT", reading.eventDate()),
                    reading.sections().sections(), reading.calculationVersion(), reading.generationVersion(),
                    reading.contentVersion(), reading.generationMode().name(), reading.createdAt(), List.of("FOR_ENTERTAINMENT"),
                    reading.talismanId() == null ? null : new Talisman(reading.talismanId(), reading.talismanStatus()));
        }
    }

    public record ReadingListResponse(List<ReadingSummary> items, String nextCursor, boolean hasNext) {}

    public record ReadingSummary(UUID id, String fortuneType, String productOption, String subjectDisplayName,
                                 String counterpartDisplayName, String questionKey, String generationMode,
                                 Instant createdAt) {
        static ReadingSummary from(OwnedReading reading) {
            return new ReadingSummary(reading.id(), reading.fortuneType().name(), reading.productOption().name(),
                    reading.subjectDisplayName(), reading.counterpartDisplayName(), reading.questionKey(),
                    reading.generationMode().name(), reading.createdAt());
        }
    }

    public record Counterpart(UUID personId, String displayName, String relationType) {}

    public record Event(String type, LocalDate date) {}

    public record Talisman(UUID id, String status) {}
}
