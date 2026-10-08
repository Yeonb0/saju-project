package com.sajuppugi.fortune.reading.port;

import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository {
    void createPurchase(ReadingPurchase purchase);
    Optional<ReadingPurchase> findPurchaseByQuote(UUID buyerUserId, UUID quoteId);
    void markDebited(UUID purchaseId, UUID walletTransactionId);
    void markGenerating(UUID purchaseId);
    void fulfill(ReadingPurchase purchase, OwnedReading reading);
    void markFailed(UUID purchaseId);
    void markRefunded(UUID purchaseId);
    Optional<OwnedReading> findOwnedReading(UUID ownerUserId, UUID readingId);
}
