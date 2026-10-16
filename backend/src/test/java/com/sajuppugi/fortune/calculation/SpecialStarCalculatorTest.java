package com.sajuppugi.fortune.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.SpecialStarType;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.domain.SpecialStarCalculator;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

class SpecialStarCalculatorTest {
    private final DeterministicSajuEngine engine = new DeterministicSajuEngine();

    @ParameterizedTest(name = "{0}")
    @MethodSource("sinsalFixtures")
    void matchesVersionedSinsalRuleFixtures(SinsalFixture fixture) {
        assertThat(fixture.calculationPolicyVersion()).isEqualTo(CalculationPolicy.CURRENT.version());
        assertThat(fixture.ruleVersion()).isEqualTo(SpecialStarCalculator.RULE_VERSION);
        var input = fixture.input();
        var facts = engine.calculate(new BirthInput(LocalDate.parse(input.birthDate()), LocalTime.parse(input.birthTime()), false,
                BirthInput.CalendarType.valueOf(input.calendarType()), input.leapMonth(),
                BirthInput.Gender.valueOf(input.gender())), CalculationPolicy.CURRENT);

        assertThat(facts.specialStars().ruleVersion()).isEqualTo(fixture.ruleVersion());
        assertThat(facts.specialStars().natal()).hasSize(fixture.expected().natalTypeCount());
        fixture.expected().occurrenceCounts().forEach((type, count) -> assertThat(facts.specialStars().natal())
                .filteredOn(item -> item.type() == SpecialStarType.valueOf(type)).singleElement()
                .extracting(item -> item.count()).isEqualTo(count));
        if (fixture.expected().requiresActivations()) assertThat(facts.specialStars().activations()).isNotEmpty();
    }

    @Test
    void calculatesAllThirteenNatalStarsAndTenYearAndAnnualActivations() {
        var facts = engine.calculate(new BirthInput(LocalDate.of(2024, 9, 1), LocalTime.of(5, 8), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE), CalculationPolicy.CURRENT);

        assertThat(facts.specialStars().ruleVersion()).isEqualTo("sinsal-v1");
        assertThat(facts.specialStars().natal()).hasSize(13);
        assertThat(facts.specialStars().natal())
                .filteredOn(item -> item.type() == SpecialStarType.RED_CHARM)
                .singleElement().satisfies(item -> assertThat(item.count()).isEqualTo(2));
        assertThat(facts.specialStars().natal())
                .filteredOn(item -> item.type() == SpecialStarType.WHITE_TIGER)
                .singleElement().satisfies(item -> assertThat(item.count()).isEqualTo(1));
        assertThat(facts.specialStars().activations()).isNotEmpty();
        assertThat(facts.specialStars().activations()).allSatisfy(activation ->
                assertThat((activation.year() != null) ^ (activation.startAge() != null)).isTrue());
    }

    private static java.util.stream.Stream<SinsalFixture> sinsalFixtures() {
        try (var stream = new ClassPathResource("saju-rules/sinsal-v1/cases.json").getInputStream()) {
            return new ObjectMapper().findAndRegisterModules()
                    .readValue(stream, new TypeReference<List<SinsalFixture>>() {}).stream();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load sinsal rule fixtures", exception);
        }
    }

    record SinsalFixture(String caseId, String calculationPolicyVersion, String ruleVersion,
                         SinsalInput input, SinsalExpected expected) {
        @Override public String toString() { return caseId; }
    }
    record SinsalInput(String birthDate, String birthTime, String calendarType, boolean leapMonth, String gender) {}
    record SinsalExpected(int natalTypeCount, Map<String, Integer> occurrenceCounts, boolean requiresActivations) {}
}
