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
import com.sajuppugi.fortune.generation.application.ReadingGenerationService;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.reading.application.SuneungReadingService.PurchaseResult;
import com.sajuppugi.fortune.reading.domain.GeneralReadingPolicy;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.fortune.talisman.application.SuneungTalismanPort;
import com.sajuppugi.wallet.application.WalletPurchasePort;
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
    private final ObjectProvider<WalletPurchasePort> walletProvider;
    private final ObjectProvider<ReadingSubjectPort> subjectProvider;
    private final ObjectProvider<SuneungTalismanPort> talismanProvider;
    private final SajuCalculationUseCase calculation;
    private final ReadingGenerationService generation;
    private final SuneungPurchaseClaimService claims;
    private final ReadingRepository readings;
    private final ReadingQuoteContext quoteContext;
    private final Clock clock;

    public GeneralReadingService(CatalogUseCase catalog, ObjectProvider<WalletPurchasePort> walletProvider,
                                 ObjectProvider<ReadingSubjectPort> subjectProvider,
                                 ObjectProvider<SuneungTalismanPort> talismanProvider,
                                 SajuCalculationUseCase calculation, ReadingGenerationService generation,
                                 SuneungPurchaseClaimService claims, ReadingRepository readings,
                                 ReadingQuoteContext quoteContext, Clock clock) {
        this.catalog = catalog;
        this.walletProvider = walletProvider;
        this.subjectProvider = subjectProvider;
        this.talismanProvider = talismanProvider;
        this.calculation = calculation;
        this.generation = generation;
        this.claims = claims;
        this.readings = readings;
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
        validateInput(fortuneType, option, personId, counterpartPersonId, relationType, questionKey);
        Subjects subjects = ownedSubjects(userId, personId, counterpartPersonId);
        PurchaseQuote quote = validateQuote(userId, quoteId, fortuneType, option, personId,
                counterpartPersonId, relationType, questionKey);
        CalculationFacts facts = calculate(subjects.primary());
        CalculationFacts counterpartFacts = subjects.counterpart() == null ? null : calculate(subjects.counterpart());

        ReadingPurchase purchase = claims.claim(userId, quoteId, quote.productSnapshot().id(), personId,
                counterpartPersonId, value(relationType), questionKey);
        if (purchase.status() == Status.FULFILLED) {
            OwnedReading reading = readings.findOwnedReading(userId, purchase.readingId())
                    .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND));
            return completed(reading, null, true, quote.productSnapshot().price());
        }
        if (purchase.status() != Status.CREATED) {
            return new PurchaseResult(purchase.id(), purchase.readingId(), purchase.status(), null, null, true,
                    quote.productSnapshot().price());
        }

        WalletPurchasePort wallet = required(walletProvider.getIfAvailable());
        WalletPurchasePort.DebitResult debit = wallet.debit(userId, quoteId, key);
        if (debit == null) throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        readings.markDebited(purchase.id(), debit.transactionId());
        if (debit.debitedAmount() != quote.productSnapshot().price().amount()) {
            readings.markFailed(purchase.id());
            compensate(wallet, debit.transactionId(), key, purchase.id(), "READING_DEBIT_MISMATCH");
            throw new ApiException(ErrorCode.PURCHASE_DEBIT_MISMATCH);
        }

        readings.markGenerating(purchase.id());
        try {
            GeneralReadingPolicy.Definition policy = GeneralReadingPolicy.require(fortuneType);
            List<String> missingFields = missingFields(subjects.primary(), subjects.counterpart());
            GeneratedReading generated = generation.generate(new GenerationCommand(userId, fortuneType,
                    LocalDate.now(clock.withZone(ZONE)), questionKey, facts, counterpartFacts,
                    value(relationType), policy.allowedSections(), missingFields,
                    GeneralReadingPolicy.GENERATION_VERSION, policy.contentVersion()));
            UUID readingId = UUID.randomUUID();
            SuneungTalismanPort.TalismanFulfillment talisman = option == ProductOption.READING_WITH_TALISMAN
                    ? createTalisman(userId, readingId, facts, policy.contentVersion()) : null;
            OwnedReading reading = new OwnedReading(readingId, generated.resultId(), userId, purchase.id(),
                    fortuneType, option, personId, subjects.primary().displayName(), counterpartPersonId,
                    subjects.counterpart() == null ? null : subjects.counterpart().displayName(), value(relationType),
                    questionKey, null, generated.response(), generated.calculationVersion(),
                    generated.generationVersion(), generated.contentVersion(), generated.generationMode(),
                    talisman == null ? null : talisman.talismanId(), talisman == null ? null : talisman.status().name(),
                    clock.instant());
            readings.fulfill(purchase, reading);
            return completed(reading, debit.balance().balance(), false, quote.productSnapshot().price());
        } catch (RuntimeException failure) {
            readings.markFailed(purchase.id());
            try {
                compensate(wallet, debit.transactionId(), key, purchase.id(), "READING_GENERATION_FAILED");
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw new ApiException(ErrorCode.READING_GENERATION_FAILED);
        }
    }

    private PurchaseQuote validateQuote(UUID userId, UUID quoteId, FortuneType fortuneType, ProductOption option,
                                        UUID personId, UUID counterpartPersonId, RelationType relationType,
                                        String questionKey) {
        PurchaseQuote quote = catalog.getQuote(userId, quoteId);
        Product product = quote.productSnapshot();
        String expectedCode = GeneralReadingPolicy.productCode(fortuneType, option);
        String expectedHash = quoteContext.hash(fortuneType, option, personId,
                counterpartPersonId, relationType, questionKey);
        if (!expectedHash.equals(quote.contextHash()) || !expectedCode.equals(product.code())
                || product.category() != Product.Category.READING
                || product.price().currency() != Price.Currency.TURTLE_SHELL) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return quote;
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

    private SuneungTalismanPort.TalismanFulfillment createTalisman(
            UUID userId, UUID readingId, CalculationFacts facts, String contentVersion) {
        SuneungTalismanPort talismans = required(talismanProvider.getIfAvailable());
        SuneungTalismanPort.TalismanFulfillment talisman = talismans.create(userId, readingId, facts, contentVersion);
        if (talisman == null) throw new IllegalStateException("Talisman fulfillment returned no result");
        return talisman;
    }

    private void compensate(WalletPurchasePort wallet, UUID transactionId, IdempotencyKey key,
                            UUID purchaseId, String reason) {
        wallet.compensate(transactionId, reason, new IdempotencyKey(key.value() + ":compensate"));
        readings.markRefunded(purchaseId);
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
