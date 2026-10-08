package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import java.util.UUID;

/** Talisman fulfillment boundary used by the bundled Suneung product. */
public interface SuneungTalismanPort {
    TalismanFulfillment create(UUID ownerUserId, UUID readingId, CalculationFacts facts, String contentVersion);

    record TalismanFulfillment(UUID talismanId, Status status) {
        public TalismanFulfillment {
            if (talismanId == null || status == null) {
                throw new IllegalArgumentException("Complete talisman fulfillment is required");
            }
        }
    }

    enum Status { PENDING, READY }
}
