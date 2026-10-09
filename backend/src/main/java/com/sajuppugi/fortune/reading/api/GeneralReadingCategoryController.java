package com.sajuppugi.fortune.reading.api;

import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.application.GeneralReadingService;
import com.sajuppugi.fortune.reading.application.ReadingQueryService;
import com.sajuppugi.fortune.reading.application.SuneungReadingService.PurchaseResult;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.RelationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

/** Category-specific HTTP contract. Payment and generation orchestration remain shared internally. */
@RestController
@RequestMapping("/api/v1")
public class GeneralReadingCategoryController {
    private final GeneralReadingService readings;
    private final ReadingQueryService queries;

    public GeneralReadingCategoryController(GeneralReadingService readings, ReadingQueryService queries) {
        this.readings = readings;
        this.queries = queries;
    }

    @PostMapping("/quotes/fortune/overall")
    public ResponseEntity<ApiResponse<CategoryQuoteResponse>> quoteOverall(
            Authentication authentication, @Valid @RequestBody StandardQuoteRequest request) {
        return quote(authentication, FortuneType.OVERALL, request.personId(), request.productOption(),
                request.questionKey(), null, null);
    }

    @PostMapping("/quotes/fortune/love")
    public ResponseEntity<ApiResponse<CategoryQuoteResponse>> quoteLove(
            Authentication authentication, @Valid @RequestBody StandardQuoteRequest request) {
        return quote(authentication, FortuneType.LOVE, request.personId(), request.productOption(),
                request.questionKey(), null, null);
    }

    @PostMapping("/quotes/fortune/wealth")
    public ResponseEntity<ApiResponse<CategoryQuoteResponse>> quoteWealth(
            Authentication authentication, @Valid @RequestBody StandardQuoteRequest request) {
        return quote(authentication, FortuneType.WEALTH, request.personId(), request.productOption(),
                request.questionKey(), null, null);
    }

    @PostMapping("/quotes/fortune/compatibility")
    public ResponseEntity<ApiResponse<CategoryQuoteResponse>> quoteCompatibility(
            Authentication authentication, @Valid @RequestBody CompatibilityQuoteRequest request) {
        return quote(authentication, FortuneType.COMPATIBILITY, request.personId(), request.productOption(),
                request.questionKey(), request.counterpartPersonId(), request.relationType());
    }

    @PostMapping("/quotes/fortune/sinsal")
    public ResponseEntity<ApiResponse<CategoryQuoteResponse>> quoteSinsal(
            Authentication authentication, @Valid @RequestBody StandardQuoteRequest request) {
        return quote(authentication, FortuneType.SINSAL, request.personId(), request.productOption(),
                request.questionKey(), null, null);
    }

    @PostMapping("/reading-purchases/overall")
    public ResponseEntity<ApiResponse<CategoryPurchaseResponse<OverallReadingResponse>>> purchaseOverall(
            Authentication authentication, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StandardPurchaseRequest request) {
        PurchaseResult result = purchase(authentication, key, FortuneType.OVERALL, request.quoteId(),
                request.personId(), request.productOption(), request.questionKey(), null, null);
        return purchaseResponse(result, this::overall);
    }

    @PostMapping("/reading-purchases/love")
    public ResponseEntity<ApiResponse<CategoryPurchaseResponse<LoveReadingResponse>>> purchaseLove(
            Authentication authentication, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StandardPurchaseRequest request) {
        PurchaseResult result = purchase(authentication, key, FortuneType.LOVE, request.quoteId(),
                request.personId(), request.productOption(), request.questionKey(), null, null);
        return purchaseResponse(result, this::love);
    }

    @PostMapping("/reading-purchases/wealth")
    public ResponseEntity<ApiResponse<CategoryPurchaseResponse<WealthReadingResponse>>> purchaseWealth(
            Authentication authentication, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StandardPurchaseRequest request) {
        PurchaseResult result = purchase(authentication, key, FortuneType.WEALTH, request.quoteId(),
                request.personId(), request.productOption(), request.questionKey(), null, null);
        return purchaseResponse(result, this::wealth);
    }

    @PostMapping("/reading-purchases/compatibility")
    public ResponseEntity<ApiResponse<CategoryPurchaseResponse<CompatibilityReadingResponse>>> purchaseCompatibility(
            Authentication authentication, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CompatibilityPurchaseRequest request) {
        PurchaseResult result = purchase(authentication, key, FortuneType.COMPATIBILITY, request.quoteId(),
                request.personId(), request.productOption(), request.questionKey(),
                request.counterpartPersonId(), request.relationType());
        return purchaseResponse(result, this::compatibility);
    }

    @PostMapping("/reading-purchases/sinsal")
    public ResponseEntity<ApiResponse<CategoryPurchaseResponse<SinsalReadingResponse>>> purchaseSinsal(
            Authentication authentication, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody StandardPurchaseRequest request) {
        PurchaseResult result = purchase(authentication, key, FortuneType.SINSAL, request.quoteId(),
                request.personId(), request.productOption(), request.questionKey(), null, null);
        return purchaseResponse(result, this::sinsal);
    }

    @GetMapping("/readings/overall")
    public ApiResponse<CategoryListResponse> listOverall(Authentication authentication,
            @RequestParam(required = false) UUID personId, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return list(authentication, FortuneType.OVERALL, personId, cursor, size);
    }

    @GetMapping("/readings/love")
    public ApiResponse<CategoryListResponse> listLove(Authentication authentication,
            @RequestParam(required = false) UUID personId, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return list(authentication, FortuneType.LOVE, personId, cursor, size);
    }

    @GetMapping("/readings/wealth")
    public ApiResponse<CategoryListResponse> listWealth(Authentication authentication,
            @RequestParam(required = false) UUID personId, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return list(authentication, FortuneType.WEALTH, personId, cursor, size);
    }

    @GetMapping("/readings/compatibility")
    public ApiResponse<CategoryListResponse> listCompatibility(Authentication authentication,
            @RequestParam(required = false) UUID personId, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return list(authentication, FortuneType.COMPATIBILITY, personId, cursor, size);
    }

    @GetMapping("/readings/sinsal")
    public ApiResponse<CategoryListResponse> listSinsal(Authentication authentication,
            @RequestParam(required = false) UUID personId, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return list(authentication, FortuneType.SINSAL, personId, cursor, size);
    }

    @GetMapping("/readings/overall/{readingId}")
    public ApiResponse<OverallReadingResponse> getOverall(Authentication authentication, @PathVariable UUID readingId) {
        return ApiResponse.of(overall(owned(authentication, readingId, FortuneType.OVERALL)));
    }

    @GetMapping("/readings/love/{readingId}")
    public ApiResponse<LoveReadingResponse> getLove(Authentication authentication, @PathVariable UUID readingId) {
        return ApiResponse.of(love(owned(authentication, readingId, FortuneType.LOVE)));
    }

    @GetMapping("/readings/wealth/{readingId}")
    public ApiResponse<WealthReadingResponse> getWealth(Authentication authentication, @PathVariable UUID readingId) {
        return ApiResponse.of(wealth(owned(authentication, readingId, FortuneType.WEALTH)));
    }

    @GetMapping("/readings/compatibility/{readingId}")
    public ApiResponse<CompatibilityReadingResponse> getCompatibility(
            Authentication authentication, @PathVariable UUID readingId) {
        return ApiResponse.of(compatibility(owned(authentication, readingId, FortuneType.COMPATIBILITY)));
    }

    @GetMapping("/readings/sinsal/{readingId}")
    public ApiResponse<SinsalReadingResponse> getSinsal(Authentication authentication, @PathVariable UUID readingId) {
        return ApiResponse.of(sinsal(owned(authentication, readingId, FortuneType.SINSAL)));
    }

    private ResponseEntity<ApiResponse<CategoryQuoteResponse>> quote(
            Authentication authentication, FortuneType type, UUID personId, ProductOption option,
            String questionKey, UUID counterpartPersonId, RelationType relationType) {
        PurchaseQuote quote = readings.issueQuote(userId(authentication), type, option, personId,
                counterpartPersonId, relationType, questionKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(new CategoryQuoteResponse(
                quote.id(), quote.productSnapshot().code(), option.name(), questionKey,
                new Charged(quote.productSnapshot().price().currency().name(), quote.productSnapshot().price().amount()),
                quote.expiresAt())));
    }

    private PurchaseResult purchase(Authentication authentication, String key, FortuneType type, UUID quoteId,
                                    UUID personId, ProductOption option, String questionKey,
                                    UUID counterpartPersonId, RelationType relationType) {
        return readings.purchase(userId(authentication), quoteId, type, option, personId,
                counterpartPersonId, relationType, questionKey, new IdempotencyKey(key));
    }

    private <T> ResponseEntity<ApiResponse<CategoryPurchaseResponse<T>>> purchaseResponse(
            PurchaseResult result, Function<OwnedReading, T> mapper) {
        CategoryPurchaseResponse<T> response = new CategoryPurchaseResponse<>(result.purchaseId(), result.readingId(),
                result.status().name(), result.reading() == null ? null : result.reading().calculationVersion(),
                result.reading() == null ? null : result.reading().generationVersion(),
                result.reading() == null ? null : result.reading().contentVersion(),
                result.charged() == null ? null : new Charged(
                        result.charged().currency().name(), result.charged().amount()),
                result.balance(), result.reused(), result.reading() == null ? null : mapper.apply(result.reading()));
        HttpStatus status = result.status() == com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status.FULFILLED
                ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.of(response));
    }

    private ApiResponse<CategoryListResponse> list(Authentication authentication, FortuneType type, UUID personId,
                                                    String cursor, Integer size) {
        ReadingQueryService.Page page = queries.list(userId(authentication), type, personId, cursor, size);
        List<CategoryReadingSummary> items = page.items().stream().map(reading -> new CategoryReadingSummary(
                reading.id(), reading.productOption().name(), reading.subjectDisplayName(),
                reading.counterpartDisplayName(), reading.questionKey(),
                section(reading, SectionKey.SUMMARY), reading.generationMode().name(), reading.createdAt())).toList();
        return ApiResponse.of(new CategoryListResponse(items, page.nextCursor(), page.hasNext()));
    }

    private OwnedReading owned(Authentication authentication, UUID readingId, FortuneType expectedType) {
        OwnedReading reading = queries.get(userId(authentication), readingId);
        if (reading.fortuneType() != expectedType) throw new ApiException(ErrorCode.READING_NOT_FOUND);
        return reading;
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private OverallReadingResponse overall(OwnedReading reading) {
        Map<SectionKey, SectionContent> values = sections(reading);
        return new OverallReadingResponse(meta(reading), values.get(SectionKey.SUMMARY),
                values.get(SectionKey.CURRENT_FLOW), values.get(SectionKey.RELATIONSHIPS),
                values.get(SectionKey.STUDY_AND_WORK), values.get(SectionKey.WEALTH_FLOW),
                values.get(SectionKey.CONDITION), values.get(SectionKey.LUCKY_POINT),
                values.get(SectionKey.MISSING_ELEMENT));
    }

    private LoveReadingResponse love(OwnedReading reading) {
        Map<SectionKey, SectionContent> values = sections(reading);
        return new LoveReadingResponse(meta(reading), values.get(SectionKey.SUMMARY),
                values.get(SectionKey.CURRENT_FLOW), values.get(SectionKey.GOOD_PERIOD),
                values.get(SectionKey.CAUTION), values.get(SectionKey.ACTION_TIP),
                values.get(SectionKey.MISSING_ELEMENT));
    }

    private WealthReadingResponse wealth(OwnedReading reading) {
        Map<SectionKey, SectionContent> values = sections(reading);
        return new WealthReadingResponse(meta(reading), values.get(SectionKey.SUMMARY),
                values.get(SectionKey.WEALTH_FLOW), values.get(SectionKey.INCOME),
                values.get(SectionKey.SPENDING_CAUTION), values.get(SectionKey.GOOD_PERIOD),
                values.get(SectionKey.ACTION_TIP), values.get(SectionKey.MISSING_ELEMENT));
    }

    private CompatibilityReadingResponse compatibility(OwnedReading reading) {
        Map<SectionKey, SectionContent> values = sections(reading);
        return new CompatibilityReadingResponse(meta(reading), new Counterpart(
                reading.counterpartPersonId(), reading.counterpartDisplayName(), reading.relationType()),
                values.get(SectionKey.SUMMARY), values.get(SectionKey.MATCH_STRENGTH),
                values.get(SectionKey.MATCH_CONFLICT), values.get(SectionKey.COMMUNICATION),
                values.get(SectionKey.RELATIONSHIP_TIP), values.get(SectionKey.MISSING_ELEMENT));
    }

    private SinsalReadingResponse sinsal(OwnedReading reading) {
        Map<SectionKey, SectionContent> values = sections(reading);
        return new SinsalReadingResponse(meta(reading), values.get(SectionKey.SUMMARY),
                values.get(SectionKey.SPECIAL_STARS), values.get(SectionKey.BALANCING_SPECIAL_STARS),
                values.get(SectionKey.MISSING_ELEMENT));
    }

    private ReadingMeta meta(OwnedReading reading) {
        return new ReadingMeta(reading.id(), reading.productOption().name(), reading.subjectDisplayName(),
                reading.questionKey(), reading.calculationVersion(), reading.generationVersion(),
                reading.contentVersion(), reading.generationMode().name(), reading.createdAt(),
                List.of("FOR_ENTERTAINMENT"), reading.talismanId() == null ? null
                        : new Talisman(reading.talismanId(), reading.talismanStatus()));
    }

    private Map<SectionKey, SectionContent> sections(OwnedReading reading) {
        Map<SectionKey, SectionContent> values = new EnumMap<>(SectionKey.class);
        reading.sections().sections().forEach(section -> values.put(section.key(), view(section)));
        return values;
    }

    private SectionContent section(OwnedReading reading, SectionKey key) {
        return reading.sections().sections().stream().filter(item -> item.key() == key)
                .findFirst().map(this::view).orElse(null);
    }

    private SectionContent view(GeneratedSection section) {
        return new SectionContent(section.content(), section.sourceFactKeys());
    }

    public record StandardQuoteRequest(@NotNull UUID personId, @NotNull ProductOption productOption,
                                       @NotBlank String questionKey) {}

    public record CompatibilityQuoteRequest(@NotNull UUID personId, @NotNull UUID counterpartPersonId,
                                             @NotNull RelationType relationType,
                                             @NotNull ProductOption productOption, @NotBlank String questionKey) {}

    public record StandardPurchaseRequest(@NotNull UUID quoteId, @NotNull UUID personId,
                                          @NotNull ProductOption productOption, @NotBlank String questionKey) {}

    public record CompatibilityPurchaseRequest(@NotNull UUID quoteId, @NotNull UUID personId,
                                                @NotNull UUID counterpartPersonId,
                                                @NotNull RelationType relationType,
                                                @NotNull ProductOption productOption,
                                                @NotBlank String questionKey) {}

    public record CategoryQuoteResponse(UUID quoteId, String productCode, String productOption,
                                        String questionKey, Charged charged, Instant expiresAt) {}

    public record CategoryPurchaseResponse<T>(UUID purchaseId, UUID readingId, String status,
                                               String calculationVersion, String generationVersion,
                                               String contentVersion, Charged charged, Integer balance,
                                               boolean reused, T reading) {}

    public record CategoryListResponse(List<CategoryReadingSummary> items, String nextCursor, boolean hasNext) {}

    public record CategoryReadingSummary(UUID id, String productOption, String subjectDisplayName,
                                         String counterpartDisplayName, String questionKey,
                                         SectionContent summary, String generationMode, Instant createdAt) {}

    public record ReadingMeta(UUID id, String productOption, String subjectDisplayName, String questionKey,
                              String calculationVersion, String generationVersion, String contentVersion,
                              String generationMode, Instant createdAt, List<String> disclaimers,
                              Talisman talisman) {}

    public record SectionContent(String content, List<String> sourceFactKeys) {
        public SectionContent {
            sourceFactKeys = List.copyOf(sourceFactKeys);
        }
    }

    public record OverallReadingResponse(ReadingMeta meta, SectionContent summary, SectionContent currentFlow,
                                         SectionContent relationships, SectionContent studyAndWork,
                                         SectionContent wealthFlow, SectionContent condition,
                                         SectionContent luckyPoint, SectionContent missingElement) {}

    public record LoveReadingResponse(ReadingMeta meta, SectionContent summary, SectionContent currentFlow,
                                      SectionContent goodPeriod, SectionContent caution,
                                      SectionContent actionTip, SectionContent missingElement) {}

    public record WealthReadingResponse(ReadingMeta meta, SectionContent summary, SectionContent wealthFlow,
                                        SectionContent income, SectionContent spendingCaution,
                                        SectionContent goodPeriod, SectionContent actionTip,
                                        SectionContent missingElement) {}

    public record CompatibilityReadingResponse(ReadingMeta meta, Counterpart counterpart,
                                                SectionContent summary, SectionContent matchStrength,
                                                SectionContent matchConflict, SectionContent communication,
                                                SectionContent relationshipTip, SectionContent missingElement) {}

    public record SinsalReadingResponse(ReadingMeta meta, SectionContent summary, SectionContent specialStars,
                                        SectionContent balancingGuide, SectionContent missingElement) {}

    public record Counterpart(UUID personId, String displayName, String relationType) {}
    public record Charged(String currency, int amount) {}
    public record Talisman(UUID id, String status) {}
}
