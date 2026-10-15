package com.sajuppugi.fortune.reading.domain;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for public general-reading inputs and generated output shape.
 * Keys are deliberately stable API identifiers rather than display copy.
 */
public final class GeneralReadingPolicy {
    public static final String GENERATION_VERSION = "liner-general-reading-v4";
    private static final Map<FortuneType, Definition> DEFINITIONS = definitions();

    private GeneralReadingPolicy() {}

    public static Definition require(FortuneType type) {
        Definition definition = DEFINITIONS.get(type);
        if (definition == null) throw new ApiException(ErrorCode.INVALID_FORTUNE_TYPE);
        return definition;
    }

    public static String productCode(FortuneType type, ProductOption option) {
        require(type);
        return type.name() + "_" + option.name();
    }

    public static void validateQuestion(FortuneType type, String questionKey) {
        if (questionKey == null || !require(type).questionKeys().contains(questionKey)) {
            throw new ApiException(ErrorCode.INVALID_QUESTION_KEY);
        }
    }

    private static Map<FortuneType, Definition> definitions() {
        Map<FortuneType, Definition> values = new EnumMap<>(FortuneType.class);
        values.put(FortuneType.OVERALL, new Definition(
                List.of(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW, SectionKey.RELATIONSHIPS,
                        SectionKey.STUDY_AND_WORK, SectionKey.WEALTH_FLOW, SectionKey.CONDITION,
                        SectionKey.LUCKY_POINT, SectionKey.MISSING_ELEMENT, SectionKey.STRENGTHS,
                        SectionKey.TIMING),
                Set.of("OVERALL_FLOW", "RELATIONSHIPS", "STUDY_AND_WORK", "WEALTH", "CONDITION"),
                "overall-2026.10.15-v2"));
        values.put(FortuneType.LOVE, new Definition(
                List.of(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW, SectionKey.GOOD_PERIOD,
                        SectionKey.CAUTION, SectionKey.ACTION_TIP, SectionKey.RELATIONSHIPS,
                        SectionKey.DAILY_PRACTICE, SectionKey.STRENGTHS, SectionKey.TIMING,
                        SectionKey.MISSING_ELEMENT),
                Set.of("CURRENT_RELATIONSHIP", "NEW_RELATIONSHIP", "RECONCILIATION", "MARRIAGE"),
                "love-2026.10.15-v2"));
        values.put(FortuneType.WEALTH, new Definition(
                List.of(SectionKey.SUMMARY, SectionKey.WEALTH_FLOW, SectionKey.INCOME,
                        SectionKey.SPENDING_CAUTION, SectionKey.GOOD_PERIOD, SectionKey.ACTION_TIP,
                        SectionKey.CAUTION, SectionKey.TIMING, SectionKey.DAILY_PRACTICE,
                        SectionKey.MISSING_ELEMENT),
                Set.of("OVERALL_WEALTH", "INCOME", "SPENDING", "INVESTMENT", "CAREER_FINANCE"),
                "wealth-2026.10.15-v2"));
        values.put(FortuneType.COMPATIBILITY, new Definition(
                List.of(SectionKey.SUMMARY, SectionKey.MATCH_STRENGTH, SectionKey.MATCH_CONFLICT,
                        SectionKey.COMMUNICATION, SectionKey.RELATIONSHIP_TIP, SectionKey.CURRENT_FLOW,
                        SectionKey.CAUTION, SectionKey.TIMING, SectionKey.DAILY_PRACTICE,
                        SectionKey.MISSING_ELEMENT),
                Set.of("OVERALL_MATCH", "COMMUNICATION", "CONFLICT", "LONG_TERM_POTENTIAL"),
                "compatibility-2026.10.15-v2"));
        values.put(FortuneType.SINSAL, new Definition(
                List.of(SectionKey.SUMMARY, SectionKey.SPECIAL_STARS, SectionKey.SINSAL_POPULAR,
                        SectionKey.SINSAL_MOVEMENT, SectionKey.SINSAL_INTUITION, SectionKey.SINSAL_INTENSITY,
                        SectionKey.SINSAL_TIMING, SectionKey.BALANCING_SPECIAL_STARS,
                        SectionKey.MISSING_ELEMENT, SectionKey.ACTION_TIP),
                Set.of("OVERALL_SINSAL", "RELATIONSHIPS", "WORK_AND_STUDY", "WEALTH"),
                "sinsal-2026.10.15-v2"));
        return Map.copyOf(values);
    }

    public record Definition(List<SectionKey> allowedSections, Set<String> questionKeys, String contentVersion) {
        public Definition {
            allowedSections = List.copyOf(allowedSections);
            questionKeys = Set.copyOf(questionKeys);
        }
    }
}
