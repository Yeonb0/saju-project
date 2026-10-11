package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.TopUpProduct;
import com.sajuppugi.payment.application.TopUpConfirmationService;
import com.sajuppugi.payment.application.TopUpOrderService;
import com.sajuppugi.payment.application.TopUpPaymentService;
import com.sajuppugi.payment.application.TopUpUseCase.Confirmation;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.payment.infrastructure.JdbcTopUpExecutions;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.payment.port.PaymentProviderPort.Outcome;
import com.sajuppugi.payment.port.PaymentProviderPort.Result;
import com.sajuppugi.wallet.infrastructure.JdbcWalletTopUps;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {"app.toss.recovery.enabled=true", "app.toss.recovery.poll-delay=PT1H"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopUpPaymentFlowTest {
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TopUpOrderService orders;
    @Autowired TopUpPaymentService payments;
    @Autowired TopUpConfirmationService bindings;
    @Autowired JdbcTopUpExecutions executions;
    @Autowired com.sajuppugi.payment.application.TopUpRecoveryScheduler scheduler;
    @MockitoBean CatalogUseCase catalog;
    @MockitoBean PaymentProviderPort provider;
    @MockitoBean Clock clock;
    @MockitoSpyBean JdbcWalletTopUps grants;
    UUID user;

    @BeforeEach void setup() {
        user = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        catalog(50, 6);
        when(provider.confirm(any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return approved(call.getArgument(0));
        });
        when(provider.lookup(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return approved(call.getArgument(0));
        });
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM top_up_confirmation_requests WHERE user_id = ?", user);
        jdbc.update("DELETE FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", user);
        jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", user);
        jdbc.update("DELETE FROM wallet_transaction_lines WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", user);
    }

    @Test void httpApprovalGrantsOnceAndReplaysEvenWithAnotherRequestKey() throws Exception {
        var order = order("create");
        when(catalog.availableTopUps()).thenReturn(List.of());
        for (String key : List.of("confirm", "confirm", "alias")) {
            mvc.perform(confirmRequest(order.id(), key))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CREDITED"))
                    .andExpect(jsonPath("$.data.processing").value(false))
                    .andExpect(jsonPath("$.data.walletBalance").value(56))
                    .andExpect(jsonPath("$.data.grantedShellAmount").value(56))
                    .andExpect(jsonPath("$.data.nextAction").value("VIEW_WALLET"));
        }
        assertLedger(1, 56, 2);
        verify(provider, times(1)).confirm(eq(order.id()), any());
        verify(provider, never()).lookup(any());
    }

    @Test void paidAndBonusUseCalendarYearsAndZeroBonusDoesNotCreateAnEmptyLot() {
        Instant leap = Instant.parse("2028-02-29T03:00:00Z");
        when(clock.instant()).thenReturn(leap);
        var first = order("first");
        approve(first);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM wallet_lots WHERE wallet_user_id = ? AND balance_type = 'PAID'",
                java.sql.Timestamp.class, user).toInstant()).isEqualTo(Instant.parse("2033-02-28T03:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT expires_at FROM wallet_lots WHERE wallet_user_id = ? AND balance_type = 'BONUS'",
                java.sql.Timestamp.class, user).toInstant()).isEqualTo(Instant.parse("2029-02-28T03:00:00Z"));
        catalog(10, 0);
        var second = order("second");
        approve(second);
        assertLedger(2, 66, 3);
        assertThat(payments.get(user, first.id()).walletBalance().balance()).isEqualTo(56);
        assertThat(payments.get(user, second.id()).walletBalance().balance()).isEqualTo(66);
    }

    @Test void httpChecksSessionCsrfOwnershipAmountPathAndImmutablePayment() throws Exception {
        var order = order("create");
        mvc.perform(post("/api/v1/top-up-orders/{id}/confirm", order.id()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "key").content(body(order.id(), 5000)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/top-up-orders/{id}/confirm", order.id()).with(user(user.toString()))
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "key").content(body(order.id(), 5000)))
                .andExpect(status().isForbidden());
        mvc.perform(confirmRequest(order.id(), "key").with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(confirmRequest(order.id(), "key").content(body(order.id(), 4999)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        mvc.perform(confirmRequest(order.id(), "key").content(body(UUID.randomUUID(), 5000)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
        approve(order);
        mvc.perform(confirmRequest(order.id(), "confirm").content(body(order.id(), 5000).replace(payment(order.id()), "changed")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/top-up-orders/{id}", order.id()).with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        assertLedger(1, 56, 2);
    }

    @Test void unknownApprovalReturns202ThenLookupRecoversWithoutRepeatingApproval() throws Exception {
        var order = order("create");
        when(provider.confirm(any(), any())).thenReturn(unknown(order.id()));
        mvc.perform(confirmRequest(order.id(), "confirm"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.processing").value(true))
                .andExpect(jsonPath("$.data.walletBalance").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.grantedShellAmount").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.nextAction").value("POLL"));
        assertThat(count("wallet_transactions")).isZero();
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        scheduler.poll();
        assertThat(payments.get(user, order.id()).order().status()).isEqualTo(TopUpOrder.Status.CREDITED);
        assertLedger(1, 56, 2);
        verify(provider, times(1)).confirm(eq(order.id()), any());
        verify(provider, times(1)).lookup(order.id());
    }

    @Test void interruptionBetweenBindingAndDispatchCanBeRecoveredByOrderLookup() {
        var order = order("create");
        bindings.prepare(user, order.id(), payment(order.id()), 5000, "confirm");
        assertThat(executions.due(NOW)).contains(order.id());
        payments.recover(order.id());
        assertLedger(1, 56, 2);
        verify(provider, never()).confirm(any(), any());
        verify(provider).lookup(order.id());
    }

    @Test void expiredExecutionRejectsLateResultAndNewLeaseCompletesOnce() {
        var order = order("create");
        bindings.prepare(user, order.id(), payment(order.id()), 5000, "confirm");
        var old = executions.claim(order.id(), user, true, NOW).orElseThrow();
        Instant later = NOW.plusSeconds(181);
        when(clock.instant()).thenReturn(later);
        var current = executions.claim(order.id(), null, false, later).orElseThrow();
        assertThat(current.confirm()).isFalse();
        assertThat(executions.apply(old, approved(order.id()), later)).isFalse();
        assertThat(executions.apply(current, approved(order.id()), later)).isTrue();
        assertThat(executions.apply(old, approved(order.id()), later)).isFalse();
        assertLedger(1, 56, 2);
    }

    @Test void twoClientsDoNotHoldDbLocksWhileProviderRunsOrApproveTwice() throws Exception {
        var order = order("create");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(provider.confirm(any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return approved(order.id());
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> approve(order));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                var concurrent = approve(order);
                assertThat(concurrent.processing()).isTrue();
                assertThat(concurrent.walletBalance()).isNull();
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).order().status()).isEqualTo(TopUpOrder.Status.CREDITED);
        }
        assertLedger(1, 56, 2);
        verify(provider, times(1)).confirm(any(), any());
    }

    @Test void twoOrdersForNewWalletSerializeGrantsWithoutLostBalance() throws Exception {
        var a = order("a"); var b = order("b");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return approve(a); });
            var second = pool.submit(() -> { start.await(); return approve(b); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).order().status()).isEqualTo(TopUpOrder.Status.CREDITED);
            assertThat(second.get(10, TimeUnit.SECONDS).order().status()).isEqualTo(TopUpOrder.Status.CREDITED);
        }
        assertLedger(2, 112, 4);
    }

    @Test void grantFaultRollsBackAllWalletFactsAndRecoveryUsesLookup() {
        var order = order("create");
        var target = org.springframework.test.util.AopTestUtils.<JdbcWalletTopUps>getUltimateTargetObject(grants);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected grant fault"); })
                .when(target).save(any(), any(), anyList(), any(), any());
        assertThatThrownBy(() -> approve(order)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(count("wallet_transactions")).isZero();
        assertThat(count("wallet_lots")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(payments.get(user, order.id()).processing()).isTrue();
        doCallRealMethod().when(target).save(any(), any(), anyList(), any(), any());
        when(clock.instant()).thenReturn(NOW.plusSeconds(181));
        payments.recover(order.id());
        assertLedger(1, 56, 2);
        verify(provider, times(1)).confirm(any(), any());
        verify(provider, times(1)).lookup(any());
    }

    @Test void recoveryStopsAtLimitWithoutReportingDeclineOrGrant() {
        var order = order("create");
        when(provider.confirm(any(), any())).thenReturn(unknown(order.id()));
        when(provider.lookup(any())).thenReturn(unknown(order.id()));
        approve(order);
        for (int i = 1; i <= 4; i++) {
            when(clock.instant()).thenReturn(NOW.plusSeconds(31L * i));
            payments.recover(order.id());
        }
        var view = approve(order);
        assertThat(view.order().status()).isEqualTo(TopUpOrder.Status.PAYMENT_PENDING);
        assertThat(view.processing()).isFalse();
        assertThat(view.nextAction().name()).isEqualTo("CONTACT_SUPPORT");
        assertThat(view.failureCode()).isEqualTo("PAYMENT_REVIEW_REQUIRED");
        assertThat(view.walletBalance()).isNull();
        payments.recover(order.id());
        assertThat(count("wallet_transactions")).isZero();
        verify(provider, times(1)).confirm(any(), any());
        verify(provider, times(4)).lookup(any());
    }

    @ParameterizedTest @ValueSource(strings = {"order", "payment", "amount", "currency"})
    void mismatchedApprovalEvidenceNeverGrants(String field) {
        var order = order("create");
        var expected = approved(order.id());
        when(provider.confirm(any(), any())).thenReturn(new Result(Outcome.APPROVED,
                field.equals("order") ? UUID.randomUUID() : order.id(),
                field.equals("payment") ? "0".repeat(64) : expected.paymentKeyHash(),
                field.equals("amount") ? 4999 : 5000, field.equals("currency") ? "USD" : "KRW", null));
        assertThat(approve(order).processing()).isTrue();
        assertThat(count("wallet_transactions")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"DECLINED", "CANCELED"})
    void verifiedTerminalFailureDoesNotMintFundsOrRetry(String outcome) {
        var order = order("create");
        var expected = approved(order.id());
        when(provider.confirm(any(), any())).thenReturn(new Result(Outcome.valueOf(outcome), order.id(),
                expected.paymentKeyHash(), 5000, "KRW", null));
        var view = approve(order);
        assertThat(view.processing()).isFalse();
        assertThat(view.order().status().name()).isEqualTo(outcome.equals("DECLINED") ? "FAILED" : "CANCELED");
        approve(order);
        assertThat(count("wallet_transactions")).isZero();
        verify(provider, times(1)).confirm(any(), any());
    }

    private void catalog(int paid, int bonus) {
        when(catalog.availableTopUps()).thenReturn(List.of(new TopUpProduct(new Product(UUID.randomUUID(), "TEST_TOPUP",
                Product.Category.TOP_UP, new Price(Price.Currency.KRW, 5000), true, null, null, "test"), paid, bonus)));
    }
    private TopUpOrder order(String key) { return orders.create(user, "TEST_TOPUP", key); }
    private com.sajuppugi.payment.application.TopUpUseCase.OrderView approve(TopUpOrder order) {
        return payments.confirm(user, order.id(), new Confirmation(payment(order.id()), 5000), "confirm-" + order.id());
    }
    private Result approved(UUID order) {
        try { return new Result(Outcome.APPROVED, order, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payment(order).getBytes(StandardCharsets.UTF_8))), 5000, "KRW", null); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private Result unknown(UUID order) { return new Result(Outcome.UNKNOWN, order, null, 0, null, "unsafe-private-provider-text"); }
    private String payment(UUID order) { return "payment-" + order; }
    @ParameterizedTest
    @ValueSource(strings = {"5000.9", "5000.0", "\"5000\"", "true", "null", "2147483648", "0", "-1"})
    void nonIntegerOrNonPositiveAmountsCannotPrepareOrGrant(String amount) throws Exception {
        var order = order("invalid-amount");
        mvc.perform(confirmRequest(order.id(), "invalid-amount")
                .content(body(order.id(), 5000).replace("\"amount\":5000", "\"amount\":" + amount)))
                .andExpect(status().isBadRequest());
        verify(provider, never()).confirm(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_up_confirmation_intents WHERE order_id = ?",
                Integer.class, order.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ?",
                Integer.class, user)).isZero();
    }

    private String body(UUID order, int amount) {
        return "{\"paymentKey\":\"" + payment(order) + "\",\"orderId\":\"" + order + "\",\"amount\":" + amount + "}";
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirmRequest(UUID order, String key) {
        return post("/api/v1/top-up-orders/{id}/confirm", order).with(user(user.toString())).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(order, 5000));
    }
    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE wallet_user_id = ?", Integer.class, user);
    }
    private void assertLedger(int transactions, int balance, int lots) {
        assertThat(count("wallet_transactions")).isEqualTo(transactions);
        assertThat(count("wallet_lots")).isEqualTo(lots);
        assertThat(jdbc.queryForObject("SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?", Integer.class, user)).isEqualTo(balance);
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM wallet_transactions WHERE wallet_user_id = ?", Integer.class, user)).isEqualTo(balance);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM wallet_transaction_lines WHERE wallet_user_id = ?", Integer.class, user)).isEqualTo(balance);
    }
}
