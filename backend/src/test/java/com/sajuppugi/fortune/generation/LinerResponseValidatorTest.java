package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;

class LinerResponseValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
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
    void rejectsAnEmptyPaidResultAndOmittedRequiredSections() {
        LinerResponse empty = new LinerResponse(List.of(), List.of(SectionKey.SUMMARY));

        assertThatThrownBy(() -> validator.validate(request, empty))
                .isInstanceOf(InvalidGenerationException.class)
                .extracting(exception -> ((InvalidGenerationException) exception).code())
                .isEqualTo("LINER_SCHEMA_INVALID");
    }
}
