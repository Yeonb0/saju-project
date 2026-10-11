package com.sajuppugi.fortune.reading.domain;

import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable sensitive generation input persisted encrypted before external generation starts.
 * It lets a recovery worker resume a debited purchase without rereading mutable people or quotes.
 */
public record GenerationResumeContext(
        GenerationCommand command,
        OwnedReading.ProductOption productOption,
        UUID subjectPersonId,
        String subjectDisplayName,
        UUID counterpartPersonId,
        String counterpartDisplayName,
        String relationType,
        String questionKey,
        boolean includesTalisman) {
    public GenerationResumeContext {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(productOption, "productOption");
        Objects.requireNonNull(subjectPersonId, "subjectPersonId");
        if (includesTalisman && command.calculationFacts() == null) {
            throw new IllegalArgumentException("Talisman generation requires calculation facts");
        }
    }
}
