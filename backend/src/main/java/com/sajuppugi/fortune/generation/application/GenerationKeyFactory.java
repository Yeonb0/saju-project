package com.sajuppugi.fortune.generation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GenerationKeyFactory {
    private final ObjectMapper canonicalMapper;

    public GenerationKeyFactory(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String create(GenerationCommand command, JsonNode safeFacts) {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("facts", safeFacts);
        seed.put("fortuneType", command.fortuneType());
        seed.put("referenceDate", command.referenceDate());
        seed.put("interestKey", command.interestKey());
        seed.put("calculationVersion", command.calculationFacts().meta().calculationVersion());
        seed.put("generationVersion", command.generationVersion());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalMapper.writeValueAsBytes(seed)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create generation key", exception);
        }
    }

    public String hashInput(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalMapper.writeValueAsBytes(value)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash generation input", exception);
        }
    }
}
