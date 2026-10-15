package com.sajuppugi.fortune.talisman.port;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import java.util.Objects;
import java.util.UUID;

public interface TalismanFulfillmentPort {
    TalismanFulfillment create(CreateTalisman command);

    /**
     * Creates an addon from the immutable, already-safe facts snapshot retained
     * with the completed reading.  It intentionally needs only five-elements,
     * so no mutable person record or raw birth input is reread at purchase time.
     */
    TalismanFulfillment createFromStoredFacts(CreateFromStoredFacts command);

    record CreateTalisman(UUID ownerUserId, UUID readingId, FortuneType fortuneType,
                          CalculationFacts facts, String contentVersion) {
        public CreateTalisman {
            Objects.requireNonNull(ownerUserId, "ownerUserId");
            Objects.requireNonNull(readingId, "readingId");
            Objects.requireNonNull(fortuneType, "fortuneType");
            Objects.requireNonNull(facts, "facts");
            if (contentVersion == null || contentVersion.isBlank()) {
                throw new IllegalArgumentException("contentVersion is required");
            }
        }
    }

    record CreateFromStoredFacts(UUID ownerUserId, UUID readingId, FortuneType fortuneType,
                                 CalculationFacts.FiveElements fiveElements, String contentVersion) {
        public CreateFromStoredFacts {
            Objects.requireNonNull(ownerUserId, "ownerUserId");
            Objects.requireNonNull(readingId, "readingId");
            Objects.requireNonNull(fortuneType, "fortuneType");
            Objects.requireNonNull(fiveElements, "fiveElements");
            if (contentVersion == null || contentVersion.isBlank()) {
                throw new IllegalArgumentException("contentVersion is required");
            }
        }
    }

    record TalismanFulfillment(UUID talismanId, Status status) {
        public TalismanFulfillment {
            Objects.requireNonNull(talismanId, "talismanId");
            Objects.requireNonNull(status, "status");
            if (status == Status.FAILED) {
                throw new IllegalArgumentException("Failed talisman cannot fulfill a purchase");
            }
        }
    }
}
