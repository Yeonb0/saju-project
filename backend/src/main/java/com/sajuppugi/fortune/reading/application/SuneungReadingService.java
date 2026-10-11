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
import com.sajuppugi.fortune.generation.domain.GenerationModels.*;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseOrchestrator.Execution;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseOrchestrator.ExecutionResult;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateTalisman;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SuneungReadingService {
    private static final List<SectionKey> SECTIONS = List.of(
            SectionKey.SUMMARY, SectionKey.EXAM_DAY, SectionKey.EXAM_PERIODS, SectionKey.FOCUS,
            SectionKey.MEAL, SectionKey.PREPARATION, SectionKey.ANXIETY_MANAGEMENT, SectionKey.MISSING_ELEMENT);

    private final CatalogUseCase catalog;
    private final ObjectProvider<ReadingSubjectPort> subjectProvider;
    private final SajuCalculationUseCase calculation;
    private final SuneungPurchaseClaimService claims;
    private final ReadingRepository readings;
    private final BundledReadingFulfillmentService bundledFulfillment;
    private final ReadingPurchaseOrchestrator orchestrator;
    private final SuneungQuoteContext quoteContext;
    private final Clock clock;

    public SuneungReadingService(CatalogUseCase catalog, ObjectProvider<ReadingSubjectPort> subjectProvider,
                                 SajuCalculationUseCase calculation,
                                 SuneungPurchaseClaimService claims, ReadingRepository readings,
                                 BundledReadingFulfillmentService bundledFulfillment,
                                 ReadingPurchaseOrchestrator orchestrator, SuneungQuoteContext quoteContext,
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

    public PurchaseResult purchase(UUID userId, UUID quoteId, UUID personId, IdempotencyKey key) {
        ReadingPurchase existing = readings.findPurchaseByQuote(userId, quoteId).orElse(null);
        if (existing != null) {
            PurchaseQuote snapshot = validateQuoteSnapshot(catalog.getQuoteSnapshot(userId, quoteId), personId);
            if (existing.status() == Status.FULFILLED) {
                return completed(readings.findOwnedReading(userId, existing.readingId())
                        .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND)), null, true,
                        snapshot.productSnapshot().price());
            }
            return new PurchaseResult(existing.id(), existing.readingId(), existing.status(), null, null, true,
                    snapshot.productSnapshot().price());
        }
        ReadingSubjectPort subjects = required(subjectProvider.getIfAvailable());
        ReadingSubjectPort.OwnedSubject subject = Optional.ofNullable(subjects.getOwnedSubject(userId, personId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        PurchaseQuote quote = validateQuote(userId, quoteId, personId);
        CalculationFacts facts;
        try {
            facts = calculation.calculate(subject.birthInput(), CalculationPolicy.CURRENT);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNSUPPORTED_CALENDAR_DATE);
        }

        ReadingPurchase purchase = claims.claim(userId, quoteId, quote.productSnapshot().id(), personId);
        if (purchase.status() == Status.FULFILLED) {
            return completed(readings.findOwnedReading(userId, purchase.readingId())
                    .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND)), null, true,
                    quote.productSnapshot().price());
        }
        if (purchase.status() != Status.CREATED) {
            return new PurchaseResult(purchase.id(), purchase.readingId(), purchase.status(), null, null, true,
                    quote.productSnapshot().price());
        }

        GenerationCommand command = new GenerationCommand(userId, FortuneType.SUNEUNG,
                SuneungEventPolicy.EXAM_DATE, "EXAM_FOCUS", facts, SECTIONS,
                subject.birthInput().birthTimeUnknown() ? List.of("pillars.hour", "tenGods.hour", "twelveStages.hour") : List.of(),
                SuneungEventPolicy.GENERATION_VERSION, SuneungEventPolicy.CONTENT_VERSION);
        GenerationResumeContext resumeContext = new GenerationResumeContext(command,
                ProductOption.READING_WITH_TALISMAN, personId, subject.displayName(), null, null, null,
                "EXAM_FOCUS", true);
        ExecutionResult result = orchestrator.execute(new Execution(userId, quoteId, key, purchase,
                quote.productSnapshot().price(), command, resumeContext, (generated, lease) -> {
            UUID readingId = UUID.randomUUID();
            OwnedReading reading = new OwnedReading(readingId, generated.resultId(), userId, purchase.id(),
                    FortuneType.SUNEUNG, ProductOption.READING_WITH_TALISMAN, personId, subject.displayName(),
                    null, null, null, "EXAM_FOCUS", SuneungEventPolicy.EXAM_DATE,
                    generated.response(), generated.calculationVersion(),
                    generated.generationVersion(), generated.contentVersion(), generated.generationMode(), null,
                    null, clock.instant());
            return bundledFulfillment.fulfill(purchase, reading, new CreateTalisman(
                    userId, readingId, FortuneType.SUNEUNG, facts, SuneungEventPolicy.CONTENT_VERSION), lease);
        }));
        if (result.current() != null) {
            return new PurchaseResult(result.current().id(), result.current().readingId(), result.current().status(),
                    null, null, true, quote.productSnapshot().price());
        }
        return completed(result.reading(), result.balance(), result.reused(), quote.productSnapshot().price());
    }

    public OwnedReading get(UUID userId, UUID readingId) {
        return readings.findOwnedReading(userId, readingId)
                .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND));
    }

    public PurchaseQuote issueQuote(UUID userId, UUID personId) {
        ReadingSubjectPort subjects = required(subjectProvider.getIfAvailable());
        if (subjects.getOwnedSubject(userId, personId) == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ensureSaleOpen();
        return catalog.issueQuote(new CatalogUseCase.IssueQuote(userId, SuneungEventPolicy.PRODUCT_CODE,
                quoteContext.hash(personId)));
    }

    private PurchaseQuote validateQuote(UUID userId, UUID quoteId, UUID personId) {
        ensureSaleOpen();
        return validateQuoteSnapshot(catalog.getQuote(userId, quoteId), personId);
    }

    private PurchaseQuote validateQuoteSnapshot(PurchaseQuote quote, UUID personId) {
        Product product = quote.productSnapshot();
        if (!quote.contextHash().equals(quoteContext.hash(personId))) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if (!product.code().equals(SuneungEventPolicy.PRODUCT_CODE)
                || product.category() != Product.Category.READING
                || product.price().currency() != Price.Currency.TURTLE_SHELL) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return quote;
    }

    private void ensureSaleOpen() {
        if (ZonedDateTime.now(clock.withZone(SuneungEventPolicy.ZONE)).toLocalDateTime()
                .isAfter(SuneungEventPolicy.SALE_END)) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
    }

    private <T> T required(T dependency) {
        if (dependency == null) throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        return dependency;
    }

    private PurchaseResult completed(OwnedReading reading, Integer balance, boolean reused, Price charged) {
        return new PurchaseResult(reading.purchaseId(), reading.id(), Status.FULFILLED, reading, balance, reused, charged);
    }

    public record PurchaseResult(UUID purchaseId, UUID readingId, Status status,
                                 OwnedReading reading, Integer balance, boolean reused, Price charged) {}
}
