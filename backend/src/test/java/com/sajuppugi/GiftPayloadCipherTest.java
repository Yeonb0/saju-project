package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GiftPayloadCipherTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private GiftPayloadCipher create(String key, String... profiles) {
        var environment = new MockEnvironment(); environment.setActiveProfiles(profiles);
        return new GiftPayloadCipher(true, key, environment);
    }
    @Test void randomNoncesAndContextAuthenticationProtectPrivatePayload() {
        var cipher = create(KEY, "test");
        String a = cipher.encrypt("synthetic-private-data", "owner|order");
        assertThat(cipher.encrypt("synthetic-private-data", "owner|order")).isNotEqualTo(a);
        assertThat(cipher.decrypt(a, "owner|order")).isEqualTo("synthetic-private-data");
        assertThatThrownBy(() -> cipher.decrypt(a, "other|order")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(a.substring(0, 20), "owner|order")).isInstanceOf(IllegalStateException.class);
        assertThat(cipher.fingerprint("request|synthetic-private-data")).hasSize(64).doesNotContain("synthetic");
        assertThat(cipher.fingerprint("request|synthetic-private-data")).isNotEqualTo(cipher.fingerprint("key|synthetic-private-data"));
    }
    @Test void invalidKeysAndNonLocalEnvironmentsCannotEnableDrafts() {
        for (String key : java.util.List.of("", "invalid", "YQ=="))
            assertThatThrownBy(() -> create(key, "test")).isInstanceOf(IllegalStateException.class);
        for (String profile : java.util.List.of("staging", "production", "prod")) {
            assertThatThrownBy(() -> create(KEY, profile)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> create(KEY, "test", profile)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> create(KEY)).isInstanceOf(IllegalStateException.class);
    }
    @Test void disabledDoesNotRequireOrInspectAKey() {
        assertThatCode(() -> new GiftPayloadCipher(false, "not-configured", new MockEnvironment())).doesNotThrowAnyException();
    }
}
