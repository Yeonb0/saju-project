package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.application.GenerationRecoveryService;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
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
}
