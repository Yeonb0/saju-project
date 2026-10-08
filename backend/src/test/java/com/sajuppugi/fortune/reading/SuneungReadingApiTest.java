package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.TalismanFulfillment;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuneungReadingApiTest {
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private static final UUID PRODUCT_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WalletPurchasePort wallet;
    @MockitoBean ReadingSubjectPort subjects;
    @MockitoBean LinerProvider liner;
    @MockitoBean TalismanFulfillmentPort talismans;

    private UUID userId;
    private UUID otherUserId;
    private UUID personId;
    private UUID quoteId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM generation_attempts");
        jdbc.update("DELETE FROM readings");
        jdbc.update("DELETE FROM reading_purchases");
        jdbc.update("DELETE FROM reading_results");
        jdbc.update("DELETE FROM purchase_quotes");
        jdbc.update("DELETE FROM products WHERE id = ?", PRODUCT_ID);

        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        personId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, code, category, price_currency, price_amount, active, catalog_version)
                VALUES (?, 'SUNEUNG_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, 'test-v1')
                """, PRODUCT_ID);

        when(subjects.getOwnedSubject(userId, personId)).thenReturn(new ReadingSubjectPort.OwnedSubject(personId,
                "민지", new BirthInput(LocalDate.of(2008, 3, 12), LocalTime.of(13, 25), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE)));
        when(liner.name()).thenReturn("test-liner");
        when(liner.generate(any())).thenAnswer(invocation -> valid(invocation.getArgument(0)));
        quoteId = issueQuote(userId, personId);
        when(wallet.debit(eq(userId), eq(quoteId), any())).thenReturn(
                new WalletPurchasePort.DebitResult(UUID.randomUUID(), 15, new WalletBalance(20, 5)));
        when(talismans.create(any())).thenReturn(new TalismanFulfillment(UUID.randomUUID(), Status.PENDING));
    }

    @Test
    void purchasesAndReadsOwnedSuneungSnapshot() throws Exception {
        String response = mvc.perform(post("/api/v1/reading-purchases")
                        .with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "suneung-purchase-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FULFILLED"))
                .andExpect(jsonPath("$.data.balance").value(25))
                .andExpect(jsonPath("$.data.reading.fortuneType").value("SUNEUNG"))
                .andExpect(jsonPath("$.data.reading.productOption").value("READING_WITH_TALISMAN"))
                .andExpect(jsonPath("$.data.reading.subjectDisplayName").value("민지"))
                .andExpect(jsonPath("$.data.reading.event.type").value("CSAT"))
                .andExpect(jsonPath("$.data.reading.event.date").value("2026-11-19"))
                .andExpect(jsonPath("$.data.reading.sections.length()").value(8))
                .andExpect(jsonPath("$.data.reading.generationMode").value("LINER"))
                .andExpect(jsonPath("$.data.reading.talisman.id").isString())
                .andExpect(jsonPath("$.data.reading.talisman.status").value("PENDING"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(userId.toString()))))
                .andReturn().getResponse().getContentAsString();

        String readingId = com.jayway.jsonpath.JsonPath.read(response, "$.data.readingId");
        mvc.perform(get("/api/v1/readings/{id}", readingId).with(user(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(readingId))
                .andExpect(jsonPath("$.data.sections.length()").value(8));
        mvc.perform(get("/api/v1/readings/{id}", readingId).with(user(otherUserId.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("READING_NOT_FOUND"));
    }

    @Test
    void sameQuoteDoesNotDebitOrGenerateTwice() throws Exception {
        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "first-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "second-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reused").value(true));

        verify(wallet, times(1)).debit(eq(userId), eq(quoteId), any());
        verify(liner, times(1)).generate(any());
    }

    @Test
    void concurrentSameQuoteRequestsOnlyDebitOnce() throws Exception {
        CountDownLatch debitStarted = new CountDownLatch(1);
        CountDownLatch releaseDebit = new CountDownLatch(1);
        when(wallet.debit(eq(userId), eq(quoteId), any())).thenAnswer(invocation -> {
            debitStarted.countDown();
            if (!releaseDebit.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test debit timed out");
            return new WalletPurchasePort.DebitResult(UUID.randomUUID(), 15, new WalletBalance(20, 5));
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> mvc.perform(post("/api/v1/reading-purchases")
                            .with(user(userId.toString())).with(csrf())
                            .header("Idempotency-Key", "concurrent-first")
                            .contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andReturn());
            assertThat(debitStarted.await(5, TimeUnit.SECONDS)).isTrue();

            mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                            .header("Idempotency-Key", "concurrent-second")
                            .contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.status").value("CREATED"))
                    .andExpect(jsonPath("$.data.reused").value(true));

            releaseDebit.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
        } finally {
            releaseDebit.countDown();
        }
        verify(wallet, times(1)).debit(eq(userId), eq(quoteId), any());
    }

    @Test
    void linerFailureReturnsDetailedFallbackWithoutRefundingPurchase() throws Exception {
        doThrow(new IllegalStateException("provider unavailable")).when(liner).generate(any());

        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "failed-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FULFILLED"))
                .andExpect(jsonPath("$.data.reading.generationMode").value("FALLBACK"))
                .andExpect(jsonPath("$.data.reading.sections.length()").value(8))
                .andExpect(jsonPath("$.data.reading.sections[0].content").value(
                        org.hamcrest.Matchers.containsString("합격 여부를 단정하는 예측이 아니라")))
                .andExpect(jsonPath("$.data.reading.sections[1].content").value(
                        org.hamcrest.Matchers.containsString("수험번호와 선택과목 확인")))
                .andExpect(jsonPath("$.data.reading.sections[6].content").value(
                        org.hamcrest.Matchers.containsString("4초 들이마시고 6초 내쉬는 호흡")));

        verify(liner, times(3)).generate(any());
        verify(wallet, times(0)).compensate(any(), any(), any());
        assertThatStatus("FULFILLED");
    }

    @Test
    void compensatesAndRejectsUnexpectedDebitAmount() throws Exception {
        UUID transactionId = UUID.randomUUID();
        doReturn(new WalletPurchasePort.DebitResult(transactionId, 14, new WalletBalance(20, 5)))
                .when(wallet).debit(eq(userId), eq(quoteId), any());
        when(wallet.compensate(eq(transactionId), eq("READING_DEBIT_MISMATCH"), any())).thenReturn(
                new WalletPurchasePort.CompensationResult(UUID.randomUUID(), transactionId, new WalletBalance(34, 5)));

        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "mismatch-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PURCHASE_DEBIT_MISMATCH"));

        verify(liner, times(0)).generate(any());
        verify(wallet).compensate(eq(transactionId), eq("READING_DEBIT_MISMATCH"), any());
        assertThatStatus("REFUNDED");
    }

    @Test
    void talismanFulfillmentFailureAlsoCompensatesPurchase() throws Exception {
        doThrow(new IllegalStateException("image pipeline unavailable")).when(talismans)
                .create(any());
        when(wallet.compensate(any(), eq("READING_GENERATION_FAILED"), any())).thenReturn(
                new WalletPurchasePort.CompensationResult(UUID.randomUUID(), UUID.randomUUID(), new WalletBalance(35, 5)));

        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "talisman-failure-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("READING_GENERATION_FAILED"));

        verify(wallet).compensate(any(), eq("READING_GENERATION_FAILED"), any());
        assertThatStatus("REFUNDED");
    }

    @Test
    void hidesAnotherUsersQuoteAndRequiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/reading-purchases").with(user(otherUserId.toString())).with(csrf())
                        .header("Idempotency-Key", "other-user-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUsingQuoteForDifferentPerson() throws Exception {
        UUID anotherPerson = UUID.randomUUID();
        when(subjects.getOwnedSubject(userId, anotherPerson)).thenReturn(new ReadingSubjectPort.OwnedSubject(
                anotherPerson, "서연", new BirthInput(LocalDate.of(2007, 8, 9), LocalTime.of(8, 10), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE)));

        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "wrong-person-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteId":"%s","personId":"%s"}
                                """.formatted(quoteId, anotherPerson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(wallet, times(0)).debit(any(), any(), any());
    }

    private LinerResponse valid(LinerRequest request) {
        return new LinerResponse(request.allowedSections().stream()
                .map(section -> new GeneratedSection(section, section + " 검증된 수능운 문장", List.of("dayMaster.hanja")))
                .toList(), List.of());
    }

    private String body() {
        return """
                {"quoteId":"%s","personId":"%s"}
                """.formatted(quoteId, personId);
    }

    private UUID issueQuote(UUID owner, UUID person) throws Exception {
        String response = mvc.perform(post("/api/v1/quotes/fortune")
                        .with(user(owner.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":"%s"}
                                """.formatted(person)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productCode").value("SUNEUNG_READING_WITH_TALISMAN"))
                .andExpect(jsonPath("$.data.amount").value(15))
                .andExpect(jsonPath("$.data.event.date").value("2026-11-19"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.data.quoteId"));
    }

    private void assertThatStatus(String expected) {
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT status FROM reading_purchases WHERE quote_id = ?", String.class, quoteId)).isEqualTo(expected);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
