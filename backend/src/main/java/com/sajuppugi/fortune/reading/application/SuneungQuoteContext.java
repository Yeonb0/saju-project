package com.sajuppugi.fortune.reading.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SuneungQuoteContext {
    public String hash(UUID personId) {
        String canonical = "personId=" + personId
                + "&productCode=" + SuneungEventPolicy.PRODUCT_CODE
                + "&eventDate=" + SuneungEventPolicy.EXAM_DATE;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
