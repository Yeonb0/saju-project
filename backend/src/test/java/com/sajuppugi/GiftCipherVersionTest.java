package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GiftCipherVersionTest {
    static final String ROOT = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    GiftPayloadCipher cipher(String active, String ring) {
        var env = new MockEnvironment(); env.setActiveProfiles("test");
        return new GiftPayloadCipher(true, ROOT, env, active, ring, new ObjectMapper());
    }
    String key(int value) { byte[] bytes = new byte[32]; Arrays.fill(bytes, (byte) value); return Base64.getEncoder().encodeToString(bytes); }
    @Test void rotationKeepsLegacyReadableAndFingerprintStable() {
        var old = cipher("", "{}"); var modern = cipher("new", "{\"new\":\"" + key(1) + "\"}");
        String legacy = old.encrypt("private", "owner|order"), current = modern.encrypt("private", "owner|order");
        assertThat(current).startsWith("v2:new:"); assertThat(modern.decrypt(legacy, "owner|order")).isEqualTo("private");
        assertThat(modern.decrypt(current, "owner|order")).isEqualTo("private");
        assertThat(modern.fingerprint("key|command")).isEqualTo(old.fingerprint("key|command"));
    }
    @Test void keyIdAndOwnerContextAreAuthenticatedEvenWithIdenticalKeyBytes() {
        var modern = cipher("new", "{\"new\":\"" + key(1) + "\",\"other\":\"" + key(1) + "\"}");
        String encrypted = modern.encrypt("private", "owner|order");
        assertThatThrownBy(() -> modern.decrypt(encrypted.replace("v2:new:", "v2:other:"), "owner|order")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> modern.decrypt(encrypted, "someone-else|order")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> modern.decrypt(encrypted.replace("v2:new:", "v2:missing:"), "owner|order")).isInstanceOf(IllegalStateException.class);
    }
    @Test void invalidKeyringMissingActiveAndDuplicateIdsAreRejected() {
        assertThatThrownBy(() -> cipher("missing", "{}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("new", "{\"new\":\"short\"}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("new", "{\"new\":\"" + key(1) + "\",\"new\":\"" + key(2) + "\"}")).isInstanceOf(IllegalStateException.class);
    }
    @Test void priorVersionedKeyMustRemainAvailableUntilPayloadsAreRewritten() {
        var prior = cipher("old", "{\"old\":\"" + key(1) + "\"}");
        var rotating = cipher("new", "{\"old\":\"" + key(1) + "\",\"new\":\"" + key(2) + "\"}");
        String payload = prior.encrypt("private", "context"); assertThat(rotating.decrypt(payload, "context")).isEqualTo("private");
        var retired = cipher("new", "{\"new\":\"" + key(2) + "\"}");
        assertThatThrownBy(() -> retired.decrypt(payload, "context")).isInstanceOf(IllegalStateException.class);
    }
}
