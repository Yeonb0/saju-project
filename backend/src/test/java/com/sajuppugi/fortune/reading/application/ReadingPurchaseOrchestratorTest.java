package com.sajuppugi.fortune.reading.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Price.Currency;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService.GenerationFailedException;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseOrchestrator.Execution;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.reading.port.ReadingRepository.GenerationLease;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.InvalidDataAccessApiUsageException;

class ReadingPurchaseOrchestratorTest {
    @Test
    void translatedStaleFenceFailureReturnsFulfillmentUnavailable() {
        ReadingRepository readings = mock(ReadingRepository.class);
        ReadingGenerationService generation = mock(ReadingGenerationService.class);
        WalletPurchasePort wallet = mock(WalletPurchasePort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WalletPurchasePort> walletProvider = mock(ObjectProvider.class);
        when(walletProvider.getIfAvailable()).thenReturn(wallet);

        UUID userId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        ReadingPurchase purchase = new ReadingPurchase(purchaseId, userId, UUID.randomUUID(), quoteId,
                UUID.randomUUID(), null, Status.CREATED, null, Instant.parse("2026-10-11T00:00:00Z"), null);
        GenerationCommand command = mock(GenerationCommand.class);
        GenerationResumeContext context = mock(GenerationResumeContext.class);
        GenerationLease lease = new GenerationLease(UUID.randomUUID(), 2);
        when(readings.tryClaimDebit(eq(purchaseId), any(), any(), any())).thenReturn(true);
        when(wallet.debit(eq(userId), eq(quoteId), any())).thenReturn(
                new WalletPurchasePort.DebitResult(transactionId, 15, new WalletBalance(20, 0)));
        when(readings.tryClaimGeneration(eq(purchaseId), any(), any(), any())).thenReturn(Optional.of(lease));
        when(generation.generate(command)).thenThrow(new GenerationFailedException("FAILED", null));
        doThrow(new InvalidDataAccessApiUsageException("stale fence", new IllegalStateException("stale")))
                .when(readings).markFailed(purchaseId, "READING_GENERATION_FAILED", lease);

        ReadingPurchaseOrchestrator orchestrator = new ReadingPurchaseOrchestrator(walletProvider, generation,
                readings, new GenerationLeaseProperties(Duration.ofMinutes(2)),
                Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC));
        Execution execution = new Execution(userId, quoteId, new IdempotencyKey("request"), purchase,
                new Price(Currency.TURTLE_SHELL, 15), command, context,
                (generated, generationLease) -> { throw new AssertionError("fulfillment must not run"); });

        assertThatThrownBy(() -> orchestrator.execute(execution))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.READING_FULFILLMENT_UNAVAILABLE));
    }
}
