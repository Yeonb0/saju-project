package com.sajuppugi.person.domain;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.domain.RelationType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Person(UUID id, UUID ownerUserId, ProfileType profileType, String displayName,
                     BirthInput birthInput, RelationType relationType, Instant thirdPartyConfirmedAt,
                     Instant createdAt, Instant updatedAt) {
    public enum ProfileType { SELF, OTHER }

    public Person {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ownerUserId);
        Objects.requireNonNull(profileType);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(birthInput);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
