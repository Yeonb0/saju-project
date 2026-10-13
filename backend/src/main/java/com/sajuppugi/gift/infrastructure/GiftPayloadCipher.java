package com.sajuppugi.gift.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
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
    private final SecureRandom random = new SecureRandom();

    public GiftPayloadCipher(@Value("${app.gift.orders-enabled:false}") boolean enabled,
            @Value("${app.gift.payload-key:}") String encodedKey, Environment environment) {
        if (!enabled) { key = null; return; }
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))
                || environment.acceptsProfiles(Profiles.of("staging", "production", "prod"))) {
            throw new IllegalStateException("Gift order drafts are restricted to local/test profiles");
        }
        try { key = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Gift payload key must be Base64"); }
        if (key.length != 32) throw new IllegalStateException("Gift payload key must contain 32 bytes");
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
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Gift encryption failed"); }
    }

    public String decrypt(String encoded, String context) {
        try {
            if (!encoded.startsWith("v1:")) throw new IllegalArgumentException();
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(encoded.substring(3)));
            byte[] nonce = new byte[12]; bytes.get(nonce);
            byte[] ciphertext = new byte[bytes.remaining()]; bytes.get(ciphertext);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, context).doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new IllegalStateException("Gift payload authentication failed");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
