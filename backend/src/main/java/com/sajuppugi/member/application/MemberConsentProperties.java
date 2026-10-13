package com.sajuppugi.member.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.member.consent")
public record MemberConsentProperties(String termsVersion, String privacyVersion,
        boolean requiredForPersonCreation) {
    public MemberConsentProperties {
        if (termsVersion == null || termsVersion.isBlank()) termsVersion = "2026-10-14";
        if (privacyVersion == null || privacyVersion.isBlank()) privacyVersion = "2026-10-14";
    }
}
