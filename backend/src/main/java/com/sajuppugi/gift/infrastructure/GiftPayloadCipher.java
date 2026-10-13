package com.sajuppugi.gift.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Local/test order drafts only until retention, key rotation and delivery are implemented. */
@Component
public class GiftPayloadCipher {
    private final byte[] key;
    private final Map<String, byte[]> versionedKeys;
    private final String activeId;
    private final SecureRandom random = new SecureRandom();

    public GiftPayloadCipher(@Value("${app.gift.orders-enabled:false}") boolean enabled,
            @Value("${app.gift.payload-key:}") String encodedKey, Environment environment) {
        this(enabled, encodedKey, environment, "", "{}", new ObjectMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GiftPayloadCipher(@Value("${app.gift.orders-enabled:false}") boolean enabled,
            @Value("${app.gift.payload-key:}") String encodedKey, Environment environment,
            @Value("${app.gift.payload-active-key-id:}") String activeId,
            @Value("${app.gift.payload-keyring:{}}") String keyring, ObjectMapper mapper) {
        this.activeId = activeId;
        if (!enabled) { key = null; versionedKeys = Map.of(); return; }
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))
                || environment.acceptsProfiles(Profiles.of("staging", "production", "prod"))) {
            throw new IllegalStateException("Gift order drafts are restricted to local/test profiles");
        }
        try { key = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Gift payload key must be Base64"); }
        if (key.length != 32) throw new IllegalStateException("Gift payload key must contain 32 bytes");
        try {
            Map<String, String> encoded = mapper.readerFor(new TypeReference<Map<String, String>>() {})
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(keyring);
            Map<String, byte[]> decoded = new HashMap<>();
            for (var entry : encoded.entrySet()) {
                if (!entry.getKey().matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException();
                byte[] value = Base64.getDecoder().decode(entry.getValue());
                if (value.length != 32) throw new IllegalArgumentException();
                decoded.put(entry.getKey(), value);
            }
            if (!activeId.isEmpty() && !decoded.containsKey(activeId)) throw new IllegalArgumentException();
            versionedKeys = Map.copyOf(decoded);
        } catch (Exception invalid) { throw new IllegalStateException("Gift encryption keyring or active key is invalid"); }
    }

    public String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("gift-order-v1|" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Gift fingerprint failed"); }
    }

    public String encrypt(String plaintext, String context) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context, activeId);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return activePrefix() + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Gift encryption failed"); }
    }

    public String decrypt(String encoded, String context) {
        try {
            String id; String body;
            if (encoded.startsWith("v1:")) { id = ""; body = encoded.substring(3); }
            else {
                String[] parts = encoded.split(":", 3);
                if (parts.length != 3 || !parts[0].equals("v2") || !versionedKeys.containsKey(parts[1])) throw new IllegalArgumentException();
                id = parts[1]; body = parts[2];
            }
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(body));
            byte[] nonce = new byte[12]; bytes.get(nonce);
            byte[] ciphertext = new byte[bytes.remaining()]; bytes.get(ciphertext);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, context, id).doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new IllegalStateException("Gift payload authentication failed");
        }
    }

    public boolean versionedActive() { return !activeId.isEmpty(); }
    public String activeKeyId() { return activeId; }
    public String activePrefix() { return activeId.isEmpty() ? "v1:" : "v2:" + activeId + ":"; }

    private Cipher cipher(int mode, byte[] nonce, String context, String keyId) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(keyId.isEmpty() ? key : versionedKeys.get(keyId), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD((keyId.isEmpty() ? context : context + "|gift-payload-key|" + keyId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
