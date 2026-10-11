package com.sajuppugi.fortune.reading.infrastructure;

import com.sajuppugi.common.crypto.AesGcmCipher;
import com.sajuppugi.person.infrastructure.PersonDataProtectionProperties;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class GenerationContextProtector {
    private static final String LEGACY_PREFIX = "enc:v1:";
    private static final String CURRENT_PREFIX = "enc:v2:";
    private final AesGcmCipher current;
    private final AesGcmCipher legacy;

    public GenerationContextProtector(GenerationContextProtectionProperties properties,
                                      PersonDataProtectionProperties legacyProperties) {
        current = new AesGcmCipher(properties.encryptionKey(), "GENERATION_CONTEXT_ENCRYPTION_KEY");
        legacy = new AesGcmCipher(legacyProperties.encryptionKey(), "PERSON_ENCRYPTION_KEY");
    }

    public String encrypt(String json) {
        return CURRENT_PREFIX + Base64.getEncoder().encodeToString(current.encrypt(json));
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (stored.startsWith(CURRENT_PREFIX)) {
            return current.decrypt(Base64.getDecoder().decode(stored.substring(CURRENT_PREFIX.length())));
        }
        if (stored.startsWith(LEGACY_PREFIX)) {
            return legacy.decrypt(Base64.getDecoder().decode(stored.substring(LEGACY_PREFIX.length())));
        }
        return stored;
    }
}
