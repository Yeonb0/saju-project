package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.application.GenerationRecoveryService;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.infrastructure.GenerationContextProtector;
import com.sajuppugi.fortune.reading.infrastructure.GenerationContextReencryptionService;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL-only lease competition test; never runs in the default H2 suite. */
@EnabledIfSystemProperty(named = "generation.recovery.postgres", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/sajuppugi_test",
        "spring.datasource.username=sajuppugi_test", "spring.datasource.password=local-test-only",
        "app.wallet.purchases-enabled=false", "app.reading.generation-recovery.enabled=false",
        "app.liner.adapter=fake"})
@ActiveProfiles("test")
class GenerationRecoveryPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ReadingRepository readings;
    @Autowired GenerationRecoveryService recovery;
    @Autowired GenerationContextProtector contextProtector;
    @Autowired GenerationContextReencryptionService reencryption;

    @Test
    void legacyPlaintextContextIsReencryptedIdempotentlyAndRemainsRecoverable() {
        Fixture fixture = fixture();
        String encrypted = jdbc.queryForObject(
                "SELECT generation_context FROM reading_purchases WHERE id = ?", String.class, fixture.purchase());
        String plaintext = contextProtector.decrypt(encrypted);
        jdbc.update("UPDATE reading_purchases SET generation_context = ? WHERE id = ?", plaintext, fixture.purchase());

        assertThat(reencryption.reencryptBatch(100)).isGreaterThanOrEqualTo(1);
        String migrated = jdbc.queryForObject(
                "SELECT generation_context FROM reading_purchases WHERE id = ?", String.class, fixture.purchase());
        assertThat(migrated).startsWith("enc:v2:").doesNotContain("테스트", "2004-03-15");
        assertThat(reencryption.reencryptBatch(100)).isZero();
        assertThat(recovery.recover(fixture.purchase())).isEqualTo(GenerationRecoveryService.Outcome.FULFILLED);
    }

    @Test
    void twoWorkersRecoverOneDebitedPurchaseExactlyOnce() throws Exception {
        UUID user = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID quote = UUID.randomUUID();
        UUID purchase = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        Instant now = Instant.now();
        String code = "RECOVERY_" + product;
        jdbc.update("INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version) VALUES (?, ?, 'READING', 'TURTLE_SHELL', 15, true, 'test')", product, code);
        jdbc.update("""
                INSERT INTO purchase_quotes(id, requester_user_id, product_id, product_code, category, price_currency,
                price_amount, active, catalog_version, context_hash, created_at, expires_at, purchase_id)
                VALUES (?, ?, ?, ?, 'READING', 'TURTLE_SHELL', 15, true, 'test', ?, ?, ?, ?)
                """, quote, user, product, code, "a".repeat(64), Timestamp.from(now), Timestamp.from(now.plusSeconds(60)), purchase);
        jdbc.update("""
                INSERT INTO reading_purchases(id, buyer_user_id, product_id, quote_id, subject_person_id, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'DEBITED', ?)
                """, purchase, user, product, quote, person, Timestamp.from(now));
        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2004, 3, 15),
                LocalTime.of(14, 32), false, BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE), CalculationPolicy.CURRENT);
        var command = new GenerationCommand(user, FortuneType.OVERALL, LocalDate.of(2026, 10, 11), "OVERALL_FLOW",
                facts, List.of(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW), List.of(), "recovery-test", "recovery-test");
        readings.saveGenerationContext(purchase, new GenerationResumeContext(command, ProductOption.READING_ONLY,
                person, "테스트", null, null, null, "OVERALL_FLOW", false));
        String storedContext = jdbc.queryForObject(
                "SELECT generation_context FROM reading_purchases WHERE id = ?", String.class, purchase);
        assertThat(storedContext).startsWith("enc:v2:").doesNotContain("테스트", "2004-03-15");

        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> recovery.recover(purchase));
            var second = pool.submit(() -> recovery.recover(purchase));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .contains(GenerationRecoveryService.Outcome.FULFILLED);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM reading_purchases WHERE id = ?", String.class, purchase))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM readings WHERE purchase_id = ?", Integer.class, purchase))
                .isEqualTo(1);
    }

    @Test
    void restartReclaimsExpiredLeaseAndRejectsLateWorkerMutation() {
        Fixture fixture = fixture();
        Instant firstStart = Instant.now().minusSeconds(120);
        var firstLease = readings.tryClaimGeneration(fixture.purchase(), UUID.randomUUID(), firstStart,
                firstStart.plusSeconds(30)).orElseThrow();
        var secondLease = readings.tryClaimGeneration(fixture.purchase(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(120)).orElseThrow();

        assertThat(secondLease.fence()).isGreaterThan(firstLease.fence());
        assertThatThrownBy(() -> readings.markFailed(fixture.purchase(), "LATE_WORKER_FAILURE", firstLease))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM reading_purchases WHERE id = ?", String.class,
                fixture.purchase())).isEqualTo("GENERATING");

        // Simulate a process dying while holding the second lease, then a restarted worker polling after expiry.
        jdbc.update("UPDATE reading_purchases SET generation_lease_until = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), fixture.purchase());
        assertThat(recovery.recover(fixture.purchase())).isEqualTo(GenerationRecoveryService.Outcome.FULFILLED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM readings WHERE purchase_id = ?", Integer.class,
                fixture.purchase())).isEqualTo(1);
    }

    @Test
    void exhaustedStaleGenerationLeavesPollingAndMovesToFailedReviewPath() {
        Fixture fixture = fixture();
        jdbc.update("""
                UPDATE reading_purchases SET status = 'GENERATING', generation_attempts = 3,
                generation_lease_token = ?, generation_lease_until = ? WHERE id = ?
                """, UUID.randomUUID(), Timestamp.from(Instant.now().minusSeconds(1)), fixture.purchase());

        assertThat(readings.findExhaustedGenerationRecoveries(Instant.now(), 3, 20))
                .contains(fixture.purchase());
        assertThat(readings.markGenerationExhausted(fixture.purchase(), Instant.now(), 3)).isTrue();
        assertThat(jdbc.queryForMap("""
                SELECT status, failure_code, compensation_status FROM reading_purchases WHERE id = ?
                """, fixture.purchase()))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "GENERATION_RETRY_EXHAUSTED")
                .containsEntry("compensation_status", "NOT_REQUIRED");
    }

    private Fixture fixture() {
        UUID user = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID quote = UUID.randomUUID();
        UUID purchase = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        Instant now = Instant.now();
        String code = "RESTART_" + product;
        jdbc.update("INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version) VALUES (?, ?, 'READING', 'TURTLE_SHELL', 15, true, 'test')", product, code);
        jdbc.update("""
                INSERT INTO purchase_quotes(id, requester_user_id, product_id, product_code, category, price_currency,
                price_amount, active, catalog_version, context_hash, created_at, expires_at, purchase_id)
                VALUES (?, ?, ?, ?, 'READING', 'TURTLE_SHELL', 15, true, 'test', ?, ?, ?, ?)
                """, quote, user, product, code, "b".repeat(64), Timestamp.from(now), Timestamp.from(now.plusSeconds(60)), purchase);
        jdbc.update("INSERT INTO reading_purchases(id, buyer_user_id, product_id, quote_id, subject_person_id, status, created_at) VALUES (?, ?, ?, ?, ?, 'DEBITED', ?)",
                purchase, user, product, quote, person, Timestamp.from(now));
        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2004, 3, 15),
                LocalTime.of(14, 32), false, BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE), CalculationPolicy.CURRENT);
        var command = new GenerationCommand(user, FortuneType.OVERALL, LocalDate.of(2026, 10, 11), "OVERALL_FLOW",
                facts, List.of(SectionKey.SUMMARY, SectionKey.CURRENT_FLOW), List.of(), "restart-test", "restart-test");
        readings.saveGenerationContext(purchase, new GenerationResumeContext(command, ProductOption.READING_ONLY,
                person, "테스트", null, null, null, "OVERALL_FLOW", false));
        return new Fixture(purchase);
    }

    private record Fixture(UUID purchase) {}
}
