package com.sajuppugi.fortune.talisman.port;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.FiveElements;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for one addon claim per completed reading. */
public interface TalismanAddonPurchaseRepository {
    Optional<Claim> findByReadingId(UUID readingId);
    Claim insert(Claim claim);
    void markDebited(UUID purchaseId, UUID walletTransactionId, Instant updatedAt);
    void markDirectPaid(UUID purchaseId, Instant updatedAt);
    void markPendingTalisman(UUID purchaseId, UUID talismanId, Instant updatedAt);
    Optional<StoredReading> findOwnedEligibleReading(UUID ownerUserId, UUID readingId);

    record Claim(UUID id, UUID readingId, UUID ownerUserId, String readingOnlyProductCode,
                 String bundledProductCode, int quotedAmount, UUID quoteId, UUID walletTransactionId,
                 String state, UUID talismanId, Instant createdAt, Instant updatedAt) {}

    record StoredReading(UUID readingId, UUID ownerUserId, FortuneType fortuneType,
                         String contentVersion, FiveElements fiveElements) {}
}
