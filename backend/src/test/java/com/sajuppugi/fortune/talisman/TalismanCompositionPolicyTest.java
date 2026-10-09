package com.sajuppugi.fortune.talisman;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.TalismanCompositionPolicy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TalismanCompositionPolicyTest {
    private final TalismanCompositionPolicy policy = new TalismanCompositionPolicy(new Random(7));

    @Test
    void selectsFirstExplicitlyMissingElementInCanonicalOrder() {
        var composition = policy.compose(FortuneType.SUNEUNG,
                facts(counts(1, 2, 3, 4, 5), List.of(Element.WATER, Element.FIRE)));

        assertThat(composition.element()).isEqualTo(Element.FIRE);
        assertThat(composition.description()).contains("불 기운", "붉은빛");
    }

    @Test
    void fallsBackToLowestCountAndBreaksTiesCanonically() {
        var composition = policy.compose(FortuneType.LOVE,
                facts(counts(1, 1, 3, 4, 5), List.of()));

        assertThat(composition.element()).isEqualTo(Element.WOOD);
        assertThat(composition.phrase()).startsWith("따뜻한 인연");
    }

    @Test
    void onlyChoosesFromTheTwelveAnimals() {
        for (int index = 0; index < 100; index++) {
            Animal animal = policy.compose(FortuneType.OVERALL,
                    facts(counts(1, 2, 3, 4, 5), List.of())).animal();
            assertThat(List.of(Animal.values())).contains(animal);
        }
    }

    private CalculationFacts facts(Map<Element, Integer> counts, List<Element> missing) {
        return new CalculationFacts(null, null, null, null,
                new CalculationFacts.FiveElements(counts, List.of(), missing), null, null, null, null);
    }

    private Map<Element, Integer> counts(int wood, int fire, int earth, int metal, int water) {
        Map<Element, Integer> values = new EnumMap<>(Element.class);
        values.put(Element.WOOD, wood);
        values.put(Element.FIRE, fire);
        values.put(Element.EARTH, earth);
        values.put(Element.METAL, metal);
        values.put(Element.WATER, water);
        return values;
    }
}
