package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.application.LinerFactProjector;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Opt-in paid external check. It sends only a fixed synthetic calculation projection and never
 * prints credentials or raw provider transport data. The persisted output is safe to inspect:
 * it contains no user identity or raw birth input.
 */
@EnabledIfEnvironmentVariable(named = "LINER_LIVE_TEST", matches = "true")
class LinerLiveReadingQualityTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void generatesAndValidatesAnOverallReadingFromSyntheticFacts() throws Exception {
        Properties environment = localEnvironment();
        var properties = new LinerApiProperties(
                value(environment, "LINER_BASE_URL", "https://platform.liner.com/api/v1"),
                required(environment, "LINER_API_KEY"), value(environment, "LINER_MODEL", "liner-mark"),
                Duration.parse("PT" + value(environment, "LINER_CONNECT_TIMEOUT_SECONDS", "3") + "S"),
                Duration.parse("PT" + value(environment, "LINER_READ_TIMEOUT_SECONDS", "30") + "S"),
                Integer.parseInt(value(environment, "LINER_MAX_OUTPUT_TOKENS", "8000")),
                value(environment, "LINER_REASONING_EFFORT", "low"));
        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2004, 3, 15),
                LocalTime.of(14, 32), false, BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE),
                CalculationPolicy.CURRENT);
        var projectedFacts = new LinerFactProjector(mapper).project(facts);
        // One section keeps this paid smoke check bounded; production's ten-section contract is
        // covered by the deterministic schema/validator tests and must be exercised separately.
        var sections = List.of(SectionKey.SUMMARY);
        var request = new LinerRequest("live-synthetic-overall", facts.meta().calculationVersion(),
                "live-quality-v1", FortuneType.OVERALL, LocalDate.of(2026, 10, 16),
                "OVERALL_FLOW", projectedFacts, List.of(), sections);
        var provider = new LinerApiProvider(RestClient.builder(), mapper, properties);
        var response = provider.generate(request);

        writeSafeOutput(response);
        assertThatCode(() -> new LinerResponseValidator(mapper).validate(request, response)).doesNotThrowAnyException();
    }

    private void writeSafeOutput(Object value) throws IOException {
        Path output = Path.of("build", "liner-live-synthetic-reading.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
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
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured locally");
        return value;
    }

    private String value(Properties properties, String name, String fallback) {
        String systemValue = System.getenv(name);
        return systemValue == null || systemValue.isBlank() ? properties.getProperty(name, fallback) : systemValue;
    }
}
