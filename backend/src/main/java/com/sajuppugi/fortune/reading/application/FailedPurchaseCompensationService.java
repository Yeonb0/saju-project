package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.reading.port.ReadingRecoveryRepository;
import com.sajuppugi.wallet.application.WalletPurchaseRecoveryPort;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailedPurchaseCompensationService {
    private static final Logger log = LoggerFactory.getLogger(FailedPurchaseCompensationService.class);
    private final ReadingRecoveryRepository readings;
    private final ObjectProvider<WalletPurchaseRecoveryPort> wallets;
    private final WalletRecoveryProperties properties;
    private final Clock clock;

    public FailedPurchaseCompensationService(ReadingRecoveryRepository readings,
            ObjectProvider<WalletPurchaseRecoveryPort> wallets, WalletRecoveryProperties properties, Clock clock) {
        this.readings = readings;
        this.wallets = wallets;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(timeout = 20)
    public Outcome recover(UUID purchaseId) {
        WalletPurchaseRecoveryPort wallet = wallets.getIfAvailable();
        if (wallet == null) return Outcome.DISABLED;
        var purchase = readings.lockReadyFailed(purchaseId, clock.instant(), properties.maxAttempts()).orElse(null);
        if (purchase == null) return Outcome.SKIPPED;
        try {
            // A wallet commit may survive a later purchase-state failure. The next run reuses that reversal.
            var result = wallet.compensatePurchase(purchase.userId(), purchase.quoteId(), purchase.transactionId(),
                    "READING_RECOVERY_FAILED", new IdempotencyKey("reading-recovery:" + purchase.id()));
            if (result == null || result.transactionId() == null
                    || !purchase.transactionId().equals(result.originalTransactionId())) {
                throw new IllegalStateException("Wallet compensation did not return the original purchase receipt");
            }
            readings.markRefunded(purchase.id());
            return Outcome.REFUNDED;
        } catch (RuntimeException failure) {
            int attempts = purchase.attempts() + 1;
            String code = failure instanceof ApiException exception
                    ? exception.errorCode().name() : "WALLET_COMPENSATION_FAILED";
            boolean exhausted = attempts >= properties.maxAttempts();
            long delay = Math.min(600, properties.retryDelay().toSeconds() * (1L << Math.min(attempts - 1, 10)));
            readings.recordFailure(purchase.id(), exhausted ? null : clock.instant().plus(Duration.ofSeconds(delay)), code);
            log.warn("Purchase compensation pending: purchaseId={}, attempts={}, exhausted={}, code={}",
                    purchase.id(), attempts, exhausted, code);
            return exhausted ? Outcome.EXHAUSTED : Outcome.RETRY_SCHEDULED;
        }
    }

    public enum Outcome { REFUNDED, RETRY_SCHEDULED, EXHAUSTED, SKIPPED, DISABLED }
}
