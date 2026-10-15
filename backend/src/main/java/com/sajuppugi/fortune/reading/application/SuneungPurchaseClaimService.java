package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.catalog.port.CatalogRepository;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.payment.application.DirectPaymentConsumptionService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuneungPurchaseClaimService {
    private final ReadingRepository readings;
    private final CatalogRepository catalog;
    private final Clock clock;
    private final DirectPaymentConsumptionService payments;

    public SuneungPurchaseClaimService(ReadingRepository readings, CatalogRepository catalog, Clock clock,
                                       DirectPaymentConsumptionService payments) {
        this.readings = readings;
        this.catalog = catalog;
        this.clock = clock;
        this.payments = payments;
    }

    @Transactional
    public ReadingPurchase claim(UUID userId, UUID quoteId, UUID productId, UUID personId) {
        return claim(userId, quoteId, productId, personId, null, null, null);
    }

    @Transactional
    public ReadingPurchase claim(UUID userId, UUID quoteId, UUID productId, UUID personId,
                                 UUID counterpartPersonId, String relationType, String questionKey) {
        var existing = readings.findPurchaseByQuote(userId, quoteId);
        if (existing.isPresent()) return existing.get();

        UUID purchaseId = UUID.randomUUID();
        if (!catalog.bindQuoteToPurchase(quoteId, purchaseId)) {
            return readings.findPurchaseByQuote(userId, quoteId)
                    .orElseThrow(() -> new IllegalStateException("Quote was claimed without a reading purchase"));
        }
        ReadingPurchase purchase = new ReadingPurchase(purchaseId, userId, productId, quoteId, personId,
                counterpartPersonId, relationType, questionKey, null, Status.CREATED, null,
                ReadingPurchase.CompensationStatus.NOT_REQUIRED, null, clock.instant(), null);
        readings.createPurchase(purchase);
        return purchase;
    }

    /** A settled batch is consumed before a reading can enter the generation state. */
    @Transactional
    public ReadingPurchase claimDirectPaid(UUID userId, UUID batchId, UUID quoteId, UUID productId, UUID personId,
                                           UUID counterpartPersonId, String relationType, String questionKey) {
        ReadingPurchase purchase = claim(userId, quoteId, productId, personId, counterpartPersonId, relationType, questionKey);
        payments.claimReading(userId, batchId, quoteId, purchase.id());
        return purchase;
    }
}
