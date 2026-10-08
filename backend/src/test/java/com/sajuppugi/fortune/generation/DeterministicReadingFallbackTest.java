package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.application.DeterministicReadingFallback;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicReadingFallbackTest {
    private static final List<SectionKey> SUNEUNG_SECTIONS = List.of(
            SectionKey.SUMMARY, SectionKey.EXAM_DAY, SectionKey.EXAM_PERIODS, SectionKey.FOCUS,
            SectionKey.MEAL, SectionKey.PREPARATION, SectionKey.ANXIETY_MANAGEMENT, SectionKey.MISSING_ELEMENT);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void rendersDetailedGroundedSuneungResponseForGapWoodProfile() throws Exception {
        JsonNode facts = mapper.readTree("""
                {
                  "dayMaster": {"hangul": "갑", "element": "WOOD"},
                  "fiveElements": {
                    "counts": {"WOOD": 3, "FIRE": 2, "EARTH": 1, "METAL": 1, "WATER": 1}
                  }
                }
                """);
        LinerRequest request = new LinerRequest("key", "calc-v1", "gen-v1", FortuneType.SUNEUNG,
                LocalDate.of(2026, 11, 19), "EXAM_FOCUS", facts, List.of(), SUNEUNG_SECTIONS);

        LinerResponse response = new DeterministicReadingFallback().generate(request);

        assertThatCode(() -> new LinerResponseValidator(mapper).validate(request, response))
                .doesNotThrowAnyException();
        assertThat(response.sections()).hasSize(8);
        assertThat(response.sections().get(0).content())
                .contains("갑 일간은 목의 성향", "목 3·화 2·토 1·금 1·수 1", "상대적으로 적은 토");
        assertThat(response.sections().get(1).content()).contains("수험번호와 선택과목 확인", "종료 10분 전");
        assertThat(response.sections().get(4).content()).contains("특정 음식의 효능을 뜻하지 않습니다");
        assertThat(response.sections().get(6).content()).contains("4초 들이마시고 6초 내쉬는 호흡");
        assertThat(response.sections()).allSatisfy(section ->
                assertThat(section.sourceFactKeys()).contains("dayMaster.hangul", "fiveElements.counts.WATER"));
    }
}
