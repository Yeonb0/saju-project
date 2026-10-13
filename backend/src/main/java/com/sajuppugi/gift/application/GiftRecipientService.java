package com.sajuppugi.gift.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.application.BirthInputNormalizer;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftRecipientProfiles;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class GiftRecipientService {
    private final GiftAccessPort access;
    private final GiftFulfillmentPort fulfillment;
    private final JdbcGiftRecipientProfiles profiles;
    private final GiftPayloadCipher cipher;
    private final BirthInputNormalizer normalizer;
    private final ObjectMapper mapper;
    private final Clock clock;

    public GiftRecipientService(GiftAccessPort access, GiftFulfillmentPort fulfillment,
            JdbcGiftRecipientProfiles profiles, GiftPayloadCipher cipher, BirthInputNormalizer normalizer,
            ObjectMapper mapper, Clock clock) {
        this.access = access; this.fulfillment = fulfillment; this.profiles = profiles; this.cipher = cipher;
        this.normalizer = normalizer; this.mapper = mapper; this.clock = clock;
    }

    public Submission submit(String rawToken, String requestKey, Command command) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 512 || command == null)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        BirthInput birth = normalize(command);
        String name = normalizeName(command.displayName());
        Snapshot snapshot = new Snapshot(name, birth.birthDate().toString(),
                birth.birthTime() == null ? null : birth.birthTime().toString(), birth.birthTimeUnknown(),
                birth.calendarType(), birth.leapMonth(), birth.gender());
        String json = json(snapshot);
        String snapshotHash = cipher.fingerprint("gift-recipient-snapshot|" + json);
        UUID requestId = UUID.nameUUIDFromBytes(cipher.fingerprint("gift-recipient-request|" + requestKey)
                .getBytes(StandardCharsets.UTF_8));
        UUID giftId = access.verify(rawToken).giftId();
        UUID profileId = UUID.randomUUID();
        var receipt = fulfillment.accept(rawToken, requestId, snapshotHash, () -> profiles.insert(profileId, giftId,
                cipher.encrypt(json, "gift-recipient-profile|" + giftId + "|" + profileId),
                cipher.versionedActive() ? cipher.activeKeyId() : "legacy-v1", snapshotHash,
                profiles.productCode(giftId), clock.instant()));
        return new Submission(receipt.receiptId(), receipt.giftId(), receipt.state(), receipt.submittedAt());
    }

    private BirthInput normalize(Command command) {
        if (command.calendarType() == null || command.gender() == null)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        try {
            return normalizer.normalize(command.birthDate(), command.birthTime(), command.birthTimeUnknown(),
                    command.calendarType(), command.leapMonth(), command.gender());
        } catch (RuntimeException invalid) { throw new ApiException(ErrorCode.INVALID_REQUEST); }
    }
    private String normalizeName(String name) {
        if (name == null || name.isBlank()) throw new ApiException(ErrorCode.INVALID_REQUEST);
        String normalized = name.strip();
        if (normalized.codePointCount(0, normalized.length()) > 50
                || normalized.codePoints().anyMatch(Character::isISOControl))
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        return normalized;
    }
    private String json(Snapshot snapshot) {
        try { return mapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Gift recipient serialization failed"); }
    }

    public record Command(String displayName, String birthDate, String birthTime, boolean birthTimeUnknown,
                          BirthInput.CalendarType calendarType, boolean leapMonth, BirthInput.Gender gender) {
        @Override public String toString() { return "GiftRecipientCommand[REDACTED]"; }
    }
    private record Snapshot(String displayName, String birthDate, String birthTime, boolean birthTimeUnknown,
                            BirthInput.CalendarType calendarType, boolean leapMonth, BirthInput.Gender gender) {}
    public record Submission(UUID receiptId, UUID giftId, String state, java.time.Instant submittedAt) {}
}
