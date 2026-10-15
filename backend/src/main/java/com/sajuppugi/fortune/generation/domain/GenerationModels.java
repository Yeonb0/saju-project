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
        STRENGTHS, TIMING, DAILY_PRACTICE,
        SINSAL_POPULAR, SINSAL_MOVEMENT, SINSAL_INTUITION, SINSAL_INTENSITY, SINSAL_TIMING,
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
            String contentVersion,
            String personalizedConcern) {
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
                    allowedSections, missingFields, generationVersion, contentVersion, null);
        }

        public GenerationCommand(UUID requesterUserId, FortuneType fortuneType, LocalDate referenceDate,
                                 String interestKey, CalculationFacts calculationFacts,
                                 List<SectionKey> allowedSections, List<String> missingFields,
                                 String generationVersion, String contentVersion, String personalizedConcern) {
            this(requesterUserId, fortuneType, referenceDate, interestKey, calculationFacts, null, null,
                    allowedSections, missingFields, generationVersion, contentVersion, personalizedConcern);
        }

        public GenerationCommand(UUID requesterUserId, FortuneType fortuneType, LocalDate referenceDate,
                                 String interestKey, CalculationFacts calculationFacts,
                                 CalculationFacts counterpartCalculationFacts, String relationKey,
                                 List<SectionKey> allowedSections, List<String> missingFields,
                                 String generationVersion, String contentVersion) {
            this(requesterUserId, fortuneType, referenceDate, interestKey, calculationFacts,
                    counterpartCalculationFacts, relationKey, allowedSections, missingFields,
                    generationVersion, contentVersion, null);
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
            String personalizedConcern,
            JsonNode facts,
            List<String> missingFields,
            List<SectionKey> allowedSections,
            List<String> validationFeedback) {
        public LinerRequest {
            missingFields = List.copyOf(missingFields);
            allowedSections = List.copyOf(allowedSections);
            validationFeedback = validationFeedback == null ? List.of() : List.copyOf(validationFeedback);
        }

        public LinerRequest(String generationKey, String calculationVersion, String generationVersion,
                            FortuneType fortuneType, LocalDate referenceDate, String interestKey, String relationKey,
                            JsonNode facts, List<String> missingFields, List<SectionKey> allowedSections) {
            this(generationKey, calculationVersion, generationVersion, fortuneType, referenceDate,
                    interestKey, relationKey, null, facts, missingFields, allowedSections, List.of());
        }

        public LinerRequest(String generationKey, String calculationVersion, String generationVersion,
                            FortuneType fortuneType, LocalDate referenceDate, String interestKey, String relationKey,
                            String personalizedConcern, JsonNode facts, List<String> missingFields,
                            List<SectionKey> allowedSections) {
            this(generationKey, calculationVersion, generationVersion, fortuneType, referenceDate,
                    interestKey, relationKey, personalizedConcern, facts, missingFields, allowedSections, List.of());
        }

        public LinerRequest(String generationKey, String calculationVersion, String generationVersion,
                            FortuneType fortuneType, LocalDate referenceDate, String interestKey,
                            JsonNode facts, List<String> missingFields, List<SectionKey> allowedSections) {
            this(generationKey, calculationVersion, generationVersion, fortuneType, referenceDate,
                    interestKey, null, null, facts, missingFields, allowedSections, List.of());
        }

        public LinerRequest withValidationFeedback(List<String> feedback) {
            return new LinerRequest(generationKey, calculationVersion, generationVersion, fortuneType,
                    referenceDate, interestKey, relationKey, personalizedConcern, facts, missingFields,
                    allowedSections, feedback);
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

    public record GeneratedSection(SectionKey key, String title, String content, List<String> sourceFactKeys) {
        public GeneratedSection {
            if (title == null || title.isBlank()) title = key == null ? "운세 이야기" : defaultTitle(key);
            sourceFactKeys = sourceFactKeys == null ? List.of() : List.copyOf(sourceFactKeys);
        }

        public GeneratedSection(SectionKey key, String content, List<String> sourceFactKeys) {
            this(key, defaultTitle(key), content, sourceFactKeys);
        }

        private static String defaultTitle(SectionKey key) {
            return switch (key) {
                case SUMMARY -> "한눈에 보는 핵심 흐름";
                case CURRENT_FLOW -> "지금 흐름을 읽는 법";
                case GOOD_PERIOD -> "기회를 살릴 시기";
                case CAUTION -> "과해질 때 살필 점";
                case ACTION_TIP -> "바로 해볼 행동";
                case RELATIONSHIPS -> "사람 사이의 리듬";
                case STUDY_AND_WORK -> "공부와 일의 방식";
                case WEALTH_FLOW -> "돈의 흐름과 기준";
                case CONDITION -> "생활 리듬 점검";
                case LUCKY_POINT -> "행운을 쓰는 방식";
                case INCOME -> "수입을 만드는 강점";
                case SPENDING_CAUTION -> "지출 전 확인할 것";
                case MATCH_STRENGTH -> "두 사람의 잘 맞는 지점";
                case MATCH_CONFLICT -> "엇갈릴 때의 신호";
                case COMMUNICATION -> "대화가 풀리는 방식";
                case RELATIONSHIP_TIP -> "관계를 위한 작은 약속";
                case SPECIAL_STARS -> "원국에 들어온 신살";
                case BALANCING_SPECIAL_STARS -> "신살을 다루는 생활법";
                case MISSING_ELEMENT -> "부족한 기운의 보완";
                case STRENGTHS -> "반복해서 드러나는 강점";
                case TIMING -> "대운과 세운의 흐름";
                case DAILY_PRACTICE -> "오늘부터 만드는 습관";
                case SINSAL_POPULAR -> "도화·홍염의 매력";
                case SINSAL_MOVEMENT -> "역마·망신의 활동성";
                case SINSAL_INTUITION -> "화개·귀문의 몰입";
                case SINSAL_INTENSITY -> "강한 신살의 조절법";
                case SINSAL_TIMING -> "신살이 도드라지는 시기";
                case EXAM_DAY -> "시험 당일의 흐름";
                case EXAM_PERIODS -> "교시별 집중 전략";
                case FOCUS -> "집중력을 쓰는 방법";
                case MEAL -> "익숙한 식사 리듬";
                case PREPARATION -> "전날 준비 체크";
                case ANXIETY_MANAGEMENT -> "긴장을 낮추는 순서";
            };
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
