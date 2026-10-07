package com.sajuppugi.fortune.generation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class LinerResponseValidator {
    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("100\\s*%"),
            Pattern.compile("반드시\\s+(일어난다|된다|성공한다|합격한다)"),
            Pattern.compile("(질병|정신질환).{0,12}(진단|확정)"),
            Pattern.compile("(죽음|사망).{0,12}(예정|확정|운명)"),
            Pattern.compile("시스템\\s*프롬프트", Pattern.CASE_INSENSITIVE));
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public LinerResponseValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        try {
            this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(new ClassPathResource("liner/liner-response.schema.json").getInputStream());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load Liner response JSON Schema", exception);
        }
    }

    public void validate(LinerRequest request, LinerResponse response) {
        if (response == null) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "response is required");
        }
        var schemaErrors = schema.validate(mapper.valueToTree(response));
        if (!schemaErrors.isEmpty()) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", schemaErrors.toString());
        }
        if (response == null || response.sections() == null || response.omittedSections() == null) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "sections and omittedSections are required");
        }
        Set<SectionKey> allowed = Set.copyOf(request.allowedSections());
        Set<SectionKey> seen = new HashSet<>();
        Set<String> validFactPaths = factPaths(request.facts());
        for (GeneratedSection section : response.sections()) {
            if (section == null || section.key() == null || section.content() == null || section.sourceFactKeys() == null) {
                throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "section fields are required");
            }
            if (!allowed.contains(section.key()) || !seen.add(section.key())) {
                throw new InvalidGenerationException("LINER_SECTION_INVALID", "unapproved or duplicate section");
            }
            String content = section.content().strip();
            if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
                throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "content length is invalid");
            }
            if (FORBIDDEN.stream().anyMatch(pattern -> pattern.matcher(content).find())) {
                throw new InvalidGenerationException("LINER_FORBIDDEN_EXPRESSION", "forbidden expression found");
            }
            if (section.sourceFactKeys().isEmpty()
                    || section.sourceFactKeys().stream().anyMatch(path -> !validFactPaths.contains(path))) {
                throw new InvalidGenerationException("LINER_SOURCE_FACT_INVALID", "sourceFactKeys must reference supplied facts");
            }
        }
        Set<SectionKey> omitted = new HashSet<>(response.omittedSections());
        if (omitted.size() != response.omittedSections().size() || !allowed.containsAll(omitted)) {
            throw new InvalidGenerationException("LINER_SECTION_INVALID", "omittedSections are invalid");
        }
        Set<SectionKey> accounted = new HashSet<>(seen);
        accounted.addAll(omitted);
        if (!accounted.equals(allowed)) {
            throw new InvalidGenerationException("LINER_SECTION_INVALID", "every allowed section must be returned or omitted");
        }
    }

    Set<String> factPaths(JsonNode facts) {
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

    public static class InvalidGenerationException extends RuntimeException {
        private final String code;

        public InvalidGenerationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
