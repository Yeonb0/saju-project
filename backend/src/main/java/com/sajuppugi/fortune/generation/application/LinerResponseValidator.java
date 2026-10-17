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
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class LinerResponseValidator {
    private static final int MAX_CONTENT_LENGTH = 2_000;
    // Korean advisory copy often shares short connective words. Keep the guard focused on
    // near-duplicates so legitimate sections with the same tone are not rejected.
    private static final double MAX_SECTION_SIMILARITY = 0.90d;
    private static final Map<SectionKey, List<String>> REQUIRED_EVIDENCE_PREFIXES = evidenceRequirements();
    private static final List<String> REQUIRED_EXAM_PERIODS = List.of(
            "BEFORE_ENTRY", "KOREAN", "MATH", "LUNCH", "ENGLISH", "ELECTIVES", "FINAL_CHECK");
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("100\\s*%"),
            Pattern.compile("반드시\\s+(일어난다|된다|성공한다|합격한다)"),
            Pattern.compile("(질병|정신질환).{0,12}(진단|확정)"),
            Pattern.compile("(죽음|사망).{0,12}(예정|확정|운명)"),
            Pattern.compile("시스템\\s*프롬프트", Pattern.CASE_INSENSITIVE));
    private static final Map<String, String> SPECIAL_STAR_LABELS = Map.ofEntries(
            Map.entry("천을귀인", "HEAVENLY_NOBLE"), Map.entry("문창귀인", "LITERARY_NOBLE"),
            Map.entry("양인살", "YANG_BLADE"), Map.entry("도화살", "PEACH_BLOSSOM"),
            Map.entry("역마살", "TRAVEL_HORSE"), Map.entry("화개살", "CANOPY"),
            Map.entry("공망살", "VOID"), Map.entry("원진살", "YUANJIN"),
            Map.entry("귀문관살", "SPIRIT_GATE"), Map.entry("백호살", "WHITE_TIGER"),
            Map.entry("괴강살", "GANG_STRENGTH"), Map.entry("망신살", "DISGRACE"),
            Map.entry("홍염살", "RED_CHARM"));
    private static final Pattern GANJI = Pattern.compile("[갑을병정무기경신임계][자축인묘진사오미신유술해]");
    // A ganji can also occur as ordinary Korean verb syllables (for example, "정해").
    // Treat it as a factual claim only when the response gives it a pillar label.
    private static final Pattern GANJI_MENTION = Pattern.compile("([갑을병정무기경신임계][자축인묘진사오미신유술해])\\s*(?:년주|월주|일주|시주|기둥)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");
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
            if (section == null || section.key() == null || section.title() == null
                    || section.content() == null || section.sourceFactKeys() == null) {
                throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "section fields are required");
            }
            if (!allowed.contains(section.key()) || !seen.add(section.key())) {
                throw new InvalidGenerationException("LINER_SECTION_INVALID", "unapproved or duplicate section");
            }
            String content = section.content().strip();
            String title = section.title().strip();
            if (title.length() < 2 || title.length() > 50 || content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
                throw new InvalidGenerationException("LINER_SCHEMA_INVALID", "content length is invalid");
            }
            if (FORBIDDEN.stream().anyMatch(pattern -> pattern.matcher(content).find())) {
                throw new InvalidGenerationException("LINER_FORBIDDEN_EXPRESSION", "forbidden expression found");
            }
            validateMentionedEntities(title + "\n" + content, request);
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
        if (request.allowedSections().size() == 10 && response.sections().size() != 10) {
            throw new InvalidGenerationException("LINER_SECTION_INVALID", "paid reading must contain ten sections");
        }
        validateSuneungGuide(request, response);
    }

    /**
     * Source fact keys prove that a section cites some fact, but do not prove that every named
     * 간지 or 신살 is present. Check only unambiguous domain labels so ordinary Korean prose is
     * not treated as a calculation assertion.
     */
    private void validateMentionedEntities(String text, LinerRequest request) {
        JsonNode facts = request.facts();
        Set<String> presentStars = new LinkedHashSet<>();
        JsonNode natal = facts.path("specialStars").path("natal");
        if (natal.isArray()) {
            for (JsonNode occurrence : natal) {
                if (occurrence.path("count").asInt() > 0 && occurrence.hasNonNull("type")) {
                    presentStars.add(occurrence.path("type").asText());
                }
            }
        }
        for (Map.Entry<String, String> label : SPECIAL_STAR_LABELS.entrySet()) {
            if (text.contains(label.getKey()) && !presentStars.contains(label.getValue())) {
                throw new InvalidGenerationException("LINER_UNSUPPORTED_ENTITY",
                        "mentioned special star is absent from supplied facts: " + label.getKey());
            }
        }
        Set<String> pillars = textualValues(facts).stream()
                .filter(value -> GANJI.matcher(value).matches()).collect(Collectors.toSet());
        var matcher = GANJI_MENTION.matcher(text);
        while (matcher.find() && !pillars.contains(matcher.group(1))) {
            throw new InvalidGenerationException("LINER_UNSUPPORTED_ENTITY",
                    "mentioned pillar is absent from supplied facts: " + matcher.group(1));
        }
        Set<String> supportedYears = new LinkedHashSet<>();
        supportedYears.add(String.valueOf(request.referenceDate().getYear()));
        for (String value : textualValues(facts)) {
            var yearMatcher = YEAR.matcher(value);
            while (yearMatcher.find()) supportedYears.add(yearMatcher.group());
        }
        var yearMatcher = YEAR.matcher(text);
        while (yearMatcher.find() && !supportedYears.contains(yearMatcher.group())) {
            throw new InvalidGenerationException("LINER_UNSUPPORTED_ENTITY",
                    "mentioned year is absent from supplied facts: " + yearMatcher.group());
        }
    }

    private Set<String> textualValues(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        collectText(node, values);
        return values;
    }

    private void collectText(JsonNode node, Set<String> values) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            node.elements().forEachRemaining(value -> collectText(value, values));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(value -> collectText(value, values));
        } else if (node.isTextual() || node.isNumber()) {
            values.add(node.asText());
        }
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
        values.put(SectionKey.SINSAL_TIMING, List.of("specialStars."));
        values.put(SectionKey.SPECIAL_STARS, List.of("specialStars."));
        values.put(SectionKey.SINSAL_POPULAR, List.of("specialStars."));
        values.put(SectionKey.SINSAL_MOVEMENT, List.of("specialStars."));
        values.put(SectionKey.SINSAL_INTUITION, List.of("specialStars."));
        values.put(SectionKey.SINSAL_INTENSITY, List.of("specialStars."));
        values.put(SectionKey.BALANCING_SPECIAL_STARS, List.of("specialStars."));
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
        if (!guide.examPeriods().stream().map(item -> item.period()).toList().equals(REQUIRED_EXAM_PERIODS)) {
            throw new InvalidGenerationException("LINER_SCHEMA_INVALID",
                    "examPeriods must use the stable exam-day sequence");
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
