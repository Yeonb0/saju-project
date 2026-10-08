package com.sajuppugi.fortune.reading;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.fortune.talisman.application.SuneungTalismanPort;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
class GeneralReadingApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WalletPurchasePort wallet;
    @MockitoBean ReadingSubjectPort subjects;
    @MockitoBean LinerProvider liner;
    @MockitoBean SuneungTalismanPort talismans;

    private UUID userId;
    private UUID otherUserId;
    private UUID personId;
    private UUID counterpartId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM generation_attempts");
        jdbc.update("DELETE FROM readings");
        jdbc.update("DELETE FROM reading_purchases");
        jdbc.update("DELETE FROM reading_results");
        jdbc.update("DELETE FROM purchase_quotes");
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        personId = UUID.randomUUID();
        counterpartId = UUID.randomUUID();
        when(subjects.getOwnedSubject(userId, personId)).thenReturn(subject(personId, "민지", 1998, 3, 12));
        when(subjects.getOwnedSubject(userId, counterpartId)).thenReturn(subject(counterpartId, "서준", 1997, 8, 9));
        when(liner.name()).thenReturn("test-liner");
        when(liner.generate(any())).thenAnswer(invocation -> valid(invocation.getArgument(0)));
        when(wallet.debit(eq(userId), any(), any())).thenAnswer(invocation -> {
            UUID debitQuoteId = invocation.getArgument(1, UUID.class);
            int amount = jdbc.queryForObject("SELECT price_amount FROM purchase_quotes WHERE id = ?",
                    Integer.class, debitQuoteId);
            return new WalletPurchasePort.DebitResult(UUID.randomUUID(), amount, new WalletBalance(30, 5));
        });
        when(talismans.create(eq(userId), any(), any(), any())).thenReturn(
                new SuneungTalismanPort.TalismanFulfillment(UUID.randomUUID(), SuneungTalismanPort.Status.PENDING));
    }

    @Test
    void purchasesAllFiveGeneralFortunesWithTheirExactSections() throws Exception {
        assertPurchase("OVERALL", "OVERALL_FLOW", 8);
        assertPurchase("LOVE", "CURRENT_RELATIONSHIP", 6);
        assertPurchase("WEALTH", "OVERALL_WEALTH", 7);
        assertPurchase("SINSAL", "OVERALL_SINSAL", 4);

        String compatibility = body("COMPATIBILITY", "OVERALL_MATCH", true);
        UUID quoteId = issueQuote(compatibility, "COMPATIBILITY_READING_ONLY");
        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "purchase-compatibility")
                        .contentType(MediaType.APPLICATION_JSON).content(withQuote(compatibility, quoteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reading.fortuneType").value("COMPATIBILITY"))
                .andExpect(jsonPath("$.data.reading.counterpart.personId").value(counterpartId.toString()))
                .andExpect(jsonPath("$.data.reading.counterpart.displayName").value("서준"))
                .andExpect(jsonPath("$.data.reading.counterpart.relationType").value("LOVER"))
                .andExpect(jsonPath("$.data.reading.sections.length()").value(6));
    }

    @Test
    void categoryEndpointsReturnCategorySpecificNamedFields() throws Exception {
        String overallId = categoryPurchase("overall", "OVERALL_FLOW", false,
                "currentFlow", "relationships", "studyAndWork", "wealthFlow", "condition", "luckyPoint");
        String loveId = categoryPurchase("love", "CURRENT_RELATIONSHIP", false,
                "currentFlow", "goodPeriod", "caution", "actionTip");
        String wealthId = categoryPurchase("wealth", "OVERALL_WEALTH", false,
                "wealthFlow", "income", "spendingCaution", "goodPeriod", "actionTip");
        String compatibilityId = categoryPurchase("compatibility", "OVERALL_MATCH", true,
                "matchStrength", "matchConflict", "communication", "relationshipTip");
        String sinsalId = categoryPurchase("sinsal", "OVERALL_SINSAL", false,
                "specialStars", "balancingGuide", "missingElement");

        mvc.perform(get("/api/v1/readings/love/{id}", loveId).with(user(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.questionKey").value("CURRENT_RELATIONSHIP"))
                .andExpect(jsonPath("$.data.goodPeriod.content").isString())
                .andExpect(jsonPath("$.data.sections").doesNotExist());
        mvc.perform(get("/api/v1/readings/love").with(user(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(loveId))
                .andExpect(jsonPath("$.data.items[0].summary.content").isString());
        mvc.perform(get("/api/v1/readings/love/{id}", compatibilityId).with(user(userId.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("READING_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(List.of(overallId, loveId, wealthId, compatibilityId, sinsalId))
                .doesNotHaveDuplicates();
    }

    @Test
    void rejectsQuestionFromAnotherFortuneAndIncompleteCompatibility() throws Exception {
        mvc.perform(post("/api/v1/quotes/fortune").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("LOVE", "INVESTMENT", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_KEY"));

        mvc.perform(post("/api/v1/quotes/fortune").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("COMPATIBILITY", "OVERALL_MATCH", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMPATIBILITY_PERSON_REQUIRED"));
        verify(wallet, times(0)).debit(any(), any(), any());
    }

    @Test
    void preventsChangingQuoteContextAtPurchase() throws Exception {
        String quoteBody = body("LOVE", "CURRENT_RELATIONSHIP", false);
        UUID quoteId = issueQuote(quoteBody, "LOVE_READING_ONLY");

        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "tampered-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withQuote(body("LOVE", "MARRIAGE", false), quoteId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(wallet, times(0)).debit(any(), any(), any());
    }

    @Test
    void purchasesTalismanBundleAtCatalogPrice() throws Exception {
        String request = body("LOVE", "CURRENT_RELATIONSHIP", false)
                .replace("READING_ONLY", "READING_WITH_TALISMAN");
        UUID quoteId = issueQuote(request, "LOVE_READING_WITH_TALISMAN", 15);
        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "love-with-talisman")
                        .contentType(MediaType.APPLICATION_JSON).content(withQuote(request, quoteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.charged.amount").value(15))
                .andExpect(jsonPath("$.data.reading.productOption").value("READING_WITH_TALISMAN"))
                .andExpect(jsonPath("$.data.reading.talisman.id").isString())
                .andExpect(jsonPath("$.data.reading.talisman.status").value("PENDING"));
        verify(talismans).create(eq(userId), any(), any(), eq("love-2026.10.08"));
    }

    @Test
    void listsWithFiltersAndCursorAndProtectsDetailOwnership() throws Exception {
        String firstId = purchase("LOVE", "CURRENT_RELATIONSHIP");
        purchase("WEALTH", "OVERALL_WEALTH");
        purchase("OVERALL", "OVERALL_FLOW");

        String page = mvc.perform(get("/api/v1/readings").with(user(userId.toString())).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(page, "$.data.nextCursor");
        mvc.perform(get("/api/v1/readings").with(user(userId.toString()))
                        .param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(get("/api/v1/readings").with(user(userId.toString())).param("fortuneType", "LOVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].fortuneType").value("LOVE"));
        mvc.perform(get("/api/v1/readings").with(user(userId.toString())).param("personId", personId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3));
        mvc.perform(get("/api/v1/readings/{id}", firstId).with(user(otherUserId.toString())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("READING_NOT_FOUND"));
        mvc.perform(get("/api/v1/readings").with(user(userId.toString())).param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    private void assertPurchase(String type, String questionKey, int sections) throws Exception {
        String request = body(type, questionKey, false);
        UUID quoteId = issueQuote(request, type + "_READING_ONLY");
        mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "purchase-" + type.toLowerCase())
                        .contentType(MediaType.APPLICATION_JSON).content(withQuote(request, quoteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balance").value(35))
                .andExpect(jsonPath("$.data.charged.currency").value("TURTLE_SHELL"))
                .andExpect(jsonPath("$.data.charged.amount").value(10))
                .andExpect(jsonPath("$.data.calculationVersion").value("manse-2026.10-v1"))
                .andExpect(jsonPath("$.data.generationVersion").value("liner-general-reading-v1"))
                .andExpect(jsonPath("$.data.reading.fortuneType").value(type))
                .andExpect(jsonPath("$.data.reading.questionKey").value(questionKey))
                .andExpect(jsonPath("$.data.reading.sections.length()").value(sections))
                .andExpect(jsonPath("$.data.reading.talisman").doesNotExist());
    }

    private String categoryPurchase(String category, String questionKey, boolean compatibility,
                                    String... expectedFields) throws Exception {
        String extra = compatibility ? """
                ,"counterpartPersonId":"%s","relationType":"LOVER"
                """.formatted(counterpartId).strip() : "";
        String quoteRequest = """
                {"personId":"%s","productOption":"READING_ONLY","questionKey":"%s"%s}
                """.formatted(personId, questionKey, extra);
        String quoteResponse = mvc.perform(post("/api/v1/quotes/fortune/{category}", category)
                        .with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quoteRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productCode").value(category.toUpperCase() + "_READING_ONLY"))
                .andExpect(jsonPath("$.data.questionKey").value(questionKey))
                .andReturn().getResponse().getContentAsString();
        UUID categoryQuoteId = UUID.fromString(JsonPath.read(quoteResponse, "$.data.quoteId"));
        String purchaseRequest = quoteRequest.substring(0, quoteRequest.length() - 2)
                + ",\"quoteId\":\"" + categoryQuoteId + "\"}";
        var action = mvc.perform(post("/api/v1/reading-purchases/{category}", category)
                        .with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", "category-" + category)
                        .contentType(MediaType.APPLICATION_JSON).content(purchaseRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reading.meta.questionKey").value(questionKey))
                .andExpect(jsonPath("$.data.reading.summary.content").isString())
                .andExpect(jsonPath("$.data.reading.sections").doesNotExist());
        for (String field : expectedFields) {
            action.andExpect(jsonPath("$.data.reading." + field + ".content").isString());
        }
        if (compatibility) {
            action.andExpect(jsonPath("$.data.reading.counterpart.personId").value(counterpartId.toString()))
                    .andExpect(jsonPath("$.data.reading.counterpart.relationType").value("LOVER"));
        }
        String response = action.andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.readingId");
    }

    private String purchase(String type, String questionKey) throws Exception {
        String request = body(type, questionKey, false);
        UUID quoteId = issueQuote(request, type + "_READING_ONLY");
        String response = mvc.perform(post("/api/v1/reading-purchases").with(user(userId.toString())).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(withQuote(request, quoteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.readingId");
    }

    private UUID issueQuote(String body, String productCode) throws Exception {
        return issueQuote(body, productCode, 10);
    }

    private UUID issueQuote(String body, String productCode, int amount) throws Exception {
        String response = mvc.perform(post("/api/v1/quotes/fortune").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productCode").value(productCode))
                .andExpect(jsonPath("$.data.amount").value(amount))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.data.quoteId"));
    }

    private String body(String type, String questionKey, boolean compatibility) {
        String counterpart = compatibility ? """
                ,"counterpartPersonId":"%s","relationType":"LOVER"
                """.formatted(counterpartId).strip() : "";
        return """
                {"personId":"%s","fortuneType":"%s","productOption":"READING_ONLY","questionKey":"%s"%s}
                """.formatted(personId, type, questionKey, counterpart);
    }

    private String withQuote(String request, UUID quoteId) {
        return request.substring(0, request.length() - 2) + ",\"quoteId\":\"" + quoteId + "\"}";
    }

    private LinerResponse valid(LinerRequest request) {
        return new LinerResponse(request.allowedSections().stream()
                .map(section -> new GeneratedSection(section, section + " 검증된 일반 운세 문장",
                        List.of("dayMaster.hanja")))
                .toList(), List.of());
    }

    private ReadingSubjectPort.OwnedSubject subject(UUID id, String name, int year, int month, int day) {
        return new ReadingSubjectPort.OwnedSubject(id, name,
                new BirthInput(LocalDate.of(year, month, day), LocalTime.of(13, 25), false,
                        BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
