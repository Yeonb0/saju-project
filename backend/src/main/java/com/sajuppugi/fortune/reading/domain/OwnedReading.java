package com.sajuppugi.fortune.reading.domain;

import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OwnedReading(
        UUID id,
        UUID resultId,
        UUID ownerUserId,
        UUID purchaseId,
        FortuneType fortuneType,
        ProductOption productOption,
        UUID subjectPersonId,
        String subjectDisplayName,
        UUID counterpartPersonId,
        String counterpartDisplayName,
        String relationType,
        String questionKey,
        LocalDate eventDate,
        LinerResponse sections,
        String calculationVersion,
        String ruleVersion,
        String generationVersion,
        String contentVersion,
        List<SectionEvidence> evidenceSummary,
        GenerationMode generationMode,
        UUID talismanId,
        String talismanStatus,
        Instant createdAt) {

    public OwnedReading {
        evidenceSummary = evidenceSummary == null ? evidenceFrom(sections) : List.copyOf(evidenceSummary);
    }

    public OwnedReading(UUID id, UUID resultId, UUID ownerUserId, UUID purchaseId,
                        FortuneType fortuneType, ProductOption productOption, String subjectDisplayName,
                        LocalDate eventDate, LinerResponse sections, String calculationVersion,
                        String generationVersion, String contentVersion, GenerationMode generationMode,
                        UUID talismanId, String talismanStatus, Instant createdAt) {
        this(id, resultId, ownerUserId, purchaseId, fortuneType, productOption, null, subjectDisplayName,
                null, null, null, null, eventDate, sections, calculationVersion, null, generationVersion,
                contentVersion, evidenceFrom(sections), generationMode, talismanId, talismanStatus, createdAt);
    }

    /** Compatibility constructor for callers that do not yet carry interpretation metadata. */
    public OwnedReading(UUID id, UUID resultId, UUID ownerUserId, UUID purchaseId,
                        FortuneType fortuneType, ProductOption productOption, UUID subjectPersonId,
                        String subjectDisplayName, UUID counterpartPersonId, String counterpartDisplayName,
                        String relationType, String questionKey, LocalDate eventDate, LinerResponse sections,
                        String calculationVersion, String generationVersion, String contentVersion,
                        GenerationMode generationMode, UUID talismanId, String talismanStatus, Instant createdAt) {
        this(id, resultId, ownerUserId, purchaseId, fortuneType, productOption, subjectPersonId, subjectDisplayName,
                counterpartPersonId, counterpartDisplayName, relationType, questionKey, eventDate, sections,
                calculationVersion, null, generationVersion, contentVersion, evidenceFrom(sections), generationMode,
                talismanId, talismanStatus, createdAt);
    }

    public OwnedReading withTalisman(UUID newTalismanId, String newTalismanStatus) {
        if (talismanId != null || talismanStatus != null) {
            throw new IllegalStateException("Reading already has a talisman");
        }
        return new OwnedReading(id, resultId, ownerUserId, purchaseId, fortuneType, productOption,
                subjectPersonId, subjectDisplayName, counterpartPersonId, counterpartDisplayName,
                relationType, questionKey, eventDate, sections, calculationVersion, ruleVersion, generationVersion,
                contentVersion, evidenceSummary, generationMode, newTalismanId, newTalismanStatus, createdAt);
    }

    public static List<SectionEvidence> evidenceFrom(LinerResponse sections) {
        if (sections == null || sections.sections() == null) return List.of();
        return sections.sections().stream().map(section ->
                new SectionEvidence(section.key().name(), section.sourceFactKeys())).toList();
    }

    public record SectionEvidence(String sectionKey, List<String> factKeys) {
        public SectionEvidence { factKeys = List.copyOf(factKeys); }
    }

    public enum ProductOption { READING_ONLY, READING_WITH_TALISMAN }
}
