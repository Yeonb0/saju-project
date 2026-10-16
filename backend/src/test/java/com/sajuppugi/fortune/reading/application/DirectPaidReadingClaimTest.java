package com.sajuppugi.fortune.reading.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
class DirectPaidReadingClaimTest {
    @Autowired SuneungPurchaseClaimService claims;
    @Autowired JdbcTemplate jdbc;

    @Test
    void atomicallyConsumesPaymentAndPersistsRecoverableGenerationContext() {
        UUID user = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID quote = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        Instant now = Instant.now();
        String productCode = "DIRECT_READING_" + product;
        jdbc.update("""
                INSERT INTO products(id,code,category,price_currency,price_amount,active,catalog_version,
                    product_name,fortune_type,product_option)
                VALUES (?,?,'READING','KRW',1800,TRUE,'test','직접결제 운세','OVERALL','READING_ONLY')
                """, product, productCode);
        jdbc.update("""
                INSERT INTO purchase_quotes(id,requester_user_id,product_id,product_code,category,price_currency,
                    price_amount,active,catalog_version,context_hash,created_at,expires_at,product_name,
                    fortune_type,product_option)
                VALUES (?,?,?,?,'READING','KRW',1800,TRUE,'test',?,?,?,?, 'OVERALL','READING_ONLY')
                """, quote, user, product, productCode, "a".repeat(64), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(1800)), "직접결제 운세");
        jdbc.update("""
                INSERT INTO direct_payment_batches(id,buyer_user_id,quote_id,product_code,product_name,
                    unit_amount_krw,quantity,total_amount_krw,key_hash,status,created_at)
                VALUES (?,?,?,?,?,1800,1,1800,?,'PAID',?)
                """, batch, user, quote, productCode, "직접결제 운세", "b".repeat(64), Timestamp.from(now));

        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2000, 1, 2),
                LocalTime.of(12, 0), false, BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE),
                CalculationPolicy.CURRENT);
        var command = new GenerationCommand(user, FortuneType.OVERALL, LocalDate.of(2026, 10, 16),
                "OVERALL_FLOW", facts, List.of(SectionKey.SUMMARY), List.of(), "test-v1", "test-v1");
        var context = new GenerationResumeContext(command, ProductOption.READING_ONLY, person, "테스트",
                null, null, null, "OVERALL_FLOW", false);

        var purchase = claims.claimDirectPaid(user, batch, quote, product, person, null, null,
                "OVERALL_FLOW", context);

        assertThat(jdbc.queryForObject("SELECT status FROM reading_purchases WHERE id = ?", String.class,
                purchase.id())).isEqualTo("DEBITED");
        assertThat(jdbc.queryForObject("SELECT generation_context FROM reading_purchases WHERE id = ?", String.class,
                purchase.id())).startsWith("enc:v2:").doesNotContain("테스트", "2000-01-02");
        assertThat(jdbc.queryForObject("SELECT consumer_id FROM direct_payment_consumptions WHERE batch_id = ?",
                UUID.class, batch)).isEqualTo(purchase.id());
    }
}
