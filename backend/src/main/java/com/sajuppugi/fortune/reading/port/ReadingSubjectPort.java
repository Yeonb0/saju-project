package com.sajuppugi.fortune.reading.port;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import java.util.UUID;

/** Person module boundary. Implementations must query by both ownerUserId and personId. */
public interface ReadingSubjectPort {
    OwnedSubject getOwnedSubject(UUID ownerUserId, UUID personId);

    record OwnedSubject(UUID personId, String displayName, BirthInput birthInput) {
        public OwnedSubject {
            if (personId == null || displayName == null || displayName.isBlank() || birthInput == null) {
                throw new IllegalArgumentException("Complete owned subject is required");
            }
        }
    }
}
