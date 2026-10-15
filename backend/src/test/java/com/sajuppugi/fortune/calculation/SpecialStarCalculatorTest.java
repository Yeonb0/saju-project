package com.sajuppugi.fortune.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.SpecialStarType;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SpecialStarCalculatorTest {
    private final DeterministicSajuEngine engine = new DeterministicSajuEngine();

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
}
