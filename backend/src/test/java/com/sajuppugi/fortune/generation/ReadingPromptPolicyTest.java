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

    private LinerRequest request(FortuneType type, String question) {
        return new LinerRequest("a".repeat(64), "calc-v1", "gen-v1", type,
                LocalDate.of(2026, 10, 11), question, null,
                mapper.createObjectNode().putObject("dayMaster").put("hanja", "甲"),
                List.of(), List.of(SectionKey.SUMMARY));
    }
}
