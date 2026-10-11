package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.application.DeterministicReadingFallback;
import com.sajuppugi.fortune.generation.application.LinerFactProjector;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.domain.GeneralReadingPolicy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingQualityRegressionTest {
    private static final List<SectionKey> SUNEUNG_SECTIONS = List.of(
            SectionKey.SUMMARY, SectionKey.EXAM_DAY, SectionKey.EXAM_PERIODS, SectionKey.FOCUS,
            SectionKey.MEAL, SectionKey.PREPARATION, SectionKey.ANXIETY_MANAGEMENT, SectionKey.MISSING_ELEMENT);
    private static final List<BirthInput> PROFILES = List.of(
            birth(1994, 1, 7, 7, 10, BirthInput.Gender.FEMALE),
            birth(1998, 5, 19, 12, 30, BirthInput.Gender.MALE),
            birth(2001, 9, 3, 23, 15, BirthInput.Gender.FEMALE),
            birth(2004, 3, 15, 14, 32, BirthInput.Gender.MALE),
            birth(2007, 12, 28, 5, 45, BirthInput.Gender.FEMALE));

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeterministicSajuEngine engine = new DeterministicSajuEngine();
    private final LinerFactProjector projector = new LinerFactProjector(mapper);
    private final DeterministicReadingFallback fallback = new DeterministicReadingFallback();
    private final LinerResponseValidator validator = new LinerResponseValidator(mapper);

    @Test
    void validatesThirtyGoldenFallbackScenariosAcrossEveryFortuneType() {
        var counterpart = engine.calculate(PROFILES.get(1), CalculationPolicy.CURRENT);
        for (BirthInput profile : PROFILES) {
            var facts = engine.calculate(profile, CalculationPolicy.CURRENT);
            for (FortuneType type : FortuneType.values()) {
                List<SectionKey> sections = type == FortuneType.SUNEUNG
                        ? SUNEUNG_SECTIONS : GeneralReadingPolicy.require(type).allowedSections();
                var projected = projector.project(facts, type == FortuneType.COMPATIBILITY ? counterpart : null);
                var request = new LinerRequest(type.name() + "-golden", facts.meta().calculationVersion(),
                        "quality-v1", type, LocalDate.of(2026, 11, 19), question(type),
                        type == FortuneType.COMPATIBILITY ? "LOVER" : null,
                        projected, List.of(), sections);

                assertThatCode(() -> validator.validate(request, fallback.generate(request)))
                        .as("%s / %s", type, profile.birthDate())
                        .doesNotThrowAnyException();
            }
        }
    }

    private String question(FortuneType type) {
        return switch (type) {
            case SUNEUNG -> "EXAM_FOCUS";
            case OVERALL -> "OVERALL_FLOW";
            case LOVE -> "CURRENT_RELATIONSHIP";
            case WEALTH -> "OVERALL_WEALTH";
            case COMPATIBILITY -> "OVERALL_MATCH";
            case SINSAL -> "OVERALL_SINSAL";
        };
    }

    private static BirthInput birth(int year, int month, int day, int hour, int minute, BirthInput.Gender gender) {
        return new BirthInput(LocalDate.of(year, month, day), LocalTime.of(hour, minute), false,
                BirthInput.CalendarType.SOLAR, false, gender);
    }
}
