package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.generation.infrastructure.LinerApiProperties;
import com.sajuppugi.fortune.generation.infrastructure.LinerApiProvider;
import com.sajuppugi.fortune.generation.infrastructure.LinerApiProvider.LinerApiException;
import com.sajuppugi.fortune.generation.application.SuneungGuideFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LinerApiProviderTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;
    private LinerApiProvider provider;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<JsonNode> capturedBody = new AtomicReference<>();
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile Duration responseDelay = Duration.ZERO;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(mapper.readTree(exchange.getRequestBody()));
            if (!responseDelay.isZero()) {
                try {
                    Thread.sleep(responseDelay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        provider = new LinerApiProvider(RestClient.builder(), mapper, new LinerApiProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                "test-secret-key", "liner-mark",
                Duration.ofSeconds(2), Duration.ofSeconds(10), 3000, "low"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsSafeFactsWithStrictSchemaAndParsesOutputText() throws Exception {
        LinerRequest request = request().withValidationFeedback(List.of("LINER_SECTION_TOO_SIMILAR"));
        String generated = """
                {"sections":[{"key":"SUMMARY","content":"차분히 흐름을 살펴보세요.",
                "sourceFactKeys":["dayMaster.hanja"]}],"omittedSections":["CURRENT_FLOW"]}
                """;
        ObjectNode envelope = mapper.createObjectNode().put("status", "completed");
        ObjectNode message = envelope.putArray("output").addObject();
        message.put("type", "message");
        message.putArray("content").addObject().put("type", "output_text").put("text", generated);

        responseStatus = 200;
        responseBody = mapper.writeValueAsString(envelope);

        LinerResponse response = provider.generate(request);

        assertThat(response.sections()).singleElement().satisfies(section -> {
            assertThat(section.key()).isEqualTo(SectionKey.SUMMARY);
            assertThat(section.sourceFactKeys()).containsExactly("dayMaster.hanja");
        });
        assertThat(response.omittedSections()).containsExactly(SectionKey.CURRENT_FLOW);
        assertThat(authorization.get()).isEqualTo("Bearer test-secret-key");
        assertThat(capturedBody.get().path("model").asText()).isEqualTo("liner-mark");
        assertThat(capturedBody.get().at("/reasoning/effort").asText()).isEqualTo("low");
        assertThat(capturedBody.get().at("/text/format/type").asText()).isEqualTo("json_schema");
        assertThat(capturedBody.get().at("/text/format/strict").asBoolean()).isTrue();
        assertThat(capturedBody.get().at(
                "/text/format/schema/properties/sections/items/properties/key/enum/0").asText()).isEqualTo("SUMMARY");
        assertThat(capturedBody.get().at("/text/format/schema/$schema").isMissingNode()).isTrue();
        assertThat(capturedBody.get().at("/text/format/schema/properties/sections/minItems").isMissingNode()).isTrue();
        assertThat(capturedBody.get().at(
                "/text/format/schema/properties/sections/items/properties/content/maxLength").isMissingNode()).isTrue();
        assertThat(capturedBody.get().at(
                "/text/format/schema/properties/omittedSections/uniqueItems").isMissingNode()).isTrue();
        assertThat(capturedBody.get().path("input").asText())
                .contains("dayMaster", "validationFeedback", "LINER_SECTION_TOO_SIMILAR")
                .doesNotContain("birthDate");
    }

    @Test
    void providerErrorDoesNotExposeRemoteBodyOrApiKey() {
        responseStatus = 429;
        responseBody = "{\"error\":\"provider-private-detail\"}";

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOf(LinerApiException.class)
                .hasMessage("Liner returned HTTP 429")
                .hasMessageNotContaining("provider-private-detail")
                .hasMessageNotContaining("test-secret-key");
    }

    @Test
    void delayedHttpResponseIsBoundedByReadTimeout() {
        responseStatus = 200;
        responseBody = "{}";
        responseDelay = Duration.ofSeconds(2);
        provider = new LinerApiProvider(RestClient.builder(), mapper, new LinerApiProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                "test-secret-key", "liner-mark",
                Duration.ofMillis(100), Duration.ofMillis(100), 3000, "low"));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOf(LinerApiException.class)
                .hasMessage("Liner request failed")
                .hasMessageNotContaining("test-secret-key");
    }

    @Test
    void usesDedicatedStructuredSchemaForSuneung() throws Exception {
        LinerRequest request = suneungRequest();
        var sections = List.of(new GeneratedSection(SectionKey.SUMMARY, "차분히 준비해 보세요.",
                List.of("dayMaster.hanja")));
        LinerResponse generated = new LinerResponse(sections, List.of(), SuneungGuideFactory.fromSections(sections));
        ObjectNode envelope = mapper.createObjectNode().put("status", "completed");
        ObjectNode message = envelope.putArray("output").addObject();
        message.put("type", "message");
        message.putArray("content").addObject().put("type", "output_text")
                .put("text", mapper.writeValueAsString(generated));
        responseStatus = 200;
        responseBody = mapper.writeValueAsString(envelope);

        LinerResponse response = provider.generate(request);

        assertThat(response.suneungGuide()).isNotNull();
        assertThat(capturedBody.get().at(
                "/text/format/schema/properties/sections/items/properties/title/type").asText())
                .isEqualTo("string");
        assertThat(capturedBody.get().at("/text/format/schema/properties/suneungGuide/type").asText())
                .isEqualTo("object");
        assertThat(capturedBody.get().at(
                "/text/format/schema/properties/suneungGuide/properties/preparationChecklist/items/properties/category/enum")
                .size()).isPositive();
    }

    @Test
    void rejectsIncompleteConfigurationBeforeCreatingClient() {
        assertThatThrownBy(() -> new LinerApiProvider(RestClient.builder(), mapper,
                new LinerApiProperties("https://platform.liner.com/api/v1", "", "liner-mark",
                        Duration.ofSeconds(2), Duration.ofSeconds(10), 3000, "low")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Liner API configuration is incomplete");
    }

    private LinerRequest request() {
        JsonNode facts = mapper.createObjectNode()
                .set("dayMaster", mapper.createObjectNode().put("hangul", "갑").put("hanja", "甲"));
        return new LinerRequest("a".repeat(64), "calc-v1", "gen-v1", FortuneType.LOVE,
                LocalDate.of(2026, 10, 8), "CURRENT_RELATIONSHIP", null, facts, List.of(),
                List.of(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW));
    }

    private LinerRequest suneungRequest() {
        JsonNode facts = mapper.createObjectNode()
                .set("dayMaster", mapper.createObjectNode().put("hangul", "갑").put("hanja", "甲"));
        return new LinerRequest("b".repeat(64), "calc-v1", "suneung-v1", FortuneType.SUNEUNG,
                LocalDate.of(2026, 11, 19), "EXAM_FOCUS", null, facts, List.of(),
                List.of(SectionKey.SUMMARY));
    }
}
