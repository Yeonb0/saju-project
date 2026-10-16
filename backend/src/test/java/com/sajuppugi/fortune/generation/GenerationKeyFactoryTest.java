package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.application.GenerationKeyFactory;
import com.sajuppugi.fortune.generation.application.LinerFactProjector;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationKeyFactoryTest {
    @Test
    void personalizedConcernChangesTheCachedGenerationIdentity() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(1998, 5, 19),
                LocalTime.NOON, false, BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE),
                CalculationPolicy.CURRENT);
        var projector = new LinerFactProjector(mapper);
        var factory = new GenerationKeyFactory(mapper);
        List<SectionKey> sections = List.of(SectionKey.SUMMARY);
        GenerationCommand base = new GenerationCommand(UUID.randomUUID(), FortuneType.OVERALL,
                LocalDate.of(2026, 10, 15), "OVERALL_FLOW", facts, sections, List.of(), "v1", "c1");
        GenerationCommand tailored = new GenerationCommand(base.requesterUserId(), base.fortuneType(),
                base.referenceDate(), base.interestKey(), facts, sections, List.of(), "v1", "c1",
                "이직을 앞두고 있어요.");
        var safeFacts = projector.project(facts, null);

        assertThat(factory.create(tailored, safeFacts)).isNotEqualTo(factory.create(base, safeFacts));
    }
}
