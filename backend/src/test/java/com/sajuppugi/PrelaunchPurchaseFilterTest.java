package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.infrastructure.PrelaunchPurchaseFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PrelaunchPurchaseFilterTest {
    private final PrelaunchPurchaseFilter filter = new PrelaunchPurchaseFilter(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/direct-payment-batches", "/api/v1/direct-payment-batches/id/confirm",
            "/api/v1/direct-payment-batches/id/reading-fulfillment",
            "/api/v1/direct-payment-batches/id/suneung-fulfillment",
            "/api/v1/direct-payment-batches/id/talisman-fulfillment",
            "/api/v1/top-up-orders", "/api/v1/reading-purchases/suneung",
            "/api/v1/gift-orders", "/api/v1/gift-batches", "/api/v1/direct-gift-batches",
            "/api/v1/public/gifts/result", "/api/v1/webhooks/toss"})
    void blocksBeforeAnyControllerOrBusinessSideEffect(String path) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(new ObjectMapper().readTree(response.getContentAsByteArray()).path("code").asText())
                .isEqualTo("PAYMENT_FULFILLMENT_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void preservesReadAndPreflightRequests(String method) throws Exception {
        var reached = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest(method, "/api/v1/direct-payment-batches/id"),
                new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/people", "/api/v1/auth/logout", "/api/v1/me/consents",
            "/api/v1/direct-payment-batches-unrelated"})
    void leavesNonPurchaseWritesToExistingAuthorization(String path) throws Exception {
        var reached = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("POST", path), new MockHttpServletResponse(),
                (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }

    @Test
    void contextPathDoesNotBypassPurchaseBlock() throws Exception {
        var request = new MockHttpServletRequest("DELETE", "/server/api/v1/gift-orders/id");
        request.setContextPath("/server");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("must not reach controller"); });
        assertThat(response.getStatus()).isEqualTo(503);
    }
}
