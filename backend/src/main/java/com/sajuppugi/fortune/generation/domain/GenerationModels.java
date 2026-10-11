package com.sajuppugi.fortune.generation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GenerationModels {
    private GenerationModels() {}

    public enum FortuneType { OVERALL, LOVE, WEALTH, COMPATIBILITY, SINSAL, SUNEUNG }

    public enum GenerationMode { LINER, FALLBACK }

    public enum SectionKey {
        SUMMARY, CURRENT_FLOW, GOOD_PERIOD, CAUTION, ACTION_TIP,
        RELATIONSHIPS, STUDY_AND_WORK, WEALTH_FLOW, CONDITION, LUCKY_POINT,
        INCOME, SPENDING_CAUTION, MATCH_STRENGTH, MATCH_CONFLICT, COMMUNICATION, RELATIONSHIP_TIP,
        SPECIAL_STARS, BALANCING_SPECIAL_STARS, MISSING_ELEMENT,
        EXAM_DAY, EXAM_PERIODS, FOCUS, MEAL, PREPARATION, ANXIETY_MANAGEMENT
    }

    public record GenerationCommand(
            UUID requesterUserId,
            FortuneType fortuneType,
            LocalDate referenceDate,
            String interestKey,
            CalculationFacts calculationFacts,
            CalculationFacts counterpartCalculationFacts,
            String relationKey,
            List<SectionKey> allowedSections,
            List<String> missingFields,
            String generationVersion,
            String contentVersion) {
        public GenerationCommand {
            Objects.requireNonNull(requesterUserId, "requesterUserId");
            Objects.requireNonNull(fortuneType, "fortuneType");
            Objects.requireNonNull(referenceDate, "referenceDate");
            Objects.requireNonNull(calculationFacts, "calculationFacts");
            Objects.requireNonNull(generationVersion, "generationVersion");
            Objects.requireNonNull(contentVersion, "contentVersion");
            allowedSections = List.copyOf(allowedSections);
            missingFields = List.copyOf(missingFields);
        }

        public GenerationCommand(UUID requesterUserId, FortuneType fortuneType, LocalDate referenceDate,
                                 String interestKey, CalculationFacts calculationFacts,
                                 List<SectionKey> allowedSections, List<String> missingFields,
                                 String generationVersion, String contentVersion) {
            this(requesterUserId, fortuneType, referenceDate, interestKey, calculationFacts, null, null,
                    allowedSections, missingFields, generationVersion, contentVersion);
        }
    }

    public record LinerRequest(
            String generationKey,
            String calculationVersion,
            String generationVersion,
            FortuneType fortuneType,
            LocalDate referenceDate,
            String interestKey,
            String relationKey,
            JsonNode facts,
            List<String> missingFields,
            List<SectionKey> allowedSections) {
        public LinerRequest {
            missingFields = List.copyOf(missingFields);
            allowedSections = List.copyOf(allowedSections);
        }

        public LinerRequest(String generationKey, String calculationVersion, String generationVersion,
                            FortuneType fortuneType, LocalDate referenceDate, String interestKey,
                            JsonNode facts, List<String> missingFields, List<SectionKey> allowedSections) {
            this(generationKey, calculationVersion, generationVersion, fortuneType, referenceDate,
                    interestKey, null, facts, missingFields, allowedSections);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LinerResponse(List<GeneratedSection> sections, List<SectionKey> omittedSections,
                                SuneungGuide suneungGuide) {
        public LinerResponse {
            sections = sections == null ? List.of() : List.copyOf(sections);
            omittedSections = omittedSections == null ? List.of() : List.copyOf(omittedSections);
        }

        public LinerResponse(List<GeneratedSection> sections, List<SectionKey> omittedSections) {
            this(sections, omittedSections, null);
        }
    }

    public record GeneratedSection(SectionKey key, String content, List<String> sourceFactKeys) {
        public GeneratedSection {
            sourceFactKeys = sourceFactKeys == null ? List.of() : List.copyOf(sourceFactKeys);
        }
    }

    public record SuneungGuide(String summary, String examDayGuide, List<ExamPeriodGuide> examPeriods,
                               List<String> focusTips, List<FoodRecommendation> foodRecommendations,
                               List<PreparationItem> preparationChecklist, List<String> anxietyManagement,
                               String missingElementAdvice) {
        public SuneungGuide {
            examPeriods = examPeriods == null ? List.of() : List.copyOf(examPeriods);
            focusTips = focusTips == null ? List.of() : List.copyOf(focusTips);
            foodRecommendations = foodRecommendations == null ? List.of() : List.copyOf(foodRecommendations);
            preparationChecklist = preparationChecklist == null ? List.of() : List.copyOf(preparationChecklist);
            anxietyManagement = anxietyManagement == null ? List.of() : List.copyOf(anxietyManagement);
        }
    }

    public record ExamPeriodGuide(String period, String guidance) {}
    public record FoodRecommendation(String item, String reason) {}
    public record PreparationItem(String id, String label, String category) {}

    public record GeneratedReading(
            UUID resultId,
            String generationKey,
            FortuneType fortuneType,
            LinerResponse response,
            String calculationVersion,
            String generationVersion,
            String contentVersion,
            GenerationMode generationMode,
            boolean reused) {}
}
