package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.fortune.reading.application.GeneralReadingService;
import com.sajuppugi.fortune.reading.application.SuneungReadingService;
import com.sajuppugi.fortune.reading.application.SuneungReadingService.PurchaseResult;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseRequestBindingApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GeneralReadingService general;
    @MockitoBean SuneungReadingService suneung;
    private final UUID actor = UUID.randomUUID();
    private final UUID quote = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();

    @BeforeEach
    void setup() {
        var result = new PurchaseResult(UUID.randomUUID(), null, Status.GENERATING, null, null,
                true, new Price(Price.Currency.TURTLE_SHELL, 15));
        when(general.purchase(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(result);
        when(suneung.purchase(any(), any(), any(), any())).thenReturn(result);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM purchase_request_bindings WHERE actor_id = ?", actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"overall", "love", "wealth", "compatibility", "sinsal"})
    void guardsEveryCategoryBeforeBService(String category) throws Exception {
        String path = "/api/v1/reading-purchases/" + category;
        String body = categoryBody(quote);
        send(path, "intent", body, 202);
        mvc.perform(post(path).with(user(actor.toString())).with(csrf()).header("Idempotency-Key", "intent")
                        .contentType(MediaType.APPLICATION_JSON).content(categoryBody(UUID.randomUUID())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        verify(general, times(1)).purchase(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void guardsLegacyPurchaseAndAllowsIdenticalRetry() throws Exception {
        String path = "/api/v1/reading-purchases";
        send(path, "intent", legacyBody(quote), 202);
        send(path, "intent", legacyBody(quote), 202);
        send(path, "intent", legacyBody(UUID.randomUUID()), 409);
        verify(suneung, times(2)).purchase(any(), any(), any(), any());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void sameKeyCannotSwitchCategoryRoute() throws Exception {
        send("/api/v1/reading-purchases/overall", "intent", categoryBody(quote), 202);
        send("/api/v1/reading-purchases/love", "intent", categoryBody(quote), 409);
        verify(general, times(1)).purchase(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void changedSelectionWithSameQuoteIsRejected() throws Exception {
        String body = categoryBody(quote);
        send("/api/v1/reading-purchases/overall", "intent", body, 202);
        send("/api/v1/reading-purchases/overall", "intent", body.replace("OVERALL_FLOW", "CHANGED"), 409);
    }

    @Test
    void invalidBodyDoesNotReserveKeyAndCorrectedInputCanUseIt() throws Exception {
        send("/api/v1/reading-purchases/overall", "intent", "{}", 400);
        assertThat(count()).isZero();
        send("/api/v1/reading-purchases/overall", "intent", categoryBody(quote), 202);
    }

    @Test
    void authenticationAndCsrfFailuresNeverWriteBinding() throws Exception {
        mvc.perform(post("/api/v1/reading-purchases/overall").with(csrf())
                        .header("Idempotency-Key", "intent").contentType(MediaType.APPLICATION_JSON).content(categoryBody(quote)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reading-purchases/overall").with(user(actor.toString()))
                        .header("Idempotency-Key", "intent").contentType(MediaType.APPLICATION_JSON).content(categoryBody(quote)))
                .andExpect(status().isForbidden());
        assertThat(count()).isZero();
        verifyNoInteractions(general);
    }

    @Test
    void invalidKeysCannotReachPurchaseService() throws Exception {
        for (String key : new String[] {" ", "x".repeat(513)}) {
            send("/api/v1/reading-purchases/overall", key, categoryBody(quote), 400);
        }
        assertThat(count()).isZero();
        verifyNoInteractions(general);
    }

    @Test
    void reorderedJsonAndExplicitNullReplayAsSameTypedRequest() throws Exception {
        send("/api/v1/reading-purchases", "intent", legacyBody(quote), 202);
        String reordered = "{\"fortuneType\":null,\"personId\":\"%s\",\"quoteId\":\"%s\"}".formatted(person, quote);
        send("/api/v1/reading-purchases", "intent", reordered, 202);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void businessFailureKeepsIntentButDoesNotBlockIdenticalRetry() throws Exception {
        when(suneung.purchase(any(), any(), any(), any())).thenThrow(new com.sajuppugi.common.api.ApiException(
                com.sajuppugi.common.api.ErrorCode.READING_FULFILLMENT_UNAVAILABLE));
        send("/api/v1/reading-purchases", "intent", legacyBody(quote), 503);
        send("/api/v1/reading-purchases", "intent", legacyBody(quote), 503);
        send("/api/v1/reading-purchases", "intent", legacyBody(UUID.randomUUID()), 409);
        verify(suneung, times(2)).purchase(any(), any(), any(), any());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void missingKeyAndInvalidPrincipalDoNotWrite() throws Exception {
        mvc.perform(post("/api/v1/reading-purchases/overall").with(user(actor.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(categoryBody(quote)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reading-purchases/overall").with(user("not-a-user-id")).with(csrf())
                        .header("Idempotency-Key", "intent").contentType(MediaType.APPLICATION_JSON).content(categoryBody(quote)))
                .andExpect(status().isUnauthorized());
        assertThat(count()).isZero();
        verifyNoInteractions(general);
    }

    private void send(String path, String key, String body, int expected) throws Exception {
        mvc.perform(post(path).with(user(actor.toString())).with(csrf()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    private String categoryBody(UUID id) {
        return """
                {"quoteId":"%s","personId":"%s","counterpartPersonId":"%s",
                "relationType":"LOVER","productOption":"READING_ONLY","questionKey":"OVERALL_FLOW"}
                """.formatted(id, person, UUID.nameUUIDFromBytes("synthetic-counterpart".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private String legacyBody(UUID id) {
        return "{\"quoteId\":\"%s\",\"personId\":\"%s\"}".formatted(id, person);
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM purchase_request_bindings WHERE actor_id = ?", Integer.class, actor);
    }
}
