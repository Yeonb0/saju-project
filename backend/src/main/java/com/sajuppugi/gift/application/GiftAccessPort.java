package com.sajuppugi.gift.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A supplies token/state authorization; B supplies recipient data and content. Never log rawToken. */
public interface GiftAccessPort {
    GiftAccess verify(String rawToken);

    PublicGift describe(String rawToken);

    GiftAccess verifyTalismanAccess(String rawToken, UUID talismanId);

    record GiftAccess(UUID giftId, Instant accessExpiresAt) {}

    record PublicGift(String recipientName, String senderName, String message, ProductOption option,
                      List<IncludedContent> includedContents, AccessState state, Instant accessExpiresAt) {
        public PublicGift {
            includedContents = List.copyOf(includedContents);
        }
        public enum ProductOption { TALISMAN_ONLY, READING_WITH_TALISMAN }
        /** Stable content identifiers. The client owns localized labels and artwork. */
        public enum IncludedContent { LETTER, TALISMAN, READING, SUNEUNG_CHECKLIST }
        public enum AccessState { INPUT_REQUIRED, PROCESSING, READY }
    }
}
