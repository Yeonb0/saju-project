package com.sajuppugi.person.infrastructure;

import com.sajuppugi.common.crypto.AesGcmCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PersonDataProtector {
    private final SecretKeySpec key;
    private final AesGcmCipher cipher;

    public PersonDataProtector(PersonDataProtectionProperties properties) {
        try {
            byte[] decoded = Base64.getDecoder().decode(properties.encryptionKey());
            if (decoded.length != 32) throw new IllegalArgumentException("person encryption key must be 32 bytes");
            this.key = new SecretKeySpec(decoded, "AES");
            this.cipher = new AesGcmCipher(properties.encryptionKey(), "PERSON_ENCRYPTION_KEY");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("PERSON_ENCRYPTION_KEY must be a base64 encoded 32-byte key", exception);
        }
    }

    public byte[] encrypt(String value) {
        return cipher.encrypt(value);
    }

    public String decrypt(byte[] value) {
        return cipher.decrypt(value);
    }

    public String fingerprint(String canonicalBirthInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return java.util.HexFormat.of().formatHex(mac.doFinal(canonicalBirthInput.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("person fingerprint failed", exception);
        }
    }
}
