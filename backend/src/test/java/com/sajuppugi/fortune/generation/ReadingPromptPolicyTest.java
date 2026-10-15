package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.application.ReadingPromptPolicy;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.domain.GeneralReadingPolicy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingPromptPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everySupportedQuestionHasSpecificGroundedInstructions() {
        for (FortuneType type : List.of(FortuneType.OVERALL, FortuneType.LOVE, FortuneType.WEALTH,
                FortuneType.COMPATIBILITY, FortuneType.SINSAL)) {
            for (String question : GeneralReadingPolicy.require(type).questionKeys()) {
                String instructions = ReadingPromptPolicy.instructions(request(type, question));
                assertThat(instructions).contains("사용자가 선택한 질문 목표:", "행동 제안", "입력 facts")
                        .doesNotContain("선택한 질문에 직접 답하고");
            }
        }
    }

    @Test
    void distinguishesHighRiskQuestionsAndSuneung() {
        assertThat(ReadingPromptPolicy.instructions(request(FortuneType.WEALTH, "INVESTMENT")))
                .contains("종목·수익률·매수 시점");
        assertThat(ReadingPromptPolicy.instructions(request(FortuneType.LOVE, "RECONCILIATION")))
                .contains("재회를 예언하지 말고", "중단 기준");
        assertThat(ReadingPromptPolicy.instructions(request(FortuneType.SUNEUNG, "EXAM_FOCUS")))
                .contains("합격을 예측하지 않고", "교시별 집중");
    }

    @Test
    void requestsEasyConversationalAndConcreteCopyWithoutExtraOutputLength() {
        String instructions = ReadingPromptPolicy.instructions(request(FortuneType.OVERALL, "OVERALL_FLOW"));

        assertThat(instructions)
                .contains("존댓말 구어체", "한 줄 결론", "실제 생활에서 보일 장면", "5~8문장")
                .contains("SUMMARY: 가장 두드러진 facts 두 가지")
                .contains("분량을 채우기 위한 반복은 하지 않습니다")
                .doesNotContain("본문은 공백 포함 약 1000자");
    }

    @Test
    void givesSinsalSectionsDistinctEntertainingRoles() {
        LinerRequest request = new LinerRequest("b".repeat(64), "calc-v1", "gen-v1", FortuneType.SINSAL,
                LocalDate.of(2026, 10, 15), "OVERALL_SINSAL", null,
                mapper.createObjectNode().putObject("specialStars").put("rulesVersion", "sinsal-v1"),
                List.of(), List.of(SectionKey.SINSAL_POPULAR, SectionKey.SINSAL_MOVEMENT,
                SectionKey.SINSAL_INTENSITY, SectionKey.SINSAL_TIMING, SectionKey.ACTION_TIP));

        assertThat(ReadingPromptPolicy.instructions(request))
                .contains("SINSAL_POPULAR: 도화·홍염", "SINSAL_MOVEMENT: 역마·망신")
                .contains("SINSAL_INTENSITY: 양인·백호·괴강", "대운 구간과 세운 연도")
                .contains("가벼운 비유나 재치 있는 표현");
    }

    private LinerRequest request(FortuneType type, String question) {
        return new LinerRequest("a".repeat(64), "calc-v1", "gen-v1", type,
                LocalDate.of(2026, 10, 11), question, null,
                mapper.createObjectNode().putObject("dayMaster").put("hanja", "甲"),
                List.of(), List.of(SectionKey.SUMMARY));
    }
}
