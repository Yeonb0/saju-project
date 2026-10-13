package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.payment.application.TopUpUseCase.Confirmation;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import com.sajuppugi.payment.infrastructure.TossPaymentProperties;
import com.sajuppugi.payment.infrastructure.TossPaymentProvider;
import com.sajuppugi.payment.port.PaymentProviderPort.Outcome;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

class TossPaymentProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID orderId = UUID.randomUUID();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> idempotency = new AtomicReference<>();
    private final AtomicReference<JsonNode> payload = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private TossPaymentProvider provider;
    private volatile String body;
    private volatile String cancelBody;
    private volatile int cancelStatus = 200;
    private volatile int status;
    private volatile long delay;

    @BeforeEach
    void setup() throws Exception {
        status = 200;
        body = payment("DONE").toString();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            path.set(exchange.getRequestURI().getPath());
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] incoming = exchange.getRequestBody().readAllBytes();
            if (incoming.length > 0) payload.set(mapper.readTree(incoming));
            try {
                if (delay > 0) Thread.sleep(delay);
                if (status == 302) exchange.getResponseHeaders().set("Location", baseUrl() + "/redirected");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] response = (exchange.getRequestURI().getPath().endsWith("/cancel") && cancelBody != null ? cancelBody : body).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("/cancel") ? cancelStatus : status, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        provider = create(baseUrl(), "test_gsk_synthetic", Duration.ofSeconds(2), testEnvironment());
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
        executor.shutdownNow();
        executor.close();
    }

    @Test
    void confirmsUsingBasicAuthStableOrderKeyAndExactServerAmount() throws Exception {
        var result = provider.confirm(orderId, new Confirmation("payment", 5000));
        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.approves(intent(orderId, "payment", 5000))).isTrue();
        assertThat(result.toString()).doesNotContain(result.paymentKeyHash(), "test_gsk_synthetic");
        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("test_gsk_synthetic:".getBytes(StandardCharsets.UTF_8)));
        assertThat(path.get()).isEqualTo("/v1/payments/confirm");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(idempotency.get()).isEqualTo("top-up-confirm-" + orderId);
        assertThat(payload.get().path("amount").intValue()).isEqualTo(5000);
        assertThat(payload.get().path("orderId").textValue()).isEqualTo(orderId.toString());
        assertThat(payload.get().path("paymentKey").textValue()).isEqualTo("payment");
        provider.confirm(orderId, new Confirmation("payment", 5000));
        assertThat(idempotency.get()).isEqualTo("top-up-confirm-" + orderId);
    }

    @Test
    void lookupByOrderReturnsIdentityThatMustMatchSavedIntentBeforeGrant() throws Exception {
        var result = provider.lookup(orderId);
        assertThat(path.get()).isEqualTo("/v1/payments/orders/" + orderId);
        assertThat(method.get()).isEqualTo("GET");
        assertThat(idempotency.get()).isNull();
        assertThat(result.approves(intent(orderId, "payment", 5000))).isTrue();
        assertThat(result.approves(intent(UUID.randomUUID(), "payment", 5000))).isFalse();
        assertThat(result.approves(intent(orderId, "different", 5000))).isFalse();
        assertThat(result.approves(intent(orderId, "payment", 1))).isFalse();
        assertThat(result.approves(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"READY,PENDING", "IN_PROGRESS,PENDING", "WAITING_FOR_DEPOSIT,PENDING", "CANCELED,CANCELED",
            "ABORTED,DECLINED", "EXPIRED,DECLINED", "PARTIAL_CANCELED,UNKNOWN", "NEW_STATUS,UNKNOWN"})
    void nonApprovedStatusesNeverAuthorizeGrants(String state, Outcome expected) throws Exception {
        body = payment(state).toString();
        var result = provider.lookup(orderId);
        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.approves(intent(orderId, "payment", 5000))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"orderId", "paymentKey", "currency", "type", "totalAmount", "balanceAmount", "approvedAt"})
    void missingRequiredEvidenceNeverApproves(String field) {
        var response = payment("DONE");
        response.remove(field);
        body = response.toString();
        assertThat(provider.confirm(orderId, new Confirmation("payment", 5000)).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({"orderId,other", "paymentKey,other", "currency,USD", "type,BILLING", "totalAmount,5000", "approvedAt,invalid"})
    void mismatchedOrWrongTypedEvidenceNeverApproves(String field, String value) {
        body = payment("DONE").put(field, value).toString();
        assertThat(provider.confirm(orderId, new Confirmation("payment", 5000)).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void wrongAmountAndPartiallyCanceledBalanceNeverApprove() {
        body = payment("DONE").put("totalAmount", 4999).toString();
        assertThat(provider.confirm(orderId, new Confirmation("payment", 5000)).outcome()).isEqualTo(Outcome.UNKNOWN);
        body = payment("DONE").put("balanceAmount", 4999).toString();
        assertThat(provider.lookup(orderId).outcome()).isEqualTo(Outcome.UNKNOWN);
        body = payment("DONE").put("totalAmount", 5000.5).toString();
        assertThat(provider.lookup(orderId).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 409, 429, 500, 503})
    void errorStatusDoesNotMeanDeclineOrExposeProviderBody(int httpStatus) {
        status = httpStatus;
        body = "{\"code\":\"private-code\",\"message\":\"private-provider-body\"}";
        var result = provider.confirm(orderId, new Confirmation("payment", 5000));
        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(result.safeFailureCode()).isEqualTo("TOSS_HTTP_" + httpStatus);
        assertThat(result.toString()).doesNotContain("private-provider-body", "private-code");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void malformedAndOversizedBodiesDoNotApprove() {
        body = "{bad-json";
        assertThat(provider.lookup(orderId).safeFailureCode()).isEqualTo("TOSS_INVALID_RESPONSE");
        body = "x".repeat(262145);
        assertThat(provider.lookup(orderId).safeFailureCode()).isEqualTo("TOSS_RESPONSE_TOO_LARGE");
    }

    @Test
    void readTimeoutIsUnknownAndDoesNotAutomaticallyRetry() {
        provider = create(baseUrl(), "test_gsk_synthetic", Duration.ofMillis(100), testEnvironment());
        delay = 500;
        var result = provider.confirm(orderId, new Confirmation("payment", 5000));
        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(result.safeFailureCode()).isEqualTo("TOSS_TRANSPORT_ERROR");
        assertThat(requests.get()).isEqualTo(1);
        delay = 0;
        assertThat(provider.lookup(orderId).outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(method.get()).isEqualTo("GET");
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void redirectIsNotFollowedWithCredentials() {
        status = 302;
        assertThat(provider.lookup(orderId).safeFailureCode()).isEqualTo("TOSS_HTTP_302");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void unsupportedCancellationReasonMakesNoRequest() {
        assertThat(provider.cancel(orderId, UUID.randomUUID(), 5000, "TEST").safeFailureCode())
                .isEqualTo("TOSS_CANCEL_INVALID");
        assertThat(requests.get()).isZero();
    }

    @Test void verifiedFullCancelUsesStableRefundKeyAndExactAmount() throws Exception {
        cancelBody = payment("CANCELED").toString();
        UUID refund = UUID.randomUUID();
        assertThat(provider.cancelVerified(intent(orderId, "payment", 5000), refund).outcome()).isEqualTo(Outcome.CANCELED);
        assertThat(path.get()).isEqualTo("/v1/payments/payment/cancel");
        assertThat(idempotency.get()).isEqualTo("top-up-refund-" + refund);
        assertThat(payload.get().path("cancelAmount").intValue()).isEqualTo(5000);
        provider.cancelVerified(intent(orderId, "payment", 5000), refund);
        assertThat(idempotency.get()).isEqualTo("top-up-refund-" + refund);
    }
    @Test void alreadyCanceledPaymentDoesNotCallCancelAgain() throws Exception {
        body = payment("CANCELED").toString();
        assertThat(provider.cancelVerified(intent(orderId, "payment", 5000), UUID.randomUUID()).outcome()).isEqualTo(Outcome.CANCELED);
        assertThat(requests.get()).isEqualTo(1);
        assertThat(method.get()).isEqualTo("GET");
    }
    @Test void differentSavedPaymentKeyCannotCancel() throws Exception {
        assertThat(provider.cancelVerified(intent(orderId, "different", 5000), UUID.randomUUID()).outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(requests.get()).isEqualTo(1);
        assertThat(method.get()).isEqualTo("GET");
    }
    @ParameterizedTest @ValueSource(strings = {"DONE", "PARTIAL_CANCELED", "IN_PROGRESS"})
    void cancellationResponseMustProveFullCancel(String status) {
        cancelBody = payment(status).toString();
        assertThat(provider.cancel(orderId, UUID.randomUUID(), 5000, "UNUSED_TOP_UP").outcome()).isEqualTo(Outcome.UNKNOWN);
    }
    @Test void canceledWithNonzeroOrMissingBalanceCannotFinalizeRefund() {
        var missing = payment("CANCELED"); missing.remove("balanceAmount");
        for (String response : java.util.List.of(payment("CANCELED").put("balanceAmount", 1).toString(), missing.toString())) {
            body = response;
            assertThat(provider.lookup(orderId).outcome()).isEqualTo(Outcome.UNKNOWN);
        }
    }
    @Test void verifiedPartialCancellationPreservesIdentityButNeverApprovesOrCancels() throws Exception {
        body = payment("PARTIAL_CANCELED").put("balanceAmount", 2500).toString();
        var result = provider.lookup(orderId);
        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(result.safeFailureCode()).isEqualTo("PAYMENT_PARTIAL_CANCEL_REVIEW");
        assertThat(result.matches(intent(orderId, "payment", 5000))).isTrue();
        assertThat(provider.cancelVerified(intent(orderId, "payment", 5000), UUID.randomUUID()).outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(method.get()).isEqualTo("GET");
    }
    @Test void overflowingCancellationAmountCannotWrapToExpectedAmount() throws Exception {
        cancelBody = payment("CANCELED").put("totalAmount", new java.math.BigInteger("18446744073709556616")).toString();
        assertThat(provider.cancelVerified(intent(orderId, "payment", 5000), UUID.randomUUID()).outcome()).isEqualTo(Outcome.UNKNOWN);
    }
    @Test void remainingCancellationSendsProratedAmountAndValidatesCompletedCancelHistory() throws Exception {
        cancelBody = partialPayment(4000).toString();
        var intent = intent(orderId, "payment", 5000);
        var result = provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000);
        assertThat(result.confirmsRefund(intent, 4000)).isTrue();
        assertThat(payload.get().path("cancelAmount").asInt()).isEqualTo(4000);
        body = cancelBody; int before = requests.get();
        assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000).confirmsRefund(intent, 4000)).isTrue();
        assertThat(requests.get()).isEqualTo(before + 1);
        assertThat(method.get()).isEqualTo("GET");
    }
    @Test void wrongPartialAmountsPendingHistoryDuplicateKeysAndFullCancellationAreNotPartialProof() throws Exception {
        var intent = intent(orderId, "payment", 5000);
        for (ObjectNode invalid : java.util.List.of(partialPayment(3000), partialPayment(4000).put("balanceAmount", 2000),
                partialPayment(4000).put("totalAmount", 6000))) {
            cancelBody = invalid.toString();
            assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000).confirmsRefund(intent, 4000)).isFalse();
        }
        var pending = partialPayment(4000); ((ObjectNode) pending.path("cancels").get(0)).put("cancelStatus", "PENDING");
        cancelBody = pending.toString();
        assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000).confirmsRefund(intent, 4000)).isFalse();
        var duplicated = partialPayment(2000); duplicated.withArray("cancels").add(duplicated.path("cancels").get(0).deepCopy());
        duplicated.put("balanceAmount", 1000); cancelBody = duplicated.toString();
        assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000).confirmsRefund(intent, 4000)).isFalse();
        cancelBody = payment("CANCELED").toString();
        assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000).confirmsRefund(intent, 4000)).isFalse();
    }
    @Test void unsupportedPartialMethodAndVirtualAccountDoNotSendCancellation() throws Exception {
        var intent = intent(orderId, "payment", 5000);
        body = payment("DONE").put("isPartialCancelable", false).toString();
        assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 4000).outcome()).isEqualTo(Outcome.CANCEL_REJECTED);
        assertThat(method.get()).isEqualTo("GET");
        body = payment("DONE").put("method", "가상계좌").toString();
        assertThat(provider.cancelRemainingVerified(intent, UUID.randomUUID(), 5000).outcome()).isEqualTo(Outcome.CANCEL_REJECTED);
        assertThat(method.get()).isEqualTo("GET");
    }
    private ObjectNode partialPayment(int amount) {
        var payment = payment("PARTIAL_CANCELED").put("balanceAmount", 5000 - amount);
        payment.putArray("cancels").addObject().put("cancelAmount", amount).put("cancelStatus", "DONE").put("transactionKey", "synthetic-cancel");
        return payment;
    }
    @Test void explicitRefundRejectionNeedsFreshUnchangedApprovalBeforeReturningFunds() throws Exception {
        cancelStatus = 400;
        cancelBody = "{\"code\":\"REFUND_REJECTED\",\"message\":\"private provider detail\"}";
        var result = provider.cancelVerified(intent(orderId, "payment", 5000), UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(Outcome.CANCEL_REJECTED);
        assertThat(result.matches(intent(orderId, "payment", 5000))).isTrue();
        assertThat(result.safeFailureCode()).isEqualTo("CASH_REFUND_REJECTED");
        assertThat(result.toString()).doesNotContain("private provider detail");
        assertThat(requests.get()).isEqualTo(4);
    }
    @ParameterizedTest @ValueSource(strings = {"NOT_CANCELABLE_PAYMENT", "NOT_CANCELABLE_AMOUNT", "PROVIDER_ERROR", "ALREADY_REFUND_PAYMENT"})
    void ambiguousCancelErrorsCannotAuthorizeRestoringFunds(String code) throws Exception {
        cancelStatus = 400;
        cancelBody = "{\"code\":\"" + code + "\"}";
        assertThat(provider.cancelVerified(intent(orderId, "payment", 5000), UUID.randomUUID()).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void configurationRejectsLiveKeysClientKeysUnsafeHostsAndTimeoutsWithoutLeakingValues() {
        for (String key : java.util.List.of("", "live_gsk_synthetic", "test_ck_synthetic", "test_sk_synthetic", "secret:bad")) {
            assertThatThrownBy(() -> create(baseUrl(), key, Duration.ofSeconds(2), testEnvironment()))
                    .hasMessage("Toss test API configuration is invalid");
        }
        for (String url : java.util.List.of("https://example.com", "http://api.tosspayments.com", baseUrl() + "?private=key")) {
            assertThatThrownBy(() -> create(url, "test_gsk_synthetic", Duration.ofSeconds(2), testEnvironment()))
                    .hasMessage("Toss test API configuration is invalid");
        }
        assertThatThrownBy(() -> create(baseUrl(), "test_gsk_synthetic", Duration.ZERO, testEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create(baseUrl(), "test_gsk_synthetic", Duration.ofSeconds(61), testEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create(baseUrl(), "test_gsk_synthetic", Duration.ofSeconds(2), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new TossPaymentProperties(baseUrl(), "test_gsk_synthetic", Duration.ofSeconds(1), Duration.ofSeconds(1)).toString())
                .doesNotContain("test_gsk_synthetic");
        assertThat(create("https://api.tosspayments.com", "test_gsk_synthetic", Duration.ofSeconds(2), new MockEnvironment()))
                .isNotNull();
        var production = new MockEnvironment();
        production.setActiveProfiles("production", "test");
        assertThatThrownBy(() -> create("https://api.tosspayments.com", "test_gsk_synthetic", Duration.ofSeconds(2), production))
                .hasMessage("Toss test API configuration is invalid");
        var staging = new MockEnvironment();
        staging.setActiveProfiles("staging", "test");
        assertThatThrownBy(() -> create(baseUrl(), "test_gsk_synthetic", Duration.ofSeconds(2), staging))
                .hasMessage("Toss test API configuration is invalid");
    }

    @Test
    void paymentKeysOverProviderLimitAreRejectedBeforeNetworkCall() {
        assertThatThrownBy(() -> provider.confirm(orderId, new Confirmation("x".repeat(201), 5000)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid payment key");
        assertThat(requests.get()).isZero();
    }

    private ObjectNode payment(String state) {
        return mapper.createObjectNode().put("orderId", orderId.toString()).put("paymentKey", "payment")
                .put("currency", "KRW").put("type", "NORMAL").put("totalAmount", 5000)
                .put("balanceAmount", "CANCELED".equals(state) ? 0 : 5000).put("status", state).put("approvedAt", "2026-10-10T10:00:00+09:00");
    }

    private TossPaymentProvider create(String url, String key, Duration timeout, MockEnvironment environment) {
        return new TossPaymentProvider(RestClient.builder(), mapper,
                new TossPaymentProperties(url, key, Duration.ofSeconds(1), timeout), environment);
    }

    private MockEnvironment testEnvironment() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return environment;
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private TopUpConfirmationIntent intent(UUID id, String key, int amount) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        return new TopUpConfirmationIntent(id, hash, amount, Instant.now());
    }
}
