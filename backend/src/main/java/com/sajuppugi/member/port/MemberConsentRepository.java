package com.sajuppugi.member.port;

import com.sajuppugi.member.domain.MemberConsent;
import java.util.Optional;
import java.util.UUID;

public interface MemberConsentRepository {
    Optional<MemberConsent> find(UUID userId, String termsVersion, String privacyVersion);
    MemberConsent saveIfAbsent(MemberConsent consent);
}
