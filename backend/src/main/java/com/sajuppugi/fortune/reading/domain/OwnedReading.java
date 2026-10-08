package com.sajuppugi.fortune.reading.domain;

import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import java.time.Instant;
import java.time.LocalDate;
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
        String generationVersion,
        String contentVersion,
        GenerationMode generationMode,
        UUID talismanId,
        String talismanStatus,
        Instant createdAt) {

    public OwnedReading(UUID id, UUID resultId, UUID ownerUserId, UUID purchaseId,
                        FortuneType fortuneType, ProductOption productOption, String subjectDisplayName,
                        LocalDate eventDate, LinerResponse sections, String calculationVersion,
                        String generationVersion, String contentVersion, GenerationMode generationMode,
                        UUID talismanId, String talismanStatus, Instant createdAt) {
        this(id, resultId, ownerUserId, purchaseId, fortuneType, productOption, null, subjectDisplayName,
                null, null, null, null, eventDate, sections, calculationVersion, generationVersion,
                contentVersion, generationMode, talismanId, talismanStatus, createdAt);
    }

    public OwnedReading withTalisman(UUID newTalismanId, String newTalismanStatus) {
        if (talismanId != null || talismanStatus != null) {
            throw new IllegalStateException("Reading already has a talisman");
        }
        return new OwnedReading(id, resultId, ownerUserId, purchaseId, fortuneType, productOption,
                subjectPersonId, subjectDisplayName, counterpartPersonId, counterpartDisplayName,
                relationType, questionKey, eventDate, sections, calculationVersion, generationVersion,
                contentVersion, generationMode, newTalismanId, newTalismanStatus, createdAt);
    }

    public enum ProductOption { READING_ONLY, READING_WITH_TALISMAN }
}
