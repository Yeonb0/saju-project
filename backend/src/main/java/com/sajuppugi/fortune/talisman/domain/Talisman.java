package com.sajuppugi.fortune.talisman.domain;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Talisman(
        UUID id,
        UUID ownerUserId,
        UUID sourceReadingId,
        FortuneType fortuneType,
        Element element,
        Animal animal,
        String phrase,
        String description,
        String contentVersion,
        Status status,
        AssetKeys assets,
        String failureCode,
        Instant createdAt,
        Instant readyAt) {

    public Talisman {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(sourceReadingId, "sourceReadingId");
        Objects.requireNonNull(fortuneType, "fortuneType");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(animal, "animal");
        requireText(phrase, "phrase");
        requireText(description, "description");
        requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == Status.READY && (assets == null || readyAt == null)) {
            throw new IllegalArgumentException("Ready talisman requires assets and readyAt");
        }
        if (status != Status.READY && (assets != null || readyAt != null)) {
            throw new IllegalArgumentException("Only ready talisman can have assets and readyAt");
        }
        if (status == Status.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("Failed talisman requires failureCode");
        }
        if (status != Status.FAILED && failureCode != null) {
            throw new IllegalArgumentException("Only failed talisman can have failureCode");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public enum Animal { RAT, OX, TIGER, RABBIT, DRAGON, SNAKE, HORSE, GOAT, MONKEY, ROOSTER, DOG, PIG }

    public enum Status { PENDING, READY, FAILED }

    public enum OwnershipSource { PURCHASE, GIFT }

    public record AssetKeys(String original, String thumbnail, String share) {
        public AssetKeys {
            requireText(original, "original");
            requireText(thumbnail, "thumbnail");
            requireText(share, "share");
        }
    }
}
