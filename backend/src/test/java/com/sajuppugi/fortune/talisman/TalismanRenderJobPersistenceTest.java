package com.sajuppugi.fortune.talisman;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.time.Duration;
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
class TalismanRenderJobPersistenceTest {
    @Autowired TalismanRepository talismans;
    @Autowired TalismanRenderJobRepository jobs;
    @Autowired JdbcTemplate jdbc;

    @Test
    void completionUsesTheLeaseFenceAndPublishesAssetsExactlyOnce() {
        Talisman talisman = talismans.createPending(pending());
        Instant now = talisman.createdAt();
        jobs.enqueue(talisman.id(), now);
        jobs.enqueue(talisman.id(), now.plusSeconds(1));

        var lease = jobs.claimReady(now, 3, 10, Duration.ofMinutes(3)).getFirst();
        assertThat(jobs.claimReady(now, 3, 10, Duration.ofMinutes(3))).isEmpty();

        AssetKeys assets = new AssetKeys("talisman/original.png", "talisman/thumbnail.webp", "talisman/share.webp");
        jobs.complete(lease, assets, now.plusSeconds(2));
        jobs.complete(lease, new AssetKeys("different", "different", "different"), now.plusSeconds(3));

        assertThat(talismans.findById(talisman.id()).orElseThrow().status()).isEqualTo(Status.READY);
        assertThat(talismans.findById(talisman.id()).orElseThrow().assets()).isEqualTo(assets);
        assertThat(jdbc.queryForObject("SELECT state FROM talisman_render_jobs WHERE talisman_id = ?", String.class,
                talisman.id())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talisman_render_jobs WHERE talisman_id = ?", Integer.class,
                talisman.id())).isEqualTo(1);
    }

    @Test
    void transientFailureReturnsTheJobToPendingAndTheLastAttemptFailsTheTalisman() {
        Talisman talisman = talismans.createPending(pending());
        Instant now = talisman.createdAt();
        jobs.enqueue(talisman.id(), now);

        var first = jobs.claimReady(now, 2, 1, Duration.ofMinutes(3)).getFirst();
        jobs.fail(first, "OBJECT_STORE_TIMEOUT", now.plusSeconds(1), 2, Duration.ofSeconds(10));
        assertThat(jdbc.queryForObject("SELECT state FROM talisman_render_jobs WHERE talisman_id = ?", String.class,
                talisman.id())).isEqualTo("PENDING");

        var second = jobs.claimReady(now.plusSeconds(11), 2, 1, Duration.ofMinutes(3)).getFirst();
        jobs.fail(second, "OBJECT_STORE_TIMEOUT", now.plusSeconds(12), 2, Duration.ofSeconds(10));
        assertThat(talismans.findById(talisman.id()).orElseThrow().status()).isEqualTo(Status.FAILED);
        assertThat(jdbc.queryForObject("SELECT state FROM talisman_render_jobs WHERE talisman_id = ?", String.class,
                talisman.id())).isEqualTo("EXHAUSTED");
    }

    private Talisman pending() {
        return new Talisman(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), FortuneType.SUNEUNG,
                Element.WATER, Animal.RABBIT, "차분한 집중", "물 기운을 담았어요.", "suneung-v1",
                Status.PENDING, null, null, Instant.parse("2026-10-13T06:00:00Z"), null);
    }
}
