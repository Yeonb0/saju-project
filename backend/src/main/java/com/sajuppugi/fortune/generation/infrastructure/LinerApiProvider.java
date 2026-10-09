package com.sajuppugi.fortune.generation.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.liner.adapter", havingValue = "api")
public class LinerApiProvider implements LinerProvider {
    private static final String RESPONSE_PATH = "/responses";
    private final RestClient client;
    private final ObjectMapper mapper;
    private final LinerApiProperties properties;
    private final JsonNode responseSchema;

    public LinerApiProvider(RestClient.Builder builder, ObjectMapper mapper, LinerApiProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
        validateConfiguration(properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = builder.baseUrl(withoutTrailingSlash(properties.baseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.responseSchema = readSchema(mapper);
    }

    @Override
    public String name() {
        return "liner-responses";
    }

    @Override
    public LinerResponse generate(LinerRequest request) {
        ObjectNode payload = payload(request);
        try {
            JsonNode response = client.post().uri(RESPONSE_PATH).body(payload).retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        throw new LinerApiException("Liner returned HTTP " + httpResponse.getStatusCode().value());
                    })
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (LinerApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LinerApiException("Liner request failed", exception);
        }
    }

    ObjectNode payload(LinerRequest request) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", properties.model());
        payload.put("instructions", instructions(request.fortuneType()));
        payload.put("input", "다음 입력 JSON에만 근거해 결과 JSON을 생성하세요.\n" + input(request));
        payload.put("max_output_tokens", properties.maxOutputTokens());
        payload.putObject("reasoning").put("effort", properties.reasoningEffort());

        ObjectNode format = payload.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "sajuppugi_reading");
        format.put("strict", true);
        format.set("schema", tailoredSchema(request));
        return payload;
    }

    private ObjectNode input(LinerRequest request) {
        ObjectNode input = mapper.createObjectNode();
        input.put("fortuneType", request.fortuneType().name());
        input.put("referenceDate", request.referenceDate().toString());
        if (request.interestKey() != null) input.put("questionKey", request.interestKey());
        if (request.relationKey() != null) input.put("relationType", request.relationKey());
        input.set("facts", request.facts());
        input.set("missingFields", mapper.valueToTree(request.missingFields()));
        input.set("allowedSections", mapper.valueToTree(request.allowedSections()));
        input.set("allowedSourceFactKeys", mapper.valueToTree(factPaths(request.facts())));
        return input;
    }

    private JsonNode tailoredSchema(LinerRequest request) {
        ObjectNode schema = responseSchema.deepCopy();
        removeUnsupportedSchemaKeywords(schema);
        ArrayNode allowed = mapper.createArrayNode();
        request.allowedSections().forEach(section -> allowed.add(section.name()));
        ((ObjectNode) schema.at("/properties/sections/items/properties/key")).set("enum", allowed.deepCopy());
        ((ObjectNode) schema.at("/properties/omittedSections/items")).set("enum", allowed.deepCopy());
        return schema;
    }

    private void removeUnsupportedSchemaKeywords(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove(List.of("$schema", "$id", "title", "minItems", "minLength", "maxLength", "uniqueItems"));
            object.elements().forEachRemaining(this::removeUnsupportedSchemaKeywords);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::removeUnsupportedSchemaKeywords);
        }
    }

    private LinerResponse parseResponse(JsonNode response) {
        if (response == null || !"completed".equals(response.path("status").asText())) {
            throw new LinerApiException("Liner response was not completed");
        }
        JsonNode output = response.path("output");
        if (!output.isArray()) throw new LinerApiException("Liner response output is missing");
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) continue;
                String text = content.path("text").asText(null);
                if (!StringUtils.hasText(text)) continue;
                try {
                    return mapper.readValue(text, LinerResponse.class);
                } catch (IOException exception) {
                    throw new LinerApiException("Liner output is not valid JSON", exception);
                }
            }
        }
        throw new LinerApiException("Liner output text is missing");
    }

    private String instructions(FortuneType type) {
        return """
                당신은 뿌기사주의 한국어 운세 해석 작성기입니다.
                입력 facts는 서버의 결정론적 만세력 계산 결과이며, 입력에 없는 명리 정보는 계산·추측·보완하지 마세요.
                원본 생년월일시나 사용자의 신원을 추측하지 마세요.
                allowedSections에 있는 키만 반환하고, 근거가 부족한 섹션은 omittedSections에 넣으세요.
                각 문장은 반드시 allowedSourceFactKeys의 실제 scalar 경로를 sourceFactKeys로 인용하세요.
                결과·합격·수익·질병·죽음을 확정하거나 공포를 조장하지 말고 재미와 자기성찰을 위한 참고 정보로 작성하세요.
                재물 내용은 투자 권유가 아니며, 건강 내용은 의학적 진단이 아니라고 명확히 표현하세요.
                출력은 제공된 JSON Schema만 따르고 Markdown 코드 펜스나 추가 설명을 쓰지 마세요.
                운세별 문체 지침: %s
                """.formatted(tone(type));
    }

    private String tone(FortuneType type) {
        return switch (type) {
            case OVERALL -> "관계·학업/일·재물·컨디션을 균형 있게 연결하고 실행 가능한 작은 행동으로 마무리합니다.";
            case LOVE -> "관계 상태를 단정하지 않고 감정 확인, 대화, 경계 존중을 중심으로 따뜻하게 씁니다.";
            case WEALTH -> "현실의 수입·고정비·계약 조건을 우선하며 절제된 재정 습관을 제안합니다.";
            case COMPATIBILITY -> "두 사람을 우열로 비교하지 않고 공통점·차이·대화 방법을 양쪽 facts에 근거해 씁니다.";
            case SINSAL -> "신살을 사건 예언이나 낙인으로 쓰지 않고 장점과 생활 속 다스림의 관점으로 설명합니다.";
            case SUNEUNG -> "합격을 예측하지 않고 시험 당일 준비·집중·긴장 관리에 도움이 되는 현실적 루틴을 제안합니다.";
        };
    }

    private Set<String> factPaths(JsonNode facts) {
        Set<String> paths = new LinkedHashSet<>();
        collect(facts, "", paths);
        return paths;
    }

    private void collect(JsonNode node, String path, Set<String> paths) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collect(entry.getValue(),
                    path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), paths));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) collect(node.get(index), path + "[" + index + "]", paths);
        } else if (!path.isEmpty()) {
            paths.add(path);
        }
    }

    private static JsonNode readSchema(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource("liner/liner-response.schema.json").getInputStream()) {
            return mapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Liner response schema", exception);
        }
    }

    private static void validateConfiguration(LinerApiProperties properties) {
        if (!StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.apiKey())
                || !StringUtils.hasText(properties.model()) || properties.connectTimeout() == null
                || properties.readTimeout() == null || properties.connectTimeout().isNegative()
                || properties.connectTimeout().isZero() || properties.readTimeout().isNegative()
                || properties.readTimeout().isZero() || properties.maxOutputTokens() < 1
                || !Set.of("none", "low", "medium", "high", "max").contains(properties.reasoningEffort())) {
            throw new IllegalStateException("Liner API configuration is incomplete");
        }
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static class LinerApiException extends RuntimeException {
        public LinerApiException(String message) { super(message); }
        public LinerApiException(String message, Throwable cause) { super(message, cause); }
    }
}
