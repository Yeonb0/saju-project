package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.domain.GeneralReadingPolicy;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import org.junit.jupiter.api.Test;

class GeneralReadingPolicyTest {
    @Test
    void definesOrderedSectionsAndQuestionKeysForEveryGeneralFortune() {
        assertThat(GeneralReadingPolicy.require(FortuneType.OVERALL).allowedSections())
                .containsExactly(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW, SectionKey.RELATIONSHIPS,
                        SectionKey.STUDY_AND_WORK, SectionKey.WEALTH_FLOW, SectionKey.CONDITION,
                        SectionKey.LUCKY_POINT, SectionKey.MISSING_ELEMENT, SectionKey.STRENGTHS,
                        SectionKey.TIMING);
        assertThat(GeneralReadingPolicy.require(FortuneType.LOVE).questionKeys())
                .contains("CURRENT_RELATIONSHIP", "NEW_RELATIONSHIP", "RECONCILIATION", "MARRIAGE");
        assertThat(GeneralReadingPolicy.require(FortuneType.WEALTH).allowedSections())
                .contains(SectionKey.INCOME, SectionKey.SPENDING_CAUTION, SectionKey.GOOD_PERIOD);
        assertThat(GeneralReadingPolicy.require(FortuneType.COMPATIBILITY).allowedSections())
                .containsExactly(SectionKey.SUMMARY, SectionKey.MATCH_STRENGTH, SectionKey.MATCH_CONFLICT,
                        SectionKey.COMMUNICATION, SectionKey.RELATIONSHIP_TIP, SectionKey.CURRENT_FLOW,
                        SectionKey.CAUTION, SectionKey.TIMING, SectionKey.DAILY_PRACTICE,
                        SectionKey.MISSING_ELEMENT);
        assertThat(GeneralReadingPolicy.require(FortuneType.SINSAL).allowedSections())
                .containsExactly(SectionKey.SUMMARY, SectionKey.SPECIAL_STARS, SectionKey.SINSAL_POPULAR,
                        SectionKey.SINSAL_MOVEMENT, SectionKey.SINSAL_INTUITION, SectionKey.SINSAL_INTENSITY,
                        SectionKey.SINSAL_TIMING, SectionKey.BALANCING_SPECIAL_STARS,
                        SectionKey.MISSING_ELEMENT, SectionKey.ACTION_TIP);
    }

    @Test
    void rejectsEventFortuneAndCrossFortuneQuestion() {
        assertThatThrownBy(() -> GeneralReadingPolicy.require(FortuneType.SUNEUNG))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_FORTUNE_TYPE));
        assertThatThrownBy(() -> GeneralReadingPolicy.validateQuestion(FortuneType.LOVE, "INVESTMENT"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_QUESTION_KEY));
        assertThat(GeneralReadingPolicy.productCode(FortuneType.LOVE, ProductOption.READING_ONLY))
                .isEqualTo("LOVE_READING_ONLY");
    }
}
