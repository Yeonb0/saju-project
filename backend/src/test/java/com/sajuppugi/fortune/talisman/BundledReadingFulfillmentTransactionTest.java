package com.sajuppugi.fortune.talisman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.reading.application.BundledReadingFulfillmentService;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.OwnedReading.ProductOption;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateTalisman;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BundledReadingFulfillmentTransactionTest {
    @Autowired BundledReadingFulfillmentService fulfillment;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReadingRepository readings;
    @Autowired TalismanRepository talismans;

    @Test
    void rollsBackTalismanWhenReadingCannotBeStored() {
        Instant now = Instant.parse("2026-10-08T03:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        UUID missingResultId = UUID.randomUUID();
        seedGeneratingPurchase(productId, quoteId, purchaseId, userId, personId, now);
        ReadingPurchase purchase = new ReadingPurchase(purchaseId, userId, productId, quoteId, personId,
                UUID.randomUUID(), Status.GENERATING, null, now, null);
        OwnedReading reading = new OwnedReading(readingId, missingResultId, userId, purchaseId,
                FortuneType.SUNEUNG, ProductOption.READING_WITH_TALISMAN, personId, "민지",
                null, null, null, "EXAM_FOCUS", null, new LinerResponse(List.of(), List.of()),
                "calc-v1", "generation-v1", "content-v1", GenerationMode.LINER, null, null, now);

        assertThatThrownBy(() -> fulfillment.fulfill(purchase, reading,
                new CreateTalisman(userId, readingId, FortuneType.SUNEUNG, facts(), "content-v1")))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talismans WHERE source_reading_id = ?",
                Integer.class, readingId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM reading_purchases WHERE id = ?",
                String.class, purchaseId)).isEqualTo("GENERATING");
    }

    @Test
    void readingQueriesUseTheCurrentTalismanStatus() {
        Instant now = Instant.parse("2026-10-08T04:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        seedGeneratingPurchase(productId, quoteId, purchaseId, userId, personId, now);
        seedReadingResult(resultId, now);
        ReadingPurchase purchase = new ReadingPurchase(purchaseId, userId, productId, quoteId, personId,
                UUID.randomUUID(), Status.GENERATING, null, now, null);
        OwnedReading draft = new OwnedReading(readingId, resultId, userId, purchaseId,
                FortuneType.SUNEUNG, ProductOption.READING_WITH_TALISMAN, personId, "민지",
                null, null, null, "EXAM_FOCUS", null, new LinerResponse(List.of(), List.of()),
                "calc-v1", "generation-v1", "content-v1", GenerationMode.LINER, null, null, now);

        OwnedReading stored = fulfillment.fulfill(purchase, draft,
                new CreateTalisman(userId, readingId, FortuneType.SUNEUNG, facts(), "content-v1"));
        Instant readyAt = now.plusSeconds(5);
        talismans.markReady(stored.talismanId(), new AssetKeys("original", "thumbnail", "share"), readyAt);

        assertThat(readings.findOwnedReading(userId, readingId).orElseThrow().talismanStatus()).isEqualTo("READY");
        assertThat(readings.findOwnedReadings(userId, FortuneType.SUNEUNG, null, null, null, 10))
                .singleElement().extracting(OwnedReading::talismanStatus).isEqualTo("READY");
    }

    private void seedGeneratingPurchase(UUID productId, UUID quoteId, UUID purchaseId,
                                        UUID userId, UUID personId, Instant now) {
        jdbc.update("""
                INSERT INTO products (id, code, category, price_currency, price_amount, active, catalog_version)
                VALUES (?, ?, 'READING', 'TURTLE_SHELL', 15, TRUE, 'test-v1')
                """, productId, "TEST_TALISMAN_" + productId);
        jdbc.update("""
                INSERT INTO purchase_quotes (id, requester_user_id, product_id, product_code, category,
                price_currency, price_amount, active, catalog_version, context_hash, created_at, expires_at,
                purchase_id)
                VALUES (?, ?, ?, ?, 'READING', 'TURTLE_SHELL', 15, TRUE, 'test-v1', ?, ?, ?, ?)
                """, quoteId, userId, productId, "TEST_TALISMAN_" + productId, "0".repeat(64),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(1800)), purchaseId);
        jdbc.update("""
                INSERT INTO reading_purchases (id, buyer_user_id, product_id, quote_id, subject_person_id,
                wallet_transaction_id, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'GENERATING', ?)
                """, purchaseId, userId, productId, quoteId, personId, UUID.randomUUID(), Timestamp.from(now));
    }

    private CalculationFacts facts() {
        Map<Element, Integer> counts = new EnumMap<>(Element.class);
        for (Element element : Element.values()) counts.put(element, 2);
        counts.put(Element.WATER, 0);
        return new CalculationFacts(null, null, null, null,
                new CalculationFacts.FiveElements(counts, List.of(), List.of(Element.WATER)),
                null, null, null, null);
    }

    private void seedReadingResult(UUID resultId, Instant now) {
        String generationKey = resultId.toString().replace("-", "").repeat(2);
        jdbc.update("""
                INSERT INTO reading_results (id, generation_key, fortune_type, reference_date, facts_json,
                sections_json, calculation_version, generation_version, content_version, status,
                created_at, completed_at)
                VALUES (?, ?, 'SUNEUNG', '2026-11-19', '{}', '{}', 'calc-v1', 'generation-v1',
                'content-v1', 'SUCCEEDED', ?, ?)
                """, resultId, generationKey, Timestamp.from(now), Timestamp.from(now));
    }
}
