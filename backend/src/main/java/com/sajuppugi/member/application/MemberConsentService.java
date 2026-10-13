package com.sajuppugi.member.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.member.domain.Member.Status;
import com.sajuppugi.member.domain.MemberConsent;
import com.sajuppugi.member.port.MemberConsentRepository;
import com.sajuppugi.member.port.MemberRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberConsentService {
    private final MemberConsentRepository consents;
    private final MemberRepository members;
    private final MemberConsentProperties properties;
    private final Clock clock;

    public MemberConsentService(MemberConsentRepository consents, MemberRepository members,
            MemberConsentProperties properties, Clock clock) {
        this.consents = consents;
        this.members = members;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConsentStatus status(UUID userId) {
        requireActive(userId);
        return consents.find(userId, properties.termsVersion(), properties.privacyVersion())
                .map(value -> ConsentStatus.accepted(properties, value))
                .orElseGet(() -> ConsentStatus.missing(properties));
    }

    @Transactional
    public ConsentStatus accept(UUID userId, boolean ageOver14, boolean termsAgreed,
            boolean privacyAgreed, boolean marketingAgreed) {
        requireActive(userId);
        if (!ageOver14 || !termsAgreed || !privacyAgreed) {
            throw new ApiException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }
        MemberConsent requested = new MemberConsent(UUID.randomUUID(), userId, properties.termsVersion(),
                properties.privacyVersion(), true, true, true, marketingAgreed, clock.instant());
        return ConsentStatus.accepted(properties, consents.saveIfAbsent(requested));
    }

    @Transactional(readOnly = true)
    public void requireCurrentForPersonCreation(UUID userId) {
        if (!properties.requiredForPersonCreation()) return;
        if (!status(userId).requiredAccepted()) throw new ApiException(ErrorCode.REQUIRED_CONSENT_MISSING);
    }

    private void requireActive(UUID userId) {
        if (userId != null && members.find(userId).filter(member -> member.status() == Status.ACTIVE).isPresent()) return;
        throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
    }

    public record ConsentStatus(String termsVersion, String privacyVersion, boolean requiredAccepted,
            boolean ageOver14Confirmed, boolean termsAgreed, boolean privacyAgreed,
            boolean marketingAgreed, java.time.Instant agreedAt) {
        static ConsentStatus accepted(MemberConsentProperties properties, MemberConsent value) {
            return new ConsentStatus(properties.termsVersion(), properties.privacyVersion(), true,
                    value.ageOver14Confirmed(), value.termsAgreed(), value.privacyAgreed(),
                    value.marketingAgreed(), value.agreedAt());
        }
        static ConsentStatus missing(MemberConsentProperties properties) {
            return new ConsentStatus(properties.termsVersion(), properties.privacyVersion(), false,
                    false, false, false, false, null);
        }
    }
}
