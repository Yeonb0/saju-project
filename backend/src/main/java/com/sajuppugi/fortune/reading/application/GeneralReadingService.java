package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.calculation.application.SajuCalculationUseCase;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseOrchestrator.Execution;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseOrchestrator.ExecutionResult;
import com.sajuppugi.fortune.reading.application.SuneungReadingService.PurchaseResult;
import com.sajuppugi.fortune.reading.domain.GeneralReadingPolicy;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateTalisman;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class GeneralReadingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final CatalogUseCase catalog;
    private final ObjectProvider<ReadingSubjectPort> subjectProvider;
    private final SajuCalculationUseCase calculation;
    private final SuneungPurchaseClaimService claims;
    private final ReadingRepository readings;
    private final BundledReadingFulfillmentService bundledFulfillment;
    private final ReadingPurchaseOrchestrator orchestrator;
    private final ReadingQuoteContext quoteContext;
    private final Clock clock;

    public GeneralReadingService(CatalogUseCase catalog, ObjectProvider<ReadingSubjectPort> subjectProvider,
                                 SajuCalculationUseCase calculation,
                                 SuneungPurchaseClaimService claims, ReadingRepository readings,
                                 BundledReadingFulfillmentService bundledFulfillment,
                                 ReadingPurchaseOrchestrator orchestrator, ReadingQuoteContext quoteContext,
                                 Clock clock) {
        this.catalog = catalog;
        this.subjectProvider = subjectProvider;
        this.calculation = calculation;
        this.claims = claims;
        this.readings = readings;
        this.bundledFulfillment = bundledFulfillment;
        this.orchestrator = orchestrator;
        this.quoteContext = quoteContext;
        this.clock = clock;
    }

    public PurchaseQuote issueQuote(UUID userId, FortuneType fortuneType, ProductOption option, UUID personId,
                                    UUID counterpartPersonId, RelationType relationType, String questionKey) {
        validateInput(fortuneType, option, personId, counterpartPersonId, relationType, questionKey);
        ownedSubjects(userId, personId, counterpartPersonId);
        String productCode = GeneralReadingPolicy.productCode(fortuneType, option);
        return catalog.issueQuote(new CatalogUseCase.IssueQuote(userId, productCode,
                quoteContext.hash(fortuneType, option, personId, counterpartPersonId, relationType, questionKey)));
    }

    public PurchaseResult purchase(UUID userId, UUID quoteId, FortuneType fortuneType, ProductOption option,
                                   UUID personId, UUID counterpartPersonId, RelationType relationType,
                                   String questionKey, IdempotencyKey key) {
        return purchase(userId, quoteId, fortuneType, option, personId, counterpartPersonId, relationType,
                questionKey, null, key);
    }

    public PurchaseResult purchase(UUID userId, UUID quoteId, FortuneType fortuneType, ProductOption option,
                                   UUID personId, UUID counterpartPersonId, RelationType relationType,
                                   String questionKey, String personalizedConcern, IdempotencyKey key) {
        return purchaseInternal(userId, null, quoteId, fortuneType, option, personId, counterpartPersonId,
                relationType, questionKey, personalizedConcern, key);
    }

    public PurchaseResult purchaseDirect(UUID userId, UUID batchId, UUID quoteId, FortuneType fortuneType,
                                         ProductOption option, UUID personId, UUID counterpartPersonId,
                                         RelationType relationType, String questionKey, String personalizedConcern,
                                         IdempotencyKey key) {
        if (batchId == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        return purchaseInternal(userId, batchId, quoteId, fortuneType, option, personId, counterpartPersonId,
                relationType, questionKey, personalizedConcern, key);
    }

    private PurchaseResult purchaseInternal(UUID userId, UUID batchId, UUID quoteId, FortuneType fortuneType, ProductOption option,
                                   UUID personId, UUID counterpartPersonId, RelationType relationType,
                                   String questionKey, String personalizedConcern, IdempotencyKey key) {
        validateInput(fortuneType, option, personId, counterpartPersonId, relationType, questionKey);
        String concern = PersonalizedConcern.normalize(personalizedConcern);
        ReadingPurchase existing = readings.findPurchaseByQuote(userId, quoteId).orElse(null);
        if (existing != null) {
            PurchaseQuote snapshot = validateQuoteSnapshot(catalog.getQuoteSnapshot(userId, quoteId), fortuneType,
                    option, personId, counterpartPersonId, relationType, questionKey,
                    batchId == null ? Price.Currency.TURTLE_SHELL : Price.Currency.KRW);
            return existing(existing, userId, snapshot.productSnapshot().price());
        }
        Subjects subjects = ownedSubjects(userId, personId, counterpartPersonId);
        PurchaseQuote quote = validateQuoteSnapshot(catalog.getQuote(userId, quoteId), fortuneType, option, personId,
                counterpartPersonId, relationType, questionKey, batchId == null ? Price.Currency.TURTLE_SHELL : Price.Currency.KRW);
        CalculationFacts facts = calculate(subjects.primary());
        CalculationFacts counterpartFacts = subjects.counterpart() == null ? null : calculate(subjects.counterpart());

        ReadingPurchase purchase = batchId == null
                ? claims.claim(userId, quoteId, quote.productSnapshot().id(), personId, counterpartPersonId, value(relationType), questionKey)
                : claims.claimDirectPaid(userId, batchId, quoteId, quote.productSnapshot().id(), personId, counterpartPersonId, value(relationType), questionKey);
        if (purchase.status() == Status.FULFILLED) {
            OwnedReading reading = readings.findOwnedReading(userId, purchase.readingId())
                    .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND));
            return completed(reading, null, true, quote.productSnapshot().price());
        }
        if (purchase.status() != Status.CREATED) {
            return new PurchaseResult(purchase.id(), purchase.readingId(), purchase.status(), null, null, true,
                    quote.productSnapshot().price());
        }

        GeneralReadingPolicy.Definition policy = GeneralReadingPolicy.require(fortuneType);
        List<String> missingFields = missingFields(subjects.primary(), subjects.counterpart());
        GenerationCommand command = new GenerationCommand(userId, fortuneType,
                LocalDate.now(clock.withZone(ZONE)), questionKey, facts, counterpartFacts,
                value(relationType), policy.allowedSections(), missingFields,
                GeneralReadingPolicy.GENERATION_VERSION, policy.contentVersion(), concern);
        GenerationResumeContext resumeContext = new GenerationResumeContext(command, option, personId,
                subjects.primary().displayName(), counterpartPersonId,
                subjects.counterpart() == null ? null : subjects.counterpart().displayName(), value(relationType),
                questionKey, option == ProductOption.READING_WITH_TALISMAN);
        ExecutionResult result = orchestrator.execute(new Execution(userId, quoteId, key, purchase,
                quote.productSnapshot().price(), command, resumeContext, (generated, lease) -> {
            UUID readingId = UUID.randomUUID();
            OwnedReading reading = new OwnedReading(readingId, generated.resultId(), userId, purchase.id(),
                    fortuneType, option, personId, subjects.primary().displayName(), counterpartPersonId,
                    subjects.counterpart() == null ? null : subjects.counterpart().displayName(), value(relationType),
                    questionKey, null, generated.response(), generated.calculationVersion(),
                    generated.generationVersion(), generated.contentVersion(), generated.generationMode(),
                    null, null, clock.instant());
            if (option == ProductOption.READING_WITH_TALISMAN) {
                reading = bundledFulfillment.fulfill(purchase, reading, new CreateTalisman(
                        userId, readingId, fortuneType, facts, policy.contentVersion()), lease);
            } else {
                readings.fulfill(purchase, reading, lease);
            }
            return reading;
        }, batchId != null));
        if (result.current() != null) return existing(result.current(), userId, quote.productSnapshot().price());
        return completed(result.reading(), result.balance(), result.reused(), quote.productSnapshot().price());
    }

    private PurchaseQuote validateQuote(UUID userId, UUID quoteId, FortuneType fortuneType, ProductOption option,
                                        UUID personId, UUID counterpartPersonId, RelationType relationType,
                                        String questionKey) {
        return validateQuoteSnapshot(catalog.getQuote(userId, quoteId), fortuneType, option, personId,
                counterpartPersonId, relationType, questionKey);
    }

    private PurchaseQuote validateQuoteSnapshot(PurchaseQuote quote, FortuneType fortuneType, ProductOption option,
                                        UUID personId, UUID counterpartPersonId, RelationType relationType,
                                        String questionKey) {
        return validateQuoteSnapshot(quote, fortuneType, option, personId, counterpartPersonId, relationType, questionKey,
                Price.Currency.TURTLE_SHELL);
    }
    private PurchaseQuote validateQuoteSnapshot(PurchaseQuote quote, FortuneType fortuneType, ProductOption option,
                                        UUID personId, UUID counterpartPersonId, RelationType relationType,
                                        String questionKey, Price.Currency currency) {
        Product product = quote.productSnapshot();
        String expectedCode = GeneralReadingPolicy.productCode(fortuneType, option);
        String expectedHash = quoteContext.hash(fortuneType, option, personId,
                counterpartPersonId, relationType, questionKey);
        if (!expectedHash.equals(quote.contextHash()) || !expectedCode.equals(product.code())
                || product.category() != Product.Category.READING
                || product.price().currency() != currency) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return quote;
    }

    private PurchaseResult existing(ReadingPurchase purchase, UUID userId, Price charged) {
        if (purchase.status() == Status.FULFILLED) {
            OwnedReading reading = readings.findOwnedReading(userId, purchase.readingId())
                    .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND));
            return completed(reading, null, true, charged);
        }
        return new PurchaseResult(purchase.id(), purchase.readingId(), purchase.status(), null, null, true, charged);
    }

    private void validateInput(FortuneType fortuneType, ProductOption option, UUID personId,
                               UUID counterpartPersonId, RelationType relationType, String questionKey) {
        if (fortuneType == null || option == null || personId == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        GeneralReadingPolicy.validateQuestion(fortuneType, questionKey);
        if (fortuneType == FortuneType.COMPATIBILITY) {
            if (counterpartPersonId == null) throw new ApiException(ErrorCode.COMPATIBILITY_PERSON_REQUIRED);
            if (counterpartPersonId.equals(personId)) throw new ApiException(ErrorCode.COMPATIBILITY_SELF_NOT_ALLOWED);
            if (relationType == null) throw new ApiException(ErrorCode.RELATION_TYPE_REQUIRED);
        } else if (counterpartPersonId != null || relationType != null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Subjects ownedSubjects(UUID userId, UUID personId, UUID counterpartPersonId) {
        ReadingSubjectPort subjects = required(subjectProvider.getIfAvailable());
        ReadingSubjectPort.OwnedSubject primary = Optional.ofNullable(subjects.getOwnedSubject(userId, personId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        ReadingSubjectPort.OwnedSubject counterpart = counterpartPersonId == null ? null
                : Optional.ofNullable(subjects.getOwnedSubject(userId, counterpartPersonId))
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return new Subjects(primary, counterpart);
    }

    private CalculationFacts calculate(ReadingSubjectPort.OwnedSubject subject) {
        try {
            return calculation.calculate(subject.birthInput(), CalculationPolicy.CURRENT);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNSUPPORTED_CALENDAR_DATE);
        }
    }

    private List<String> missingFields(ReadingSubjectPort.OwnedSubject primary,
                                       ReadingSubjectPort.OwnedSubject counterpart) {
        List<String> missing = new ArrayList<>();
        if (primary.birthInput().birthTimeUnknown()) {
            missing.addAll(List.of("pillars.hour", "tenGods.hour", "twelveStages.hour"));
        }
        if (counterpart != null && counterpart.birthInput().birthTimeUnknown()) {
            missing.addAll(List.of("counterpart.pillars.hour", "counterpart.tenGods.hour",
                    "counterpart.twelveStages.hour"));
        }
        return List.copyOf(missing);
    }

    private <T> T required(T dependency) {
        if (dependency == null) throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        return dependency;
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }

    private PurchaseResult completed(OwnedReading reading, Integer balance, boolean reused, Price charged) {
        return new PurchaseResult(reading.purchaseId(), reading.id(), Status.FULFILLED, reading, balance, reused, charged);
    }

    private record Subjects(ReadingSubjectPort.OwnedSubject primary, ReadingSubjectPort.OwnedSubject counterpart) {}
}
