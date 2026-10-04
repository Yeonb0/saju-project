package com.sajuppugi.gift.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class GiftLifetimePolicy {
    public static final Duration UNUSED_VALIDITY = Duration.ofDays(90);
    public static final Duration FIRST_ACCESS_GRACE = Duration.ofHours(24);
    public static final Duration USED_VALIDITY = Duration.ofDays(90);

    private GiftLifetimePolicy() {}

    public static Instant unusedExpiresAt(Instant approvedAt) {
        return Objects.requireNonNull(approvedAt).plus(UNUSED_VALIDITY);
    }

    public static boolean canStart(Instant approvedAt, Instant recipientSubmittedAt, Instant now) {
        Objects.requireNonNull(now);
        Instant expiry = unusedExpiresAt(approvedAt);
        if (now.isBefore(approvedAt)) {
            return false;
        }
        if (recipientSubmittedAt == null) {
            return false;
        }
        boolean validSubmission = !recipientSubmittedAt.isBefore(approvedAt)
                && recipientSubmittedAt.isBefore(expiry) && !recipientSubmittedAt.isAfter(now);
        return validSubmission && now.isBefore(expiry.plus(FIRST_ACCESS_GRACE));
    }

    /** Call only when the first content response successfully supplies the content. */
    public static Instant usedExpiresAt(Instant firstProvidedAt) {
        return Objects.requireNonNull(firstProvidedAt).plus(USED_VALIDITY);
    }
}
