package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService.GenerationFailedException;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService.GenerationInProgressException;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.reading.port.ReadingRepository.GenerationLease;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ReadingPurchaseOrchestrator {
    private static final Duration DEBIT_CLAIM_LEASE = Duration.ofMinutes(1);

    private final ObjectProvider<WalletPurchasePort> walletProvider;
    private final ReadingGenerationService generation;
    private final ReadingRepository readings;
    private final GenerationLeaseProperties generationLease;
    private final Clock clock;

    public ReadingPurchaseOrchestrator(ObjectProvider<WalletPurchasePort> walletProvider,
                                       ReadingGenerationService generation, ReadingRepository readings,
                                       GenerationLeaseProperties generationLease, Clock clock) {
        this.walletProvider = walletProvider;
        this.generation = generation;
        this.readings = readings;
        this.generationLease = generationLease;
        this.clock = clock;
    }

    public ExecutionResult execute(Execution execution) {
        ReadingPurchase purchase = execution.purchase();
        readings.saveGenerationContext(purchase.id(), execution.resumeContext());

        WalletPurchasePort wallet = required(walletProvider.getIfAvailable());
        UUID debitClaim = UUID.randomUUID();
        if (!readings.tryClaimDebit(purchase.id(), debitClaim, clock.instant(),
                clock.instant().minus(DEBIT_CLAIM_LEASE))) {
            return current(execution);
        }

        WalletPurchasePort.DebitResult debit;
        try {
            debit = wallet.debit(execution.userId(), execution.quoteId(), debitKey(purchase.id(), execution.requestKey()));
            if (debit == null) throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
            readings.markDebited(purchase.id(), debitClaim, debit.transactionId());
        } catch (RuntimeException failure) {
            readings.releaseDebitClaim(purchase.id(), debitClaim);
            throw failure;
        }
        if (debit.debitedAmount() != execution.charged().amount()) {
            readings.markFailed(purchase.id(), "PURCHASE_DEBIT_MISMATCH");
            throw new ApiException(ErrorCode.PURCHASE_DEBIT_MISMATCH);
        }

        GenerationLease lease = readings.tryClaimGeneration(purchase.id(), UUID.randomUUID(), clock.instant(),
                clock.instant().plus(generationLease.leaseDuration())).orElse(null);
        if (lease == null) return current(execution);

        try {
            GeneratedReading generated = generation.generate(execution.command());
            OwnedReading reading = execution.fulfillment().fulfill(generated, lease);
            return new ExecutionResult(reading, debit.balance().balance(), false);
        } catch (GenerationInProgressException inProgress) {
            readings.releaseGenerationLease(purchase.id(), lease);
            throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        } catch (GenerationFailedException failure) {
            try {
                readings.markFailed(purchase.id(), "READING_GENERATION_FAILED", lease);
            } catch (IllegalStateException fencedOff) {
                throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
            }
            throw new ApiException(ErrorCode.READING_GENERATION_FAILED);
        } catch (RuntimeException transientFailure) {
            readings.releaseGenerationLease(purchase.id(), lease);
            throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        }
    }

    private ExecutionResult current(Execution execution) {
        ReadingPurchase current = readings.findPurchaseByQuote(execution.userId(), execution.quoteId()).orElseThrow();
        return new ExecutionResult(null, null, true, current);
    }

    private IdempotencyKey debitKey(UUID purchaseId, IdempotencyKey requestKey) {
        if (requestKey == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        return new IdempotencyKey("reading-purchase:" + purchaseId);
    }

    private <T> T required(T dependency) {
        if (dependency == null) throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        return dependency;
    }

    public record Execution(UUID userId, UUID quoteId, IdempotencyKey requestKey, ReadingPurchase purchase,
                            Price charged, GenerationCommand command, GenerationResumeContext resumeContext,
                            Fulfillment fulfillment) {}

    public record ExecutionResult(OwnedReading reading, Integer balance, boolean reused, ReadingPurchase current) {
        public ExecutionResult(OwnedReading reading, Integer balance, boolean reused) {
            this(reading, balance, reused, null);
        }
    }

    @FunctionalInterface
    public interface Fulfillment {
        OwnedReading fulfill(GeneratedReading generated, GenerationLease lease);
    }
}
