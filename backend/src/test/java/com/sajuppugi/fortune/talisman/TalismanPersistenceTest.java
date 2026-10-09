package com.sajuppugi.fortune.talisman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TalismanPersistenceTest {
    @Autowired TalismanRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void storesPendingTalismanAndPurchaseOwnershipWithoutPersonalFacts() {
        Talisman pending = pending();

        Talisman saved = repository.createPending(pending);

        assertThat(saved).isEqualTo(pending);
        assertThat(repository.findOwned(pending.ownerUserId(), pending.id())).contains(pending);
        assertThat(jdbc.queryForObject("SELECT source FROM talisman_ownerships WHERE talisman_id = ?",
                String.class, pending.id())).isEqualTo("PURCHASE");
        assertThat(jdbc.queryForList("SELECT * FROM talismans WHERE id = ?", pending.id()).getFirst().keySet())
                .doesNotContain("birth_date", "birth_time", "gender", "subject_name", "facts_json");
    }

    @Test
    void returnsFirstTalismanWhenTheSameReadingIsCreatedAgain() {
        Talisman first = repository.createPending(pending());
        Talisman duplicate = new Talisman(UUID.randomUUID(), first.ownerUserId(), first.sourceReadingId(),
                first.fortuneType(), Element.FIRE, Animal.DRAGON, "다른 문구", "다른 설명",
                first.contentVersion(), Status.PENDING, null, null, first.createdAt().plusSeconds(1), null);

        assertThat(repository.createPending(duplicate)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talismans WHERE source_reading_id = ?",
                Integer.class, first.sourceReadingId())).isEqualTo(1);
    }

    @Test
    void enforcesReadyAndFailedStateTransitions() {
        Talisman ready = repository.createPending(pending());
        Instant readyAt = ready.createdAt().plusSeconds(10);
        AssetKeys assets = new AssetKeys("private/original.png", "private/thumbnail.webp", "public/share.webp");

        repository.markReady(ready.id(), assets, readyAt);

        Talisman storedReady = repository.findByReadingId(ready.sourceReadingId()).orElseThrow();
        assertThat(storedReady.status()).isEqualTo(Status.READY);
        assertThat(storedReady.assets()).isEqualTo(assets);
        assertThat(storedReady.readyAt()).isEqualTo(readyAt);
        assertThatThrownBy(() -> repository.markFailed(ready.id(), "RENDER_FAILED"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot mark talisman failed from current state");

        Talisman failed = repository.createPending(pending());
        repository.markFailed(failed.id(), "RENDER_FAILED");
        assertThat(repository.findByReadingId(failed.sourceReadingId()).orElseThrow().status())
                .isEqualTo(Status.FAILED);
        repository.retry(failed.id());
        assertThat(repository.findByReadingId(failed.sourceReadingId()).orElseThrow().status())
                .isEqualTo(Status.PENDING);
    }

    private Talisman pending() {
        return new Talisman(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), FortuneType.SUNEUNG,
                Element.WATER, Animal.RABBIT, "차분한 집중, 고요한 힘을 모아요",
                "물 기운을 채울 수 있도록 검푸른빛을 담았어요.", "suneung-2026.10.08",
                Status.PENDING, null, null, Instant.parse("2026-10-08T03:00:00Z"), null);
    }
}
