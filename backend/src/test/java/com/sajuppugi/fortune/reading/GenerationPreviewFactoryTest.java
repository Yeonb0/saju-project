package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.application.GenerationPreviewFactory;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationPreviewFactoryTest {
    @Test
    void createsPaidGenerationCopyAndDailyAnimalWithoutRawBirthInput() {
        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2001, 2, 3),
                LocalTime.of(12, 30), false, BirthInput.CalendarType.SOLAR, false,
                BirthInput.Gender.FEMALE), CalculationPolicy.CURRENT);
        var command = new GenerationCommand(UUID.randomUUID(), FortuneType.OVERALL,
                LocalDate.of(2026, 10, 15), "OVERALL_FLOW", facts, List.of(SectionKey.SUMMARY),
                List.of(), "test-generation", "test-content");
        var context = new GenerationResumeContext(command, ProductOption.READING_ONLY, UUID.randomUUID(),
                "테스트", null, null, null, "OVERALL_FLOW", false);

        var preview = new GenerationPreviewFactory().create(context, Instant.parse("2026-10-15T03:00:00Z"));

        assertThat(preview.animalKey()).isEqualTo("dog");
        assertThat(preview.branch()).isEqualTo("술");
        assertThat(preview.animalName()).isEqualTo("개");
        assertThat(preview.message()).hasSizeBetween(180, 400)
                .doesNotContain("2001", "02-03", "테스트");
    }
}
