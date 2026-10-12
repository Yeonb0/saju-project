package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.payment.application.CashRefundService;
import com.sajuppugi.payment.application.PaymentOperationsService;
import com.sajuppugi.payment.application.TopUpPaymentService;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import com.sajuppugi.payment.infrastructure.JdbcPaymentReconciliation;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.payment.port.PaymentProviderPort.*;
import com.sajuppugi.wallet.application.WalletOperationsService;
import com.sajuppugi.wallet.application.WalletTopUpPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {"app.toss.refunds-enabled=true", "app.toss.webhooks-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CashRefundOperationsTest {
    @Autowired CashRefundService service;
    @Autowired JdbcCashRefunds refunds;
    @Autowired JdbcPaymentReconciliation jobs;
    @Autowired PaymentOperationsService operations;
    @Autowired TopUpPaymentService payments;
    @Autowired WalletTopUpPort topUps;
    @Autowired com.sajuppugi.wallet.port.WalletRepository walletRepository;
    @MockitoSpyBean JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoBean PaymentProviderPort provider;
    @MockitoBean Clock clock;
    @MockitoSpyBean WalletOperationsService wallets;
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    UUID user;
    @BeforeEach void setup() {
        user = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        when(provider.cancelVerified(any(), any())).thenCallRealMethod();
        when(provider.lookup(any())).thenAnswer(c -> { assertNoTransaction(); return evidence(c.getArgument(0), Outcome.APPROVED); });
        when(provider.cancel(any(), any(), anyInt(), anyString())).thenAnswer(c -> { assertNoTransaction(); return evidence(c.getArgument(0), Outcome.CANCELED); });
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM wallet_cash_refund_returns WHERE refund_id IN (SELECT id FROM top_up_refunds WHERE user_id = ?)", user);
        jdbc.update("DELETE FROM payment_reconciliation_jobs WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", user);
        jdbc.update("DELETE FROM top_up_refund_requests WHERE user_id = ?", user);
        jdbc.update("DELETE FROM top_up_refunds WHERE user_id = ?", user);
        jdbc.update("DELETE FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", user);
        jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", user);
        jdbc.update("DELETE FROM wallet_integrity_findings WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallet_transaction_lines WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", user);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", user);
    }

    @Test void fullRefundReclaimsPaidBonusOnceAndReplaysAcrossRequestKeys() {
        UUID order = credit();
        var a = service.request(user, order, "refund");
        var b = service.request(user, order, "refund");
        var c = service.request(user, order, "alias");
        assertThat(a.state()).isEqualTo("REFUNDED");
        assertThat(b.refundId()).isEqualTo(a.refundId());
        assertThat(c.refundId()).isEqualTo(a.refundId());
        assertThat(balance()).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'REFUND'", user)).isEqualTo(1);
        assertThat(payments.get(user, order).order().status().name()).isEqualTo("REFUNDED");
        verify(provider, times(1)).cancel(eq(order), eq(a.refundId()), eq(1000), eq("UNUSED_TOP_UP"));
    }
    @Test void firstAttemptVerifiedRejectionRestoresOriginalLotsOnceAndKeepsCashPaid() {
        UUID order = credit();
        doReturn(new Result(Outcome.CANCEL_REJECTED, order, hash(order), 1000, "KRW", "CASH_REFUND_REJECTED"))
                .when(provider).cancel(eq(order), any(), anyInt(), anyString());
        var refund = service.request(user, order, "refund");
        assertThat(refund.state()).isEqualTo("REJECTED");
        assertThat(refund.completedAt()).isNull();
        assertThat(balance()).isEqualTo(56);
        assertThat(payments.get(user, order).order().status().name()).isEqualTo("CREDITED");
        service.request(user, order, "alias");
        service.recover(order);
        assertThat(count("SELECT COUNT(*) FROM wallet_cash_refund_returns WHERE refund_id = ?", refund.refundId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'ADJUSTMENT'", user)).isEqualTo(1);
        verify(provider, times(1)).cancel(any(), any(), anyInt(), anyString());
        assertThat(wallets.inspectAndExpire(user)).isTrue();
    }
    @Test void rejectionAfterUnknownEarlierAttemptCannotReleaseReservation() {
        UUID order = credit();
        doReturn(null).when(provider).cancel(any(), any(), anyInt(), anyString());
        service.request(user, order, "refund");
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        doReturn(new Result(Outcome.CANCEL_REJECTED, order, hash(order), 1000, "KRW", "CASH_REFUND_REJECTED"))
                .when(provider).cancel(eq(order), any(), anyInt(), anyString());
        service.recover(order);
        assertThat(service.get(user, order).state()).isEqualTo("RETRY");
        assertThat(balance()).isZero();
    }
    @Test void restoringAfterExpiryNeverExtendsTheOriginalGrantLifetime() {
        UUID order = credit();
        refunds.prepare(user, order, "refund", NOW);
        Instant later = NOW.plusSeconds(6L * 366 * 86400);
        var lease = refunds.claim(order, later).orElseThrow();
        when(clock.instant()).thenReturn(later);
        refunds.apply(lease, new Result(Outcome.CANCEL_REJECTED, order, hash(order), 1000, "KRW", "CASH_REFUND_REJECTED"), later);
        assertThat(service.get(user, order).state()).isEqualTo("REJECTED");
        assertThat(balance()).isZero();
        assertThat(wallets.inspectAndExpire(user)).isTrue();
        assertThat(count("SELECT SUM(remaining_amount) FROM wallet_lots WHERE wallet_user_id = ?", user)).isZero();
    }
    @Test void externalCancellationAfterRestorationRequiresReviewAndFreezesWallet() {
        UUID order = credit();
        doReturn(new Result(Outcome.CANCEL_REJECTED, order, hash(order), 1000, "KRW", "CASH_REFUND_REJECTED"))
                .when(provider).cancel(eq(order), any(), anyInt(), anyString());
        service.request(user, order, "refund");
        jobs.signal(order, NOW);
        when(provider.lookup(order)).thenReturn(evidence(order, Outcome.CANCELED));
        operations.reconcile(order);
        assertThat(service.get(user, order).state()).isEqualTo("REVIEW_REQUIRED");
        assertThatThrownBy(() -> wallets.assertUsable(user)).isInstanceOf(ApiException.class);
    }
    @Test void returnPersistenceFailureRollsBackRestorationAndRejectionTogether() {
        UUID order = credit();
        refunds.prepare(user, order, "refund", NOW);
        var lease = refunds.claim(order, NOW).orElseThrow();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Synthetic return failure"); })
                .when(wallets).returnRejectedCashRefund(eq(user), eq(lease.refund()));
        assertThatThrownBy(() -> refunds.apply(lease,
                new Result(Outcome.CANCEL_REJECTED, order, hash(order), 1000, "KRW", "CASH_REFUND_REJECTED"), NOW)).isInstanceOf(RuntimeException.class);
        assertThat(service.get(user, order).state()).isEqualTo("IN_FLIGHT");
        assertThat(balance()).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_cash_refund_returns WHERE refund_id = ?", lease.refund())).isZero();
    }
    @Test void timeoutDoesNotDeclareRefundAndLookupAfterRestartCompletesWithoutAnotherCancel() {
        UUID order = credit();
        when(provider.cancel(any(), any(), anyInt(), anyString())).thenReturn(null);
        assertThat(service.request(user, order, "refund").state()).isEqualTo("RETRY");
        assertThat(payments.get(user, order).order().status().name()).isEqualTo("REFUND_PENDING");
        assertThat(balance()).isZero();
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        when(provider.lookup(order)).thenReturn(evidence(order, Outcome.CANCELED));
        service.recover(order);
        assertThat(service.get(user, order).state()).isEqualTo("REFUNDED");
        verify(provider, times(1)).cancel(any(), any(), anyInt(), anyString());
    }
    @Test void exhaustedUnknownRefundKeepsReservationAndRequiresSupport() {
        UUID order = credit();
        when(provider.cancel(any(), any(), anyInt(), anyString())).thenReturn(null);
        service.request(user, order, "refund");
        when(provider.lookup(order)).thenReturn(null);
        for (int i = 1; i < 5; i++) { when(clock.instant()).thenReturn(NOW.plusSeconds(i * 31L)); service.recover(order); }
        assertThat(service.get(user, order).state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(service.get(user, order).completedAt()).isNull();
        assertThat(balance()).isZero();
        service.recover(order);
        assertThat(count("SELECT attempts FROM top_up_refunds WHERE order_id = ?", order)).isEqualTo(5);
    }
    @Test void wrongProviderIdentityCannotCancelOrFinalize() {
        UUID order = credit();
        when(provider.lookup(order)).thenReturn(new Result(Outcome.APPROVED, order, "wrong", 1000, "KRW", null));
        assertThat(service.request(user, order, "refund").state()).isEqualTo("RETRY");
        verify(provider, never()).cancel(any(), any(), anyInt(), anyString());
        var lease = refunds.claim(order, NOW.plusSeconds(31)).orElseThrow();
        refunds.apply(lease, evidence(UUID.randomUUID(), Outcome.CANCELED), NOW.plusSeconds(32));
        assertThat(service.get(user, order).state()).isEqualTo("RETRY");
    }
    @Test void lateResponseIsFencedAndNewWorkerCompletesOnce() {
        UUID order = credit();
        refunds.prepare(user, order, "refund", NOW);
        var stale = refunds.claim(order, NOW).orElseThrow();
        var fresh = refunds.claim(order, NOW.plusSeconds(181)).orElseThrow();
        refunds.apply(stale, evidence(order, Outcome.CANCELED), NOW.plusSeconds(182));
        assertThat(service.get(user, order).state()).isEqualTo("IN_FLIGHT");
        refunds.apply(fresh, evidence(order, Outcome.CANCELED), NOW.plusSeconds(182));
        assertThat(service.get(user, order).state()).isEqualTo("REFUNDED");
        assertThat(balance()).isZero();
    }
    @Test void policyDeadlineOwnershipAndKeyReuseAreEnforced() {
        UUID first = credit(), second = credit();
        assertThatThrownBy(() -> service.request(UUID.randomUUID(), first, "a")).isInstanceOf(ApiException.class);
        service.request(user, first, "same");
        service.request(user, first, "alias");
        assertThatThrownBy(() -> service.request(user, second, "same")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.request(user, second, "alias")).isInstanceOf(ApiException.class);
        when(clock.instant()).thenReturn(NOW.plusSeconds(8L * 86400));
        assertThatThrownBy(() -> service.request(user, second, "different")).isInstanceOf(ApiException.class);
        assertThat(balance()).isEqualTo(56);
    }
    @Test void reservationRollbackLeavesNoPartialLedger() {
        UUID order = credit();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Synthetic persistence failure"); })
                .when(wallets).reclaimUnusedTopUp(eq(user), eq(order), any(), anyInt(), anyInt());
        assertThatThrownBy(() -> service.request(user, order, "refund")).isInstanceOf(RuntimeException.class).hasMessageContaining("Synthetic persistence failure");
        assertThat(balance()).isEqualTo(56);
        assertThat(count("SELECT COUNT(*) FROM top_up_refunds WHERE order_id = ?", order)).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'REFUND'", user)).isZero();
        verify(provider, never()).cancel(any(), any(), anyInt(), anyString());
    }
    @Test void finalizationFailureRollsBackRefundAndOrderTogetherAndCanRecoverAfterLeaseExpiry() {
        UUID order = credit();
        refunds.prepare(user, order, "refund", NOW);
        var lease = refunds.claim(order, NOW).orElseThrow();
        String statement = "UPDATE top_up_orders SET status = 'REFUNDED' WHERE id = ?";
        doThrow(new IllegalStateException("Synthetic finalization failure")).when(jdbc).update(eq(statement), eq(order));
        assertThatThrownBy(() -> refunds.apply(lease, evidence(order, Outcome.CANCELED), NOW)).isInstanceOf(RuntimeException.class);
        assertThat(service.get(user, order).state()).isEqualTo("IN_FLIGHT");
        assertThat(service.get(user, order).completedAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM top_up_orders WHERE id = ?", String.class, order)).isEqualTo("CREDITED");
        doCallRealMethod().when(jdbc).update(eq(statement), eq(order));
        var retry = refunds.claim(order, NOW.plusSeconds(181)).orElseThrow();
        refunds.apply(retry, evidence(order, Outcome.CANCELED), NOW.plusSeconds(182));
        assertThat(service.get(user, order).state()).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'REFUND'", user)).isEqualTo(1);
    }
    @Test void simultaneousDifferentOrdersCannotReuseTheSameRefundKey() throws Exception {
        UUID a = credit(), b = credit();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var one = pool.submit(() -> { start.await(); try { service.request(user, a, "shared"); return true; } catch (ApiException failure) { return false; } });
            var two = pool.submit(() -> { start.await(); try { service.request(user, b, "shared"); return true; } catch (ApiException failure) { return false; } });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS) ^ two.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(balance()).isEqualTo(56);
        assertThat(count("SELECT COUNT(*) FROM top_up_refunds WHERE user_id = ?", user)).isEqualTo(1);
    }
    @Test void spentCreditAndCompensatedUsageCannotAutomaticallyCashRefund() {
        UUID order = credit();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            UUID lot = jdbc.queryForObject("SELECT id FROM wallet_lots WHERE wallet_user_id = ? AND balance_type = 'PAID'", UUID.class, user);
            UUID debit = UUID.randomUUID(), reversal = UUID.randomUUID();
            jdbc.update("INSERT INTO wallet_transactions(id,wallet_user_id,type,total_amount,reference_id,created_at) VALUES (?,?,'PURCHASE',-1,?,?)", debit, user, UUID.randomUUID(), Timestamp.from(NOW));
            jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id,lot_id,wallet_user_id,amount) VALUES (?,?,?,-1)", debit, lot, user);
            jdbc.update("INSERT INTO wallet_transactions(id,wallet_user_id,type,total_amount,reference_id,reversal_of_id,created_at) VALUES (?,?,'REFUND',1,?,?,?)", reversal, user, UUID.randomUUID(), debit, Timestamp.from(NOW));
            jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id,lot_id,wallet_user_id,amount) VALUES (?,?,?,1)", reversal, lot, user);
        });
        assertThatThrownBy(() -> service.request(user, order, "refund")).isInstanceOf(ApiException.class);
        assertThat(balance()).isEqualTo(56);
        verifyNoInteractions(provider);
    }
    @Test void concurrentExpiryWorkersWriteOneExpiryAndNoExtraRefundReservation() throws Exception {
        UUID order = credit();
        when(clock.instant()).thenReturn(NOW.plusSeconds(6L * 366 * 86400));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = pool.submit(() -> wallets.inspectAndExpire(user));
            var two = pool.submit(() -> wallets.inspectAndExpire(user));
            assertThat(one.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(two.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(balance()).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'EXPIRY'", user)).isEqualTo(1);
        assertThatThrownBy(() -> service.request(user, order, "refund")).isInstanceOf(ApiException.class);
    }
    @Test void concurrentRequestsReserveAndCancelOnceWithoutHoldingWalletDuringProviderWait() throws Exception {
        UUID order = credit();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(provider.cancel(eq(order), any(), anyInt(), anyString())).thenAnswer(c -> {
            assertNoTransaction(); entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return evidence(order, Outcome.CANCELED);
        });
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> service.request(user, order, "first"));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> service.request(user, order, "second"));
                assertThat(second.get(5, TimeUnit.SECONDS).state()).isEqualTo("IN_FLIGHT");
                assertThat(balance()).isZero();
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).state()).isEqualTo("REFUNDED");
        }
        verify(provider, times(1)).cancel(any(), any(), anyInt(), anyString());
    }
    @Test void webhookDuplicatesOnlyQueueAndForgedCanceledBodyDoesNotReclaim() throws Exception {
        UUID order = credit();
        for (int i = 0; i < 3; i++) mvc.perform(post("/api/v1/webhooks/toss").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"" + order + "\",\"status\":\"CANCELED\"}}"))
                .andExpect(status().isOk());
        verifyNoInteractions(provider);
        assertThat(count("SELECT COUNT(*) FROM payment_reconciliation_jobs WHERE order_id = ?", order)).isEqualTo(1);
        operations.reconcile(order);
        assertThat(balance()).isEqualTo(56);
        assertThat(jobState(order)).isEqualTo("CHECKED");
    }
    @Test void verifiedExternalCancelReclaimsOnceAndSpentCreditRequiresReview() {
        UUID order = credit();
        jobs.signal(order, NOW); when(provider.lookup(order)).thenReturn(evidence(order, Outcome.CANCELED));
        operations.reconcile(order); operations.reconcile(order);
        assertThat(service.get(user, order).state()).isEqualTo("REFUNDED");
        assertThat(balance()).isZero();
        UUID spent = credit();
        // A coherent synthetic debit fixture, not a bare balance mutation.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("UPDATE wallet_lots SET remaining_amount = remaining_amount - 1 WHERE wallet_user_id = ? AND balance_type = 'PAID' AND remaining_amount > 0", user);
            UUID debit = UUID.randomUUID();
            jdbc.update("INSERT INTO wallet_transactions(id,wallet_user_id,type,total_amount,reference_id,created_at) VALUES (?,?,'PURCHASE',-1,?,?)", debit, user, UUID.randomUUID(), Timestamp.from(NOW));
            jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id,lot_id,wallet_user_id,amount) SELECT ?,id,?,-1 FROM wallet_lots WHERE wallet_user_id = ? AND balance_type = 'PAID' AND remaining_amount > 0", debit, user, user);
            jdbc.update("UPDATE wallets SET paid_balance = paid_balance - 1 WHERE user_id = ?", user);
        });
        jobs.signal(spent, NOW); when(provider.lookup(spent)).thenReturn(evidence(spent, Outcome.CANCELED));
        operations.reconcile(spent);
        assertThat(jobState(spent)).isEqualTo("REVIEW_REQUIRED");
        assertThat(service.get(user, spent).state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(balance()).isEqualTo(55);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(tx -> walletRepository.lock(user))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> wallets.assertUsable(user)).isInstanceOf(ApiException.class);
    }
    @Test void failedLookupIsBoundedAndNewSignalsCannotResetRetries() {
        UUID order = credit(); when(provider.lookup(order)).thenReturn(null); jobs.signal(order, NOW);
        for (int i = 0; i < 5; i++) {
            when(clock.instant()).thenReturn(NOW.plusSeconds(31L * i));
            operations.reconcile(order); jobs.signal(order, clock.instant());
        }
        assertThat(jobState(order)).isEqualTo("REVIEW_REQUIRED");
        assertThat(count("SELECT attempts FROM payment_reconciliation_jobs WHERE order_id = ?", order)).isEqualTo(5);
        assertThat(balance()).isEqualTo(56);
    }
    @Test void verifiedPartialCancellationRequiresReviewWithoutInventingRefundOrReclaimAmount() {
        UUID order = credit();
        when(provider.lookup(order)).thenReturn(new Result(Outcome.UNKNOWN, order, hash(order), 1000, "KRW", "PAYMENT_PARTIAL_CANCEL_REVIEW"));
        jobs.signal(order, NOW); operations.reconcile(order);
        assertThat(service.get(user, order).state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(service.get(user, order).completedAt()).isNull();
        assertThat(balance()).isEqualTo(56);
        assertThatThrownBy(() -> wallets.assertUsable(user)).isInstanceOf(ApiException.class);
        verify(provider, never()).cancel(any(), any(), anyInt(), anyString());
    }
    @Test void httpRefundRequiresLoginCsrfAndOwnerAndNeverEchoesKeys() throws Exception {
        UUID order = credit(); String path = "/api/v1/top-up-orders/" + order + "/refund";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user(user.toString())).header("Idempotency-Key", "key")).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(UUID.randomUUID().toString())).with(csrf()).header("Idempotency-Key", "key")).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user(user.toString())).with(csrf()).header("Idempotency-Key", "key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("REFUNDED"));
        mvc.perform(get(path).with(user(user.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(1000));
    }
    @Test void unsupportedMalformedUnknownOrderAndOversizedWebhooksAreSafe() throws Exception {
        mvc.perform(post("/api/v1/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"unknown\"}")) .andExpect(status().isOk());
        mvc.perform(post("/api/v1/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isOk());
        mvc.perform(post("/api/v1/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content("{")) .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content("x".repeat(16385))) .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"" + UUID.randomUUID() + "\"}}")) .andExpect(status().isOk());
        verifyNoInteractions(provider);
    }
    private UUID credit() {
        UUID order = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at) VALUES (?,?,?,'TEST',1000,50,6,'CREDITED',?)", order, user, order.toString(), Timestamp.from(NOW));
            jdbc.update("INSERT INTO top_up_confirmation_intents(order_id,payment_key_hash,amount_krw,created_at) VALUES (?,?,1000,?)", order, hash(order), Timestamp.from(NOW));
            topUps.credit(user, order, 50, 6);
        });
        return order;
    }
    private Result evidence(UUID order, Outcome state) { return order == null ? null : new Result(state, order, hash(order), 1000, "KRW", null); }
    private String hash(UUID id) { return id.toString().replace("-", "").repeat(2); }
    private int balance() { return count("SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?", user); }
    private int count(String sql, UUID id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private String jobState(UUID id) { return jdbc.queryForObject("SELECT state FROM payment_reconciliation_jobs WHERE order_id = ?", String.class, id); }
    private void assertNoTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
}
