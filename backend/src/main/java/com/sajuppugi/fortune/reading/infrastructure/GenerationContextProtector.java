package com.sajuppugi.fortune.reading.infrastructure;

import com.sajuppugi.person.infrastructure.PersonDataProtector;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class GenerationContextProtector {
    private static final String PREFIX = "enc:v1:";
    private final PersonDataProtector protector;

    public GenerationContextProtector(PersonDataProtector protector) {
        this.protector = protector;
    }

    public String encrypt(String json) {
        return PREFIX + Base64.getEncoder().encodeToString(protector.encrypt(json));
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored; // deployment compatibility for pre-encryption rows
        byte[] encrypted = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        return protector.decrypt(encrypted);
    }
}
