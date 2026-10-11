package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.generation.application.ReadingGenerationService;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService.GenerationFailedException;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService.GenerationInProgressException;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateTalisman;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GenerationRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(GenerationRecoveryService.class);
    private final ReadingRepository readings;
    private final ReadingGenerationService generation;
    private final BundledReadingFulfillmentService bundledFulfillment;
    private final GenerationLeaseProperties leaseProperties;
    private final Clock clock;

    public GenerationRecoveryService(ReadingRepository readings, ReadingGenerationService generation,
                                     BundledReadingFulfillmentService bundledFulfillment,
                                     GenerationLeaseProperties leaseProperties, Clock clock) {
        this.readings = readings;
        this.generation = generation;
        this.bundledFulfillment = bundledFulfillment;
        this.leaseProperties = leaseProperties;
        this.clock = clock;
    }

    public Outcome recover(UUID purchaseId) {
        var candidate = readings.findGenerationRecoveryCandidate(purchaseId).orElse(null);
        if (candidate == null) return Outcome.SKIPPED;
        var now = clock.instant();
        var lease = readings.tryClaimGeneration(purchaseId, UUID.randomUUID(), now,
                now.plus(leaseProperties.leaseDuration())).orElse(null);
        if (lease == null) return Outcome.CLAIMED_BY_OTHER;
        try {
            GeneratedReading generated = generation.generate(candidate.context().command());
            OwnedReading reading = reading(candidate.purchase(), candidate.context(), generated);
            if (candidate.context().includesTalisman()) {
                bundledFulfillment.fulfill(candidate.purchase(), reading, new CreateTalisman(
                        candidate.purchase().buyerUserId(), reading.id(), generated.fortuneType(),
                        candidate.context().command().calculationFacts(), generated.contentVersion()), lease);
            } else {
                readings.fulfill(candidate.purchase(), reading, lease);
            }
            return Outcome.FULFILLED;
        } catch (GenerationInProgressException inProgress) {
            readings.releaseGenerationLease(purchaseId, lease);
            return Outcome.RETRY_SCHEDULED;
        } catch (GenerationFailedException failure) {
            try {
                readings.markFailed(purchaseId, "READING_GENERATION_FAILED", lease);
                log.warn("Reading generation recovery failed: purchaseId={}, exception={}", purchaseId,
                        failure.getClass().getSimpleName());
                return Outcome.FAILED;
            } catch (IllegalStateException fencedOff) {
                return Outcome.FENCED_OFF;
            }
        } catch (RuntimeException transientFailure) {
            readings.releaseGenerationLease(purchaseId, lease);
            throw transientFailure;
        }
    }

    private OwnedReading reading(com.sajuppugi.fortune.reading.domain.ReadingPurchase purchase,
                                 GenerationResumeContext context, GeneratedReading generated) {
        return new OwnedReading(UUID.randomUUID(), generated.resultId(), purchase.buyerUserId(), purchase.id(),
                generated.fortuneType(), context.productOption(), context.subjectPersonId(), context.subjectDisplayName(),
                context.counterpartPersonId(), context.counterpartDisplayName(), context.relationType(),
                context.questionKey(), generated.fortuneType().name().equals("SUNEUNG")
                        ? context.command().referenceDate() : null,
                generated.response(), generated.calculationVersion(), generated.generationVersion(),
                generated.contentVersion(), generated.generationMode(), null, null, clock.instant());
    }

    public enum Outcome { FULFILLED, FAILED, RETRY_SCHEDULED, FENCED_OFF, CLAIMED_BY_OTHER, SKIPPED }
}
