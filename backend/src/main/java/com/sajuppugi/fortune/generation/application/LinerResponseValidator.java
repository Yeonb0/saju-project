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
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class LinerResponseValidator {
    private static final int MAX_CONTENT_LENGTH = 2_000;
    // Korean advisory copy often shares short connective words. Keep the guard focused on
    // near-duplicates so legitimate sections with the same tone are not rejected.
    private static final double MAX_SECTION_SIMILARITY = 0.90d;
    private static final Map<SectionKey, List<String>> REQUIRED_EVIDENCE_PREFIXES = evidenceRequirements();
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("100\\s*%"),
            Pattern.compile("반드시\\s+(일어난다|된다|성공한다|합격한다)"),
            Pattern.compile("(질병|정신질환).{0,12}(진단|확정)"),
            Pattern.compile("(죽음|사망).{0,12}(예정|확정|운명)"),
            Pattern.compile("시스템\\s*프롬프트", Pattern.CASE_INSENSITIVE));
    private final ObjectMapper mapper;
    private final JsonSchema schema;
    private final JsonSchema suneungSchema;

    public LinerResponseValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        try {
            this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(new ClassPathResource("liner/liner-response.schema.json").getInputStream());
            this.suneungSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(new ClassPathResource("liner/suneung-response.schema.json").getInputStream());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load Liner response JSON Schema", exception);
        }
    }

    public void validate(LinerRequest request, LinerResponse response) {
        if (response == null) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "response is required");
        }
        var selectedSchema = request.fortuneType()
                == com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType.SUNEUNG
                ? suneungSchema : schema;
        var schemaErrors = selectedSchema.validate(mapper.valueToTree(response));
        if (!schemaErrors.isEmpty()) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", schemaErrors.toString());
        }
        if (response == null || response.sections() == null || response.omittedSections() == null) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "sections and omittedSections are required");
        }
        Set<SectionKey> allowed = Set.copyOf(request.allowedSections());
        Set<SectionKey> seen = new HashSet<>();
        List<String> seenContents = new java.util.ArrayList<>();
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
            validateEvidenceFit(section);
            for (String previous : seenContents) {
                if (similarity(previous, content) > MAX_SECTION_SIMILARITY) {
                    throw new InvalidGenerationException("LINER_SECTION_TOO_SIMILAR",
                            "sections must provide distinct information");
                }
            }
            seenContents.add(content);
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
        Set<SectionKey> required = new HashSet<>();
        if (allowed.contains(SectionKey.SUMMARY)) required.add(SectionKey.SUMMARY);
        if (allowed.contains(SectionKey.MISSING_ELEMENT)) required.add(SectionKey.MISSING_ELEMENT);
        if (seen.isEmpty() || !seen.containsAll(required)) {
            throw new InvalidGenerationException("LINER_SECTION_INVALID", "required sections cannot be omitted");
        }
        validateSuneungGuide(request, response);
    }

    private void validateEvidenceFit(GeneratedSection section) {
        List<String> prefixes = REQUIRED_EVIDENCE_PREFIXES.get(section.key());
        if (prefixes == null) return;
        boolean matched = section.sourceFactKeys().stream()
                .anyMatch(path -> prefixes.stream().anyMatch(path::startsWith));
        if (!matched) {
            throw new InvalidGenerationException("LINER_SECTION_EVIDENCE_MISMATCH",
                    "section evidence does not match its purpose");
        }
    }

    private double similarity(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String content) {
        String normalized = content.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^가-힣a-z0-9]+", " ").strip();
        if (normalized.isEmpty()) return Set.of();
        return new HashSet<>(java.util.Arrays.asList(normalized.split("\\s+")));
    }

    private static Map<SectionKey, List<String>> evidenceRequirements() {
        Map<SectionKey, List<String>> values = new EnumMap<>(SectionKey.class);
        values.put(SectionKey.MISSING_ELEMENT, List.of("fiveElements."));
        values.put(SectionKey.GOOD_PERIOD, List.of("luck."));
        values.put(SectionKey.MATCH_STRENGTH, List.of("counterpart."));
        values.put(SectionKey.MATCH_CONFLICT, List.of("counterpart."));
        values.put(SectionKey.COMMUNICATION, List.of("counterpart."));
        values.put(SectionKey.RELATIONSHIP_TIP, List.of("counterpart."));
        return Map.copyOf(values);
    }

    private void validateSuneungGuide(LinerRequest request, LinerResponse response) {
        boolean suneung = request.fortuneType()
                == com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType.SUNEUNG;
        if (!suneung && response.suneungGuide() != null) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "suneungGuide is only allowed for SUNEUNG");
        }
        if (!suneung) return;
        var guide = response.suneungGuide();
        if (guide == null || blank(guide.summary()) || blank(guide.examDayGuide())
                || blank(guide.missingElementAdvice()) || guide.examPeriods().isEmpty()
                || guide.focusTips().isEmpty() || guide.foodRecommendations().isEmpty()
                || guide.preparationChecklist().isEmpty() || guide.anxietyManagement().isEmpty()) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "complete suneungGuide is required");
        }
        if (guide.examPeriods().stream().anyMatch(item -> item == null || blank(item.period()) || blank(item.guidance()))
                || guide.foodRecommendations().stream().anyMatch(item -> item == null || blank(item.item()) || blank(item.reason()))
                || guide.preparationChecklist().stream().anyMatch(item -> item == null || blank(item.id())
                        || blank(item.label()) || blank(item.category()))) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "suneungGuide items are invalid");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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
