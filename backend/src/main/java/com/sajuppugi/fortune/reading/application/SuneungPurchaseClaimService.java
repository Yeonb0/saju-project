package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.catalog.port.CatalogRepository;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuneungPurchaseClaimService {
    private final ReadingRepository readings;
    private final CatalogRepository catalog;
    private final Clock clock;

    public SuneungPurchaseClaimService(ReadingRepository readings, CatalogRepository catalog, Clock clock) {
        this.readings = readings;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public ReadingPurchase claim(UUID userId, UUID quoteId, UUID productId, UUID personId) {
        var existing = readings.findPurchaseByQuote(userId, quoteId);
        if (existing.isPresent()) return existing.get();

        UUID purchaseId = UUID.randomUUID();
        if (!catalog.bindQuoteToPurchase(quoteId, purchaseId)) {
            return readings.findPurchaseByQuote(userId, quoteId)
                    .orElseThrow(() -> new IllegalStateException("Quote was claimed without a reading purchase"));
        }
        ReadingPurchase purchase = new ReadingPurchase(purchaseId, userId, productId, quoteId, personId,
                null, Status.CREATED, null, clock.instant(), null);
        readings.createPurchase(purchase);
        return purchase;
    }
}
