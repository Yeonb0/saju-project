package com.sajuppugi.fortune.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.generation.infrastructure.LinerApiProperties;
import com.sajuppugi.fortune.generation.infrastructure.LinerApiProvider;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Opt-in smoke test for the real Liner API. It is skipped during normal test runs.
 */
@EnabledIfEnvironmentVariable(named = "LINER_LIVE_TEST", matches = "true")
class LinerLiveSmokeTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void generatesAndValidatesRealResponse() throws Exception {
        Properties environment = localEnvironment();
        LinerApiProvider provider = new LinerApiProvider(RestClient.builder(), mapper, new LinerApiProperties(
                value(environment, "LINER_BASE_URL", "https://platform.liner.com/api/v1"),
                required(environment, "LINER_API_KEY"),
                value(environment, "LINER_MODEL", "liner-mark"),
                Duration.ofSeconds(3), Duration.ofSeconds(60), 4_000, "low"));

        JsonNode facts = mapper.readTree("""
                {
                  "dayMaster": {"hangul": "갑", "hanja": "甲", "element": "WOOD"},
                  "fiveElements": {
                    "counts": {"WOOD": 3, "FIRE": 2, "EARTH": 1, "METAL": 1, "WATER": 1}
                  },
                  "balance": {"strongest": "WOOD", "weakest": "EARTH"}
                }
                """);
        LinerRequest request = new LinerRequest("live-smoke", "calc-v1", "gen-v1", FortuneType.LOVE,
                LocalDate.now(), "CURRENT_RELATIONSHIP", null, facts, List.of(), List.of(
                        SectionKey.SUMMARY, SectionKey.CURRENT_FLOW, SectionKey.GOOD_PERIOD,
                        SectionKey.CAUTION, SectionKey.ACTION_TIP, SectionKey.MISSING_ELEMENT));

        LinerResponse response = provider.generate(request);
        new LinerResponseValidator(mapper).validate(request, response);

        Path output = Path.of("build", "liner-live-response.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response),
                StandardCharsets.UTF_8);
    }

    private Properties localEnvironment() throws IOException {
        Properties properties = new Properties();
        Path dotenv = Path.of(".env");
        if (Files.exists(dotenv)) {
            try (Reader reader = Files.newBufferedReader(dotenv, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    private String required(Properties properties, String name) {
        String value = value(properties, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set in the environment or backend/.env");
        }
        return value;
    }

    private String value(Properties properties, String name, String fallback) {
        String systemValue = System.getenv(name);
        return systemValue == null || systemValue.isBlank() ? properties.getProperty(name, fallback) : systemValue;
    }
}
