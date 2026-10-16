package com.sajuppugi.fortune.daily.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DailyFortuneServiceTest {

    @Test
    void keepsTwentyDistinctCandidatesPerElementAndVariesSelectionAcrossSeeds() {
        for (var element : CalculationFacts.Element.values()) {
            assertThat(DailyFortuneService.oneLineCandidates(element)).hasSize(20).doesNotHaveDuplicates();
            assertThat(IntStream.range(0, 60)
                    .mapToObj(index -> DailyFortuneService.selectOneLine("2026-10-" + index, element))
                    .distinct()
                    .count()).isGreaterThan(1);
        }
    }
}
