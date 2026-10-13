package com.sajuppugi.member.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MemberConsent(UUID id, UUID userId, String termsVersion, String privacyVersion,
        boolean ageOver14Confirmed, boolean termsAgreed, boolean privacyAgreed,
        boolean marketingAgreed, Instant agreedAt) {
    public MemberConsent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        requireText(termsVersion, "termsVersion");
        requireText(privacyVersion, "privacyVersion");
        Objects.requireNonNull(agreedAt, "agreedAt");
        if (!ageOver14Confirmed || !termsAgreed || !privacyAgreed) {
            throw new IllegalArgumentException("Every required consent must be accepted");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
