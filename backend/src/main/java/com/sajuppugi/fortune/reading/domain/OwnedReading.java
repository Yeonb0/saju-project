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
        String subjectDisplayName,
        LocalDate eventDate,
        LinerResponse sections,
        String calculationVersion,
        String generationVersion,
        String contentVersion,
        GenerationMode generationMode,
        UUID talismanId,
        String talismanStatus,
        Instant createdAt) {

    public enum ProductOption { READING_ONLY, READING_WITH_TALISMAN }
}
