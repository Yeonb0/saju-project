package com.sajuppugi.fortune.reading.port;

import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository {
    void createPurchase(ReadingPurchase purchase);
    Optional<ReadingPurchase> findPurchaseByQuote(UUID buyerUserId, UUID quoteId);
    boolean tryClaimDebit(UUID purchaseId, UUID claimToken, Instant claimedAt, Instant staleBefore);
    void releaseDebitClaim(UUID purchaseId, UUID claimToken);
    void markDebited(UUID purchaseId, UUID claimToken, UUID walletTransactionId);
    void saveGenerationContext(UUID purchaseId, GenerationResumeContext context);
    default Optional<GenerationResumeContext> findGenerationContext(UUID buyerUserId, UUID quoteId) {
        return Optional.empty();
    }
    List<UUID> findReadyGenerationRecoveries(Instant now, int maxAttempts, int limit);
    List<UUID> findExhaustedGenerationRecoveries(Instant now, int maxAttempts, int limit);
    boolean markGenerationExhausted(UUID purchaseId, Instant now, int maxAttempts);
    Optional<GenerationRecoveryCandidate> findGenerationRecoveryCandidate(UUID purchaseId);
    Optional<GenerationLease> tryClaimGeneration(UUID purchaseId, UUID leaseToken, Instant now, Instant leaseUntil);
    void releaseGenerationLease(UUID purchaseId, GenerationLease lease);
    void fulfill(ReadingPurchase purchase, OwnedReading reading, GenerationLease lease);
    void markDebitMismatch(UUID purchaseId);
    void markFailed(UUID purchaseId, String failureCode, GenerationLease lease);
    void markRefunded(UUID purchaseId);
    Optional<OwnedReading> findOwnedReading(UUID ownerUserId, UUID readingId);
    List<OwnedReading> findOwnedReadings(UUID ownerUserId, FortuneType fortuneType, UUID personId,
                                         Instant beforeCreatedAt, UUID beforeId, int limit);

    record GenerationLease(UUID token, long fence) {}
    record GenerationRecoveryCandidate(ReadingPurchase purchase, GenerationResumeContext context) {}
}
