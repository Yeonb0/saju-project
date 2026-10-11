package com.sajuppugi.fortune.reading.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRecoveryRepository {
    List<UUID> findReadyFailed(Instant now, int maxAttempts, int limit);
    Optional<FailedPurchase> lockReadyFailed(UUID purchaseId, Instant now, int maxAttempts);
    void markRefunded(UUID purchaseId);
    void recordFailure(UUID purchaseId, Instant nextAttemptAt, String errorCode);

    record FailedPurchase(UUID id, UUID userId, UUID quoteId, UUID transactionId, int attempts) {}
}
