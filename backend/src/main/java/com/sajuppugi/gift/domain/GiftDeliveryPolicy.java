package com.sajuppugi.gift.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GiftDeliveryPolicy {
    public static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");
    public static final int DAILY_MANUAL_LIMIT = 3;
    public static final Duration MANUAL_INTERVAL = Duration.ofMinutes(1);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));

    private GiftDeliveryPolicy() {}

    /** failedAttempt is 1 for the initial send. Retry only confirmed transient failures. */
    public static Optional<Instant> nextAutomaticAttempt(int failedAttempt, Instant failedAt) {
        Objects.requireNonNull(failedAt);
        if (failedAttempt < 1 || failedAttempt > 4) {
            throw new IllegalArgumentException("Attempt must be between 1 and 4");
        }
        return failedAttempt == 4 ? Optional.empty()
                : Optional.of(failedAt.plus(RETRY_DELAYS.get(failedAttempt - 1)));
    }

    /** Count accepted resend and link-reissue requests together, including failed sends. */
    public static ManualAvailability manualAvailability(int acceptedToday, Instant lastAcceptedAt,
                                                        boolean inProgress, Instant now) {
        Objects.requireNonNull(now);
        if (acceptedToday < 0 || acceptedToday > DAILY_MANUAL_LIMIT
                || (lastAcceptedAt != null && lastAcceptedAt.isAfter(now))) {
            throw new IllegalArgumentException("Invalid manual delivery history");
        }
        int remaining = DAILY_MANUAL_LIMIT - acceptedToday;
        Instant next = lastAcceptedAt == null ? null : lastAcceptedAt.plus(MANUAL_INTERVAL);
        if (remaining == 0) {
            Instant midnight = now.atZone(DAY_ZONE).toLocalDate().plusDays(1)
                    .atStartOfDay(DAY_ZONE).toInstant();
            if (next == null || midnight.isAfter(next)) {
                next = midnight;
            }
        }
        if (next != null && !next.isAfter(now)) {
            next = null;
        }
        return new ManualAvailability(!inProgress && remaining > 0 && next == null, remaining, next);
    }

    // Gift expiry/cancellation and ownership must also be checked by the application service.
    public record ManualAvailability(boolean canResend, int remainingResends, Instant nextResendAt) {}
}
