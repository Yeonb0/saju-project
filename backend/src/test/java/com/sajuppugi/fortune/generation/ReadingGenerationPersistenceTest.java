package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReadingGenerationPersistenceTest {
    @Autowired ReadingGenerationService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reusesFirstSuccessfulSnapshotAcrossUsersWithoutPersistingIdentityOrBirthDateInFacts() {
        CalculationFacts facts = new DeterministicSajuEngine().calculate(
                new BirthInput(LocalDate.of(2004, 3, 15), LocalTime.of(14, 32), false,
                        CalendarType.SOLAR, false, Gender.FEMALE), CalculationPolicy.CURRENT);
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();

        GeneratedReading first = service.generate(command(firstUser, facts));
        GeneratedReading second = service.generate(command(secondUser, facts));

        assertThat(first.reused()).isFalse();
        assertThat(second.reused()).isTrue();
        assertThat(second.resultId()).isEqualTo(first.resultId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generation_attempts WHERE reading_result_id = ?",
                Integer.class, first.resultId())).isEqualTo(1);
        String persistedFacts = jdbc.queryForObject("SELECT facts_json FROM reading_results WHERE id = ?",
                String.class, first.resultId());
        assertThat(persistedFacts)
                .doesNotContain("2004-03-15")
                .doesNotContain(firstUser.toString())
                .doesNotContain(secondUser.toString());
        assertThat(first.calculationVersion()).isEqualTo(CalculationPolicy.CURRENT.version());
        assertThat(first.generationVersion()).isEqualTo("liner-reading-v1");
        assertThat(first.contentVersion()).isEqualTo("content-v1");
    }

    private GenerationCommand command(UUID userId, CalculationFacts facts) {
        return new GenerationCommand(userId, FortuneType.SUNEUNG, LocalDate.of(2026, 11, 19),
                "EXAM_FOCUS", facts, List.of(SectionKey.SUMMARY, SectionKey.EXAM_DAY), List.of(),
                "liner-reading-v1", "content-v1");
    }
}
