package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.RelationType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReadingQuoteContext {
    public String hash(FortuneType fortuneType, ProductOption option, UUID personId,
                       UUID counterpartPersonId, RelationType relationType, String questionKey) {
        String canonical = "fortuneType=" + fortuneType
                + "&option=" + option
                + "&personId=" + personId
                + "&counterpartPersonId=" + value(counterpartPersonId)
                + "&relationType=" + value(relationType)
                + "&questionKey=" + value(questionKey);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
