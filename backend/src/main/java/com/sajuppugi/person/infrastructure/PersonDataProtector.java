package com.sajuppugi.person.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PersonDataProtector {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PersonDataProtector(PersonDataProtectionProperties properties) {
        try {
            byte[] decoded = Base64.getDecoder().decode(properties.encryptionKey());
            if (decoded.length != 32) throw new IllegalArgumentException("person encryption key must be 32 bytes");
            this.key = new SecretKeySpec(decoded, "AES");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("PERSON_ENCRYPTION_KEY must be a base64 encoded 32-byte key", exception);
        }
    }

    public byte[] encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("person data encryption failed", exception);
        }
    }

    public String decrypt(byte[] value) {
        if (value == null) return null;
        if (value.length <= IV_LENGTH) throw new IllegalStateException("invalid encrypted person data");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("person data decryption failed", exception);
        }
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
