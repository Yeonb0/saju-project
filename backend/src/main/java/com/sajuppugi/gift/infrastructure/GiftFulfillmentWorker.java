package com.sajuppugi.gift.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.fortune.calculation.application.SajuCalculationUseCase;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.generation.application.DeterministicReadingFallback;
import com.sajuppugi.fortune.generation.application.LinerFactProjector;
import com.sajuppugi.fortune.generation.domain.GenerationModels;
import com.sajuppugi.gift.application.GiftFulfillmentPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Uses a committed lease before any decryption; no external call or long DB transaction is held here. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.fulfillment-worker-enabled", havingValue = "true")
public class GiftFulfillmentWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GiftFulfillmentWorker.class);
    private final JdbcGiftFulfillmentJobs jobs; private final JdbcGiftRecipientProfiles profiles;
    private final GiftPayloadCipher cipher; private final SajuCalculationUseCase calculation;
    private final LinerFactProjector facts; private final DeterministicReadingFallback fallback;
    private final GiftFulfillmentPort fulfillment; private final JdbcGiftResultSnapshots snapshots;
    private final ObjectMapper mapper; private final Clock clock;
    public GiftFulfillmentWorker(JdbcGiftFulfillmentJobs jobs, JdbcGiftRecipientProfiles profiles, GiftPayloadCipher cipher,
            SajuCalculationUseCase calculation, LinerFactProjector facts, DeterministicReadingFallback fallback,
            GiftFulfillmentPort fulfillment, JdbcGiftResultSnapshots snapshots, ObjectMapper mapper, Clock clock) {
        this.jobs=jobs; this.profiles=profiles; this.cipher=cipher; this.calculation=calculation; this.facts=facts;
        this.fallback=fallback; this.fulfillment=fulfillment; this.snapshots=snapshots; this.mapper=mapper; this.clock=clock;
    }
    @Scheduled(fixedDelayString = "${app.gift.fulfillment-worker-delay:60000}", initialDelayString = "${app.gift.fulfillment-worker-delay:60000}")
    public void run() {
        for (UUID id : jobs.due(clock.instant(), 20)) {
            try { jobs.claim(id, clock.instant(), Duration.ofMinutes(2), 5).ifPresent(this::fulfill); }
            catch (RuntimeException failure) { log.warn("Gift fulfillment claim failed: jobId={}, type={}", id, failure.getClass().getSimpleName()); }
        }
    }
    void fulfill(JdbcGiftFulfillmentJobs.Lease lease) {
        try {
            var profile = profiles.loadForWorker(lease.profileId(), lease.giftId()).orElseThrow();
            Recipient recipient = mapper.readValue(cipher.decrypt(profile.ciphertext(), "gift-recipient-profile|" + profile.giftId() + "|" + profile.profileId()), Recipient.class);
            var calculated = calculation.calculate(new BirthInput(LocalDate.parse(recipient.birthDate()),
                    recipient.birthTime() == null ? null : LocalTime.parse(recipient.birthTime()), recipient.birthTimeUnknown(),
                    recipient.calendarType(), recipient.leapMonth(), recipient.gender()), CalculationPolicy.CURRENT);
            String json = result(profile.productCode(), calculated);
            String hash = sha256(json); UUID content = UUID.nameUUIDFromBytes(("gift-result|" + lease.giftId() + "|" + hash).getBytes(StandardCharsets.UTF_8));
            fulfillment.provide(profile.receiptId(), content, () -> {
                jobs.requireCurrent(lease);
                snapshots.insert(new JdbcGiftResultSnapshots.Snapshot(UUID.randomUUID(), lease.giftId(), profile.receiptId(), content,
                        profile.productCode(), "gift-v1", fallback.name(), json, hash, clock.instant()));
            });
            jobs.complete(lease, JdbcGiftFulfillmentJobs.State.READY, clock.instant());
        } catch (RuntimeException | java.io.IOException failure) {
            jobs.retry(lease, clock.instant(), Duration.ofMinutes(1));
            log.warn("Gift fulfillment failed: jobId={}, type={}", lease.jobId(), failure.getClass().getSimpleName());
        }
    }
    private String result(String product, com.sajuppugi.fortune.calculation.domain.CalculationFacts calculated) throws java.io.IOException {
        ObjectNode result = mapper.createObjectNode(); ObjectNode talisman = result.putObject("talisman");
        talisman.put("status", "PENDING"); talisman.put("element", calculated.dayMaster().element().name());
        if ("GIFT_SUNEUNG_READING_WITH_TALISMAN".equals(product)) {
            var request = new GenerationModels.LinerRequest("gift-fallback", calculated.meta().calculationVersion(), fallback.name(),
                    GenerationModels.FortuneType.SUNEUNG, LocalDate.now(clock), "EXAM_FOCUS", facts.project(calculated), List.of(),
                    List.of(GenerationModels.SectionKey.SUMMARY, GenerationModels.SectionKey.EXAM_DAY, GenerationModels.SectionKey.EXAM_PERIODS,
                            GenerationModels.SectionKey.FOCUS, GenerationModels.SectionKey.MEAL, GenerationModels.SectionKey.PREPARATION,
                            GenerationModels.SectionKey.ANXIETY_MANAGEMENT, GenerationModels.SectionKey.MISSING_ELEMENT));
            result.set("guide", mapper.valueToTree(fallback.generate(request).suneungGuide()));
        }
        return mapper.writeValueAsString(result);
    }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private record Recipient(String displayName, String birthDate, String birthTime, boolean birthTimeUnknown,
                             BirthInput.CalendarType calendarType, boolean leapMonth, BirthInput.Gender gender) {}
}
