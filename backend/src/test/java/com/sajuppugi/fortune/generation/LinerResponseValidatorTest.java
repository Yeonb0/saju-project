package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator.InvalidGenerationException;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import java.time.LocalDate;
import java.util.List;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.application.LinerFactProjector;
import com.sajuppugi.fortune.reading.domain.GeneralReadingPolicy;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class LinerResponseValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LinerResponseValidator validator = new LinerResponseValidator(mapper);
    private final LinerRequest request = new LinerRequest("a".repeat(64), "calc-v1", "gen-v1",
            FortuneType.OVERALL, LocalDate.of(2026, 10, 8), null,
            mapper.createObjectNode().putObject("dayMaster").put("hanja", "癸"),
            List.of(), List.of(SectionKey.SUMMARY));

    @Test
    void rejectsEvidenceThatWasNotProvided() {
        LinerResponse response = new LinerResponse(List.of(
                new GeneratedSection(SectionKey.SUMMARY, "차분한 흐름이에요.", List.of("pillars.hour"))), List.of());

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_SOURCE_FACT_INVALID");
    }

    @Test
    void rejectsUnapprovedSectionsAndForbiddenCertainty() {
        LinerResponse wrongSection = new LinerResponse(List.of(
                new GeneratedSection(SectionKey.WEALTH_FLOW, "재물 흐름", List.of("dayMaster.hanja"))), List.of());
        LinerResponse forbidden = new LinerResponse(List.of(
                new GeneratedSection(SectionKey.SUMMARY, "반드시 합격한다.", List.of("dayMaster.hanja"))), List.of());

        assertThatThrownBy(() -> validator.validate(request, wrongSection))
                .isInstanceOf(InvalidGenerationException.class);
        assertThatThrownBy(() -> validator.validate(request, forbidden))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_FORBIDDEN_EXPRESSION");
    }

    @Test
    void rejectsNamedPillarAndSpecialStarThatAreAbsentFromFacts() {
        var facts = mapper.createObjectNode();
        facts.putObject("pillars").putObject("day").put("display", "계사");
        var occurrence = facts.putObject("specialStars").putArray("natal").addObject();
        occurrence.put("type", "PEACH_BLOSSOM");
        occurrence.put("count", 1);
        LinerRequest constrained = new LinerRequest("e".repeat(64), "calc-v1", "gen-v1",
                FortuneType.OVERALL, LocalDate.of(2026, 10, 8), null, facts,
                List.of(), List.of(SectionKey.SUMMARY));

        LinerResponse wrongPillar = new LinerResponse(List.of(new GeneratedSection(SectionKey.SUMMARY,
                "갑자 일주라고 단정하지 말아야 합니다.", List.of("pillars.day.display"))), List.of());
        LinerResponse wrongStar = new LinerResponse(List.of(new GeneratedSection(SectionKey.SUMMARY,
                "백호살이 있다고 단정하지 말아야 합니다.", List.of("specialStars.natal[0].type"))), List.of());

        assertThatThrownBy(() -> validator.validate(constrained, wrongPillar))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_UNSUPPORTED_ENTITY");
        assertThatThrownBy(() -> validator.validate(constrained, wrongStar))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_UNSUPPORTED_ENTITY");
    }

    @Test
    void rejectsAnEmptyPaidResultAndOmittedRequiredSections() {
        LinerResponse empty = new LinerResponse(List.of(), List.of(SectionKey.SUMMARY));

        assertThatThrownBy(() -> validator.validate(request, empty))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_SCHEMA_INVALID");
    }

    @Test
    void rejectsSectionEvidenceThatDoesNotFitItsPurpose() {
        var evidenceFacts = mapper.createObjectNode();
        evidenceFacts.putObject("dayMaster").put("hanja", "癸");
        LinerRequest missingElementRequest = new LinerRequest("b".repeat(64), "calc-v1", "gen-v1",
                FortuneType.OVERALL, LocalDate.of(2026, 10, 8), "OVERALL_FLOW",
                evidenceFacts,
                List.of(), List.of(SectionKey.MISSING_ELEMENT));
        LinerResponse response = new LinerResponse(List.of(new GeneratedSection(SectionKey.MISSING_ELEMENT,
                "생활의 균형을 확인해 보세요.", List.of("dayMaster.hanja"))), List.of());

        assertThatThrownBy(() -> validator.validate(missingElementRequest, response))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_SECTION_EVIDENCE_MISMATCH");
    }

    @Test
    void rejectsNearDuplicateSections() {
        var facts = mapper.createObjectNode();
        facts.putObject("dayMaster").put("hanja", "癸");
        LinerRequest twoSections = new LinerRequest("c".repeat(64), "calc-v1", "gen-v1",
                FortuneType.OVERALL, LocalDate.of(2026, 10, 8), "OVERALL_FLOW", facts,
                List.of(), List.of(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW));
        LinerResponse response = new LinerResponse(List.of(
                new GeneratedSection(SectionKey.SUMMARY, "오늘은 차분하게 순서를 확인하고 행동해 보세요.", List.of("dayMaster.hanja")),
                new GeneratedSection(SectionKey.CURRENT_FLOW, "오늘은 차분하게 순서를 확인하고 행동해 보세요!", List.of("dayMaster.hanja"))), List.of());

        assertThatThrownBy(() -> validator.validate(twoSections, response))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_SECTION_TOO_SIMILAR");
    }

    @Test
    void acceptsPurposeSpecificEvidenceFromRealProjectedFacts() {
        var calculated = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2004, 3, 15),
                LocalTime.of(14, 32), false, BirthInput.CalendarType.SOLAR, false,
                BirthInput.Gender.FEMALE), CalculationPolicy.CURRENT);
        var facts = new LinerFactProjector(mapper).project(calculated);
        var sections = GeneralReadingPolicy.require(FortuneType.OVERALL).allowedSections();
        LinerRequest realRequest = new LinerRequest("d".repeat(64), "calc-v1", "gen-v1",
                FortuneType.OVERALL, LocalDate.of(2026, 10, 8), "OVERALL_FLOW", facts, List.of(), sections);
        LinerResponse response = new LinerResponse(sections.stream().map(section -> new GeneratedSection(section,
                section + " 서로 다른 구체적인 검증 문장입니다.", section == SectionKey.MISSING_ELEMENT
                ? List.of("fiveElements.counts.WOOD") : List.of("dayMaster.hanja"))).toList(), List.of());

        assertThatCode(() -> validator.validate(realRequest, response)).doesNotThrowAnyException();
    }
}
