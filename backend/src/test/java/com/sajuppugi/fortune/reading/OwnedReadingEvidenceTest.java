package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnedReadingEvidenceTest {
    @Test
    void derivesImmutableEvidenceSummaryFromThePersistedSectionFactKeys() {
        var response = new LinerResponse(List.of(new GeneratedSection(SectionKey.SUMMARY,
                "균형을 살피는 흐름입니다.", List.of("pillars.day.display", "fiveElements.counts.WATER"))), List.of());
        var reading = new OwnedReading(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FortuneType.OVERALL, OwnedReading.ProductOption.READING_ONLY, "표시명", null, response,
                "manse-2026.10-v1", "liner-general-reading-v5", "overall-v1", GenerationMode.FALLBACK,
                null, null, Instant.now());

        assertThat(reading.ruleVersion()).isNull(); // Legacy-compatible constructor deliberately has no rule metadata.
        assertThat(reading.evidenceSummary()).containsExactly(new OwnedReading.SectionEvidence("SUMMARY",
                List.of("pillars.day.display", "fiveElements.counts.WATER")));
        assertThatThrownBy(() -> reading.evidenceSummary().add(new OwnedReading.SectionEvidence("ACTION_TIP", List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(reading.evidenceSummary().toString()).doesNotContain("2004-03-15", "14:32");
    }
}
