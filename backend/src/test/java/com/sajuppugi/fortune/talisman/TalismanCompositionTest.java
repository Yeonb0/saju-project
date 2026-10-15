package com.sajuppugi.fortune.talisman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.talisman.domain.TalismanComposition;
import org.junit.jupiter.api.Test;

class TalismanCompositionTest {
    @Test
    void acceptsOnlyStableCatalogKeys() {
        new TalismanComposition("border_water_2", "paper_water_1", "animal_rabbit", "item_csat_2",
                new TalismanComposition.Phrase("합격기원", "合格祈願", "꼭 합격하길 빌어요"), "seal_default");

        assertThatThrownBy(() -> new TalismanComposition("border-water", "paper_water_1", "animal_rabbit",
                "item_csat_2", new TalismanComposition.Phrase("합격기원", "合格祈願", "뜻"), "seal_default"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
