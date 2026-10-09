package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Opt-in connectivity check for the real Liner API. It is skipped during normal test runs.
 */
@EnabledIfEnvironmentVariable(named = "LINER_LIVE_TEST", matches = "true")
class LinerLiveSmokeTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void receivesChatCompletionFromRealApi() throws Exception {
        Properties environment = localEnvironment();
        RestClient client = RestClient.builder()
                .baseUrl(value(environment, "LINER_BASE_URL", "https://platform.liner.com/api/v1"))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + required(environment, "LINER_API_KEY"))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", value(environment, "LINER_MODEL", "liner-mark"));
        payload.putArray("messages").addObject()
                .put("role", "user")
                .put("content", "시맨틱 검색을 세 문장으로 설명해 주세요.");
        payload.put("max_completion_tokens", 1_024);

        JsonNode response = client.post().uri("/chat/completions")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        assertThat(response).isNotNull();
        assertThat(response.path("object").asText()).isEqualTo("chat.completion");
        assertThat(response.path("model").asText()).startsWith("liner-mark");
        assertThat(response.at("/choices/0/message/content").asText()).isNotBlank();

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
