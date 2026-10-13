package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.admin.application.AdminCashRefundService;
import com.sajuppugi.admin.infrastructure.JdbcAdminAudit;
import com.sajuppugi.admin.infrastructure.JdbcAdminRefundCases;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.payment.application.CashRefundService;
import com.sajuppugi.payment.application.PaymentOperationsService;
import com.sajuppugi.payment.application.TopUpPaymentService;
import com.sajuppugi.payment.infrastructure.JdbcPaymentReconciliation;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.payment.port.PaymentProviderPort.*;
import com.sajuppugi.wallet.application.RemainingTopUpRefund;
import com.sajuppugi.wallet.application.WalletTopUpPort;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletRepository;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.admin.enabled=true", "app.admin.recovery-enabled=true", "app.admin.recovery-poll-delay=PT1H", "app.toss.refunds-enabled=true", "app.wallet.purchases-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class AdminRemainingRefundTest {
    @Autowired JdbcAdminRefundCases cases;
    @Autowired AdminCashRefundService service;
    @Autowired CashRefundService refunds;
    @Autowired TopUpPaymentService payments;
    @Autowired PaymentOperationsService operations;
    @Autowired JdbcPaymentReconciliation jobs;
    @Autowired com.sajuppugi.payment.infrastructure.JdbcCashRefunds refundStore;
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletTopUpPort wallet;
    @Autowired WalletRepository walletRepository;
    @Autowired com.sajuppugi.wallet.application.WalletPurchasePort purchases;
    @Autowired com.sajuppugi.wallet.application.WalletPurchaseRecoveryPort compensation;
    @Autowired com.sajuppugi.catalog.application.CatalogUseCase catalog;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean Clock clock;
    @MockitoBean PaymentProviderPort provider;
    @MockitoSpyBean JdbcAdminAudit audit;
    static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    UUID actor, viewer, customer;
    final ConcurrentMap<UUID, Integer> canceled = new ConcurrentHashMap<>();

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        actor = member(); viewer = member(); customer = member();
        grant(actor, true); grant(viewer, false); canceled.clear();
        when(provider.cancelRemainingVerified(any(), any(), anyInt())).thenCallRealMethod();
        when(provider.lookup(any())).thenAnswer(call -> {
            assertOutsideTransaction(); UUID order = call.getArgument(0);
            return evidence(order, canceled.getOrDefault(order, 0));
        });
        when(provider.cancel(any(), any(), anyInt(), anyString())).thenAnswer(call -> {
            assertOutsideTransaction(); UUID order = call.getArgument(0); int amount = call.getArgument(2);
            assertThat((String) call.getArgument(3)).isEqualTo("REMAINING_TOP_UP");
            canceled.put(order, amount); return evidence(order, amount);
        });
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM wallet_purchase_commands WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_purchase_compensations WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_purchase_debits WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM purchase_quotes WHERE requester_user_id = ?", customer);
        jdbc.update("DELETE FROM products WHERE code LIKE ?", "REFUND_TEST_" + customer + "%");
        jdbc.update("DELETE FROM admin_refund_execution_keys WHERE case_id IN (SELECT id FROM admin_refund_cases WHERE customer_user_id = ?)", customer);
        jdbc.update("DELETE FROM wallet_cash_refund_returns WHERE refund_id IN (SELECT id FROM top_up_refunds WHERE user_id = ?)", customer);
        jdbc.update("DELETE FROM payment_reconciliation_jobs WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", customer);
        jdbc.update("DELETE FROM top_up_refund_requests WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM top_up_refunds WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM admin_refund_cases WHERE customer_user_id = ?", customer);
        jdbc.update("DELETE FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", customer);
        jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_integrity_findings WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_transaction_lines WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM admin_audit_events WHERE operator_id IN (?,?,?)", actor, viewer, customer);
        jdbc.update("DELETE FROM admin_operator_grants WHERE user_id IN (?,?,?)", actor, viewer, customer);
        jdbc.update("DELETE FROM user_sessions WHERE user_id IN (?,?,?)", actor, viewer, customer);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", actor, viewer, customer);
    }

    @Test void unusedRefundReclaimsPaidAndBonusAndCompletesOnce() {
        UUID order = credit(); var c = intake(order); var q = preview(c.caseId());
        assertThat(q).isEqualTo(new RemainingTopUpRefund(5000, 500, 50));
        var first = service.execute(actor, c.caseId(), "execute", q);
        var second = service.execute(actor, c.caseId(), "execute", q);
        var alias = service.execute(actor, c.caseId(), "alias", q);
        assertThat(first.state()).isEqualTo("REFUNDED");
        assertThat(first.refundId()).isEqualTo(second.refundId()).isEqualTo(alias.refundId());
        assertThat(balance()).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'REFUND'", customer)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ? AND action = 'ADMIN_REFUND_COMPLETED'", actor)).isEqualTo(1);
        verify(provider, times(1)).cancel(eq(order), any(), eq(5000), eq("REMAINING_TOP_UP"));
    }
    @Test void bonusOnlyUseDoesNotDeductCashAndRemainingBonusIsReclaimed() {
        UUID order = credit(); use(order, 0, 20); var c = intake(order);
        var q = preview(c.caseId());
        assertThat(q).isEqualTo(new RemainingTopUpRefund(5000, 500, 30));
        assertThat(service.execute(actor, c.caseId(), "execute", q).amount()).isEqualTo(5000);
        assertThat(balance()).isZero();
    }
    @Test void partiallyUsedOrderOlderThanSevenDaysRefundsOnlyItsRemainingPaidValue() throws Exception {
        UUID order = credit(); use(order, 100, 50); UUID unrelated = credit();
        var c = intake(order); var q = preview(c.caseId());
        assertThat(q).isEqualTo(new RemainingTopUpRefund(4000, 400, 0));
        assertThat(service.execute(actor, c.caseId(), "execute", q).state()).isEqualTo("REFUNDED");
        assertThat(payments.get(customer, order).order().status().name()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(payments.get(customer, unrelated).order().status().name()).isEqualTo("CREDITED");
        String history = mvc.perform(get("/api/v1/top-up-orders").with(user(customer.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var items = mapper.readTree(history).path("data").path("items");
        assertThat(java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(item -> order.toString().equals(item.path("orderId").asText())).findFirst().orElseThrow()
                .path("status").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(balance()).isEqualTo(550);
        jobs.signal(order, NOW); operations.reconcile(order);
        assertThat(count("SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ?", customer)).isZero();
    }
    @Test void supportTimestampAndOwnerAreValidatedAndIntakeDoesNotCallProvider() {
        UUID order = credit(); var c = intake(order);
        assertThat(c.receivedAt()).isEqualTo(NOW.minusSeconds(86400));
        assertThat(c.registeredAt()).isEqualTo(NOW);
        assertThat(cases.open(actor, order, customer, "ticket_" + order, c.receivedAt(), "open_" + order).caseId()).isEqualTo(c.caseId());
        assertThatThrownBy(() -> cases.open(actor, order, actor, "ticket", NOW, "wrong-owner")).isInstanceOf(ApiException.class);
        UUID second = credit();
        assertThatThrownBy(() -> cases.open(actor, second, customer, "ticket", NOW.plusSeconds(1), "future")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> cases.open(actor, second, customer, "ticket", NOW, "open_" + order)).isInstanceOf(ApiException.class);
        verifyNoInteractions(provider);
    }
    @Test void changedPreviewCannotExecuteAndReplayedBodyCannotChangeMeaning() {
        UUID order = credit(); var c = intake(order); var stale = preview(c.caseId()); use(order, 1, 0);
        assertThatThrownBy(() -> service.execute(actor, c.caseId(), "execute", stale)).isInstanceOf(ApiException.class);
        assertThat(count("SELECT COUNT(*) FROM top_up_refunds WHERE order_id = ?", order)).isZero();
        verifyNoInteractions(provider);
        var q = preview(c.caseId()); service.execute(actor, c.caseId(), "execute", q);
        assertThatThrownBy(() -> service.execute(actor, c.caseId(), "execute", stale)).isInstanceOf(ApiException.class);
    }
    @Test void exhaustedOrExpiredPaidLotsAreNotRefundable() {
        UUID order = credit(); use(order, 500, 0); var c = intake(order);
        assertThatThrownBy(() -> preview(c.caseId())).isInstanceOf(ApiException.class);
        UUID expired = credit(); var e = intake(expired);
        when(clock.instant()).thenReturn(NOW.plusSeconds(6L * 366 * 86400));
        assertThatThrownBy(() -> preview(e.caseId())).isInstanceOf(ApiException.class);
        verifyNoInteractions(provider);
    }
    @Test void timeoutAfterPgCancellationRecoversByLookupWithoutAnotherCancel() {
        UUID order = credit(); use(order, 100, 0); var c = intake(order); var q = preview(c.caseId());
        doAnswer(call -> { assertOutsideTransaction(); canceled.put(order, 4000); throw new IllegalStateException("Synthetic timeout"); })
                .when(provider).cancel(eq(order), any(), anyInt(), anyString());
        assertThat(service.execute(actor, c.caseId(), "execute", q).state()).isEqualTo("RETRY");
        assertThat(payments.get(customer, order).order().status().name()).isEqualTo("REFUND_PENDING");
        when(clock.instant()).thenReturn(NOW.plusSeconds(31)); refunds.recover(order);
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("REFUNDED");
        verify(provider, times(1)).cancel(eq(order), any(), anyInt(), anyString());
    }
    @Test void mismatchedCancellationCannotFinalizeAndUnknownEventuallyRequiresReview() {
        UUID order = credit(); use(order, 100, 0); var c = intake(order); var q = preview(c.caseId());
        doReturn(evidence(order, 3000)).when(provider).cancel(eq(order), any(), anyInt(), anyString());
        assertThat(service.execute(actor, c.caseId(), "execute", q).state()).isEqualTo("RETRY");
        doReturn(evidence(order, 3000)).when(provider).lookup(order);
        for (int i = 1; i < 5; i++) { when(clock.instant()).thenReturn(NOW.plusSeconds(31L * i)); refunds.recover(order); }
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(balance()).isZero();
        assertThat(cases.view(actor, c.caseId()).completedAt()).isNull();
    }
    @Test void explicitInitialRejectionRestoresOnlyReservedLotsWithOriginalExpiry() {
        UUID order = credit(); use(order, 100, 20); var c = intake(order); var q = preview(c.caseId());
        doReturn(new Result(Outcome.CANCEL_REJECTED, order, hash(order), 5000, "KRW", "CASH_REFUND_REJECTED"))
                .when(provider).cancel(eq(order), any(), anyInt(), anyString());
        assertThat(service.execute(actor, c.caseId(), "execute", q).state()).isEqualTo("REJECTED");
        assertThat(balance()).isEqualTo(430);
        assertThat(count("SELECT COUNT(*) FROM wallet_cash_refund_returns WHERE refund_id IN (SELECT id FROM top_up_refunds WHERE order_id = ?)", order)).isEqualTo(1);
    }
    @Test void revokedOperatorAndViewerCannotReserveOrCallPg() {
        UUID order = credit(); var c = intake(order); var q = preview(c.caseId());
        assertThatThrownBy(() -> service.execute(viewer, c.caseId(), "viewer", q)).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", actor);
        assertThatThrownBy(() -> service.execute(actor, c.caseId(), "execute", q)).isInstanceOf(ApiException.class);
        assertThat(balance()).isEqualTo(550); verifyNoInteractions(provider);
    }
    @Test void auditFailureRollsBackReservationAndIntentBeforePg() {
        UUID order = credit(); var c = intake(order); var q = preview(c.caseId());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> doThrow(new IllegalStateException("Synthetic audit failure"))
                .when(audit).append(argThat(e -> "ADMIN_REFUND_INTENT".equals(e.action()))));
        assertThatThrownBy(() -> service.execute(actor, c.caseId(), "execute", q)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(count("SELECT COUNT(*) FROM top_up_refunds WHERE order_id = ?", order)).isZero();
        assertThat(count("SELECT COUNT(*) FROM admin_refund_execution_keys WHERE case_id = ?", c.caseId())).isZero();
        assertThat(balance()).isEqualTo(550); verifyNoInteractions(provider);
    }
    @Test void completionAuditFailureLeavesDurableLeaseAndLookupFinishesOnce() {
        UUID order = credit(); use(order, 100, 20); var c = intake(order); var q = preview(c.caseId());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> doThrow(new IllegalStateException("Synthetic completion failure"))
                .when(audit).append(argThat(e -> "ADMIN_REFUND_COMPLETED".equals(e.action()))));
        assertThatThrownBy(() -> service.execute(actor, c.caseId(), "execute", q)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("IN_FLIGHT");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> doCallRealMethod().when(audit).append(any()));
        when(clock.instant()).thenReturn(NOW.plusSeconds(181)); refunds.recover(order);
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("REFUNDED");
        verify(provider, times(1)).cancel(eq(order), any(), anyInt(), anyString());
    }
    @Test void simultaneousExecutionKeysReserveAndCancelOnlyOnceWithoutLocksDuringPg() throws Exception {
        UUID order = credit(); var c = intake(order); var q = preview(c.caseId());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { assertOutsideTransaction(); entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            canceled.put(order, 5000); return evidence(order, 5000); }).when(provider).cancel(eq(order), any(), anyInt(), anyString());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> service.execute(actor, c.caseId(), "one", q));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                var second = pool.submit(() -> service.execute(actor, c.caseId(), "two", q));
                assertThat(second.get(5, TimeUnit.SECONDS).state()).isEqualTo("IN_FLIGHT");
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).state()).isEqualTo("REFUNDED");
        }
        verify(provider, times(1)).cancel(eq(order), any(), anyInt(), anyString());
    }
    @Test void actualCookieCsrfAndRecoveryCapabilityProtectHttpIntakeAndExecution() throws Exception {
        UUID order = credit(); String body = mapper.writeValueAsString(new com.sajuppugi.admin.api.AdminCashRefundController.Intake(order, customer, "ticket", NOW));
        mvc.perform(post("/api/v1/admin/refund-cases").with(user(viewer.toString())).with(csrf()).header("Idempotency-Key", "open")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String raw = "synthetic-session-" + UUID.randomUUID();
        String tokenHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO user_sessions(id,user_id,token_hash,csrf_secret_hash,expires_at,last_seen_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), actor, tokenHash, "b".repeat(64), Timestamp.from(NOW.plusSeconds(3600)), Timestamp.from(NOW));
        var cookie = new Cookie("SAJUPPUGI_SESSION", raw);
        mvc.perform(post("/api/v1/admin/refund-cases").cookie(cookie).header("Idempotency-Key", "open")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String session = mvc.perform(get("/api/v1/session").cookie(cookie)).andReturn().getResponse().getContentAsString();
        String csrf = mapper.readTree(session).path("data").path("csrfToken").asText();
        String opened = mvc.perform(post("/api/v1/admin/refund-cases").cookie(cookie).header("X-CSRF-Token", csrf).header("Idempotency-Key", "open")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(opened).path("data").path("caseId").asText();
        mvc.perform(post("/api/v1/admin/refund-cases/" + id + "/execute").cookie(cookie).header("X-CSRF-Token", csrf).header("Idempotency-Key", "execute")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedAmount\":5000,\"expectedPaidShells\":500,\"expectedBonusShells\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("REFUNDED"));
    }

    @Test void stalePartialRefundLeaseCannotCompleteAfterAnotherWorkerClaimsIt() {
        UUID order = credit(); use(order, 100, 20); var c = intake(order); var q = preview(c.caseId());
        cases.reserve(actor, c.caseId(), "execute", q);
        var stale = refundStore.claim(order, NOW).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(181));
        var fresh = refundStore.claim(order, clock.instant()).orElseThrow();
        refundStore.apply(stale, evidence(order, 4000), clock.instant());
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("IN_FLIGHT");
        refundStore.apply(fresh, evidence(order, 4000), clock.instant());
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ? AND action = 'ADMIN_REFUND_COMPLETED'", actor)).isEqualTo(1);
    }
    @Test void aDifferentExternalPartialAmountRequiresReviewAndRestrictsWallet() {
        UUID order = credit(); use(order, 100, 20); var c = intake(order); var q = preview(c.caseId());
        cases.reserve(actor, c.caseId(), "execute", q);
        doReturn(evidence(order, 3000)).when(provider).lookup(order);
        jobs.signal(order, NOW); operations.reconcile(order);
        assertThat(cases.view(actor, c.caseId()).state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(cases.view(actor, c.caseId()).completedAt()).isNull();
        assertThat(count("SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND kind = 'EXTERNAL_CANCEL_REVIEW'", customer)).isEqualTo(1);
        verify(provider, never()).cancel(any(), any(), anyInt(), anyString());
    }
    @Test void expiredBonusNeverReducesPaidRefundAndPreservesOriginalExpiryOnRejection() {
        UUID order = credit(); var c = intake(order);
        when(clock.instant()).thenReturn(NOW.atZone(java.time.ZoneId.of("Asia/Seoul")).plusYears(2).toInstant());
        var q = preview(c.caseId()); assertThat(q.amount()).isEqualTo(5000);
        doReturn(new Result(Outcome.CANCEL_REJECTED, order, hash(order), 5000, "KRW", "CASH_REFUND_REJECTED"))
                .when(provider).cancel(eq(order), any(), anyInt(), anyString());
        service.execute(actor, c.caseId(), "execute", q);
        assertThat(balance()).isEqualTo(500);
        assertThat(count("SELECT COUNT(*) FROM wallet_lots WHERE wallet_user_id = ? AND balance_type = 'BONUS' AND remaining_amount = 50", customer)).isEqualTo(1);
    }
    @Test void alreadyAuthorizedRefundRecoveryContinuesAfterOperatorRevocation() {
        UUID order = credit(); var c = intake(order); var q = preview(c.caseId());
        cases.reserve(actor, c.caseId(), "execute", q);
        jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", actor);
        refunds.recover(order);
        assertThat(cases.view(viewer, c.caseId()).state()).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ? AND action = 'ADMIN_REFUND_COMPLETED'", actor)).isEqualTo(1);
    }
    @Test void unfinishedPurchaseBlocksCashRefundUntilOriginalDebitIsCompensated() {
        UUID order = credit(), quote = purchaseQuote(); var c = intake(order);
        var debit = purchases.debit(customer, quote, new com.sajuppugi.common.idempotency.IdempotencyKey("purchase"));
        assertThatThrownBy(() -> preview(c.caseId())).isInstanceOf(ApiException.class);
        verifyNoInteractions(provider);
        compensation.compensatePurchase(customer, quote, debit.transactionId(), "READING_GENERATION_FAILED",
                new com.sajuppugi.common.idempotency.IdempotencyKey("return"));
        var q = preview(c.caseId()); assertThat(q).isEqualTo(new RemainingTopUpRefund(5000, 500, 50));
        assertThat(service.execute(actor, c.caseId(), "execute", q).state()).isEqualTo("REFUNDED");
        assertThat(balance()).isZero();
    }
    @RepeatedTest(3) void concurrentPurchaseAndRefundCannotBothSpendTheSameCredits() throws Exception {
        UUID order = credit(), quote = purchaseQuote(); var c = intake(order); var q = preview(c.caseId());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var refund = pool.submit(() -> { start.await(); try { service.execute(actor, c.caseId(), "execute", q); return true; }
                catch (ApiException declined) { return false; } });
            var purchase = pool.submit(() -> { start.await(); try { purchases.debit(customer, quote, new com.sajuppugi.common.idempotency.IdempotencyKey("purchase")); return true; }
                catch (ApiException declined) { return false; } });
            start.countDown();
            boolean refunded = refund.get(15, TimeUnit.SECONDS), purchased = purchase.get(15, TimeUnit.SECONDS);
            assertThat(refunded ^ purchased).isTrue();
            assertThat(balance()).isEqualTo(refunded ? 0 : 530);
        }
    }
    private UUID purchaseQuote() {
        String code = "REFUND_TEST_" + customer + "_" + UUID.randomUUID();
        jdbc.update("INSERT INTO products(id,code,category,price_currency,price_amount,active,catalog_version) VALUES (?,?,'READING','TURTLE_SHELL',20,TRUE,'synthetic')", UUID.randomUUID(), code);
        return catalog.issueQuote(new com.sajuppugi.catalog.application.CatalogUseCase.IssueQuote(customer, code, "a".repeat(64))).id();
    }

    private JdbcAdminRefundCases.CaseView intake(UUID order) {
        return cases.open(actor, order, customer, "ticket_" + order, NOW.minusSeconds(86400), "open_" + order);
    }
    private RemainingTopUpRefund preview(UUID id) {
        var q = cases.quote(actor, id); assertThat(q.feeAmount()).isZero();
        return new RemainingTopUpRefund(q.amount(), q.paidShells(), q.bonusShells());
    }
    private UUID credit() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at) VALUES (?,?,?,'TOPUP_5000',5000,500,50,'CREDITED',?)",
                    id, customer, hash(id), Timestamp.from(NOW.minusSeconds(30L * 86400)));
            jdbc.update("INSERT INTO top_up_confirmation_intents(order_id,payment_key_hash,amount_krw,created_at) VALUES (?,?,5000,?)", id, hash(id), Timestamp.from(NOW));
            wallet.credit(customer, id, 500, 50);
        }); return id;
    }
    private void use(UUID order, int paid, int bonus) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var before = walletRepository.lock(customer);
            var ids = jdbc.query("SELECT l.lot_id FROM wallet_transaction_lines l JOIN wallet_top_up_receipts r ON r.transaction_id = l.transaction_id WHERE r.order_id = ?",
                    (rs, row) -> rs.getObject(1, UUID.class), order);
            var allocations = before.lots().stream().filter(l -> ids.contains(l.id()))
                    .filter(l -> (l.balanceType() == WalletLot.BalanceType.PAID ? paid : bonus) != 0).map(l ->
                    new WalletTransaction.Allocation(l.id(), -(l.balanceType() == WalletLot.BalanceType.PAID ? paid : bonus))).toList();
            var after = before.lots().stream().map(l -> new WalletLot(l.id(), l.balanceType(), l.grantedAmount(), l.remainingAmount()
                    + allocations.stream().filter(a -> a.lotId().equals(l.id())).mapToInt(WalletTransaction.Allocation::amount).sum(), l.expiresAt(), l.createdAt())).toList();
            walletRepository.record(before, after, new WalletBalance(before.balance().paidBalance() - paid, before.balance().bonusBalance() - bonus),
                    new WalletTransaction(UUID.randomUUID(), customer, WalletTransaction.Type.PURCHASE, -paid-bonus, UUID.randomUUID(), null, allocations, NOW));
        });
    }
    private UUID member() {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO users(id,kakao_subject,nickname,status,created_at,updated_at) VALUES (?,?,'Synthetic','ACTIVE',?,?)",
                id, "synthetic-" + id, Timestamp.from(NOW), Timestamp.from(NOW)); return id;
    }
    private void grant(UUID id, boolean recovery) { jdbc.update("INSERT INTO admin_operator_grants(user_id,active,can_recover,created_at,updated_at) VALUES (?,TRUE,?,?,?)", id, recovery, Timestamp.from(NOW), Timestamp.from(NOW)); }
    private Result evidence(UUID id, int amount) {
        return amount == 0 ? new Result(Outcome.APPROVED, id, hash(id), 5000, "KRW", null)
                : amount == 5000 ? new Result(Outcome.CANCELED, id, hash(id), 5000, "KRW", null)
                : new Result(Outcome.UNKNOWN, id, hash(id), 5000, "KRW", "PAYMENT_PARTIAL_CANCEL_REVIEW", amount);
    }
    private String hash(UUID id) { return id.toString().replace("-", "").repeat(2); }
    private int count(String sql, UUID id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private int balance() { return count("SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?", customer); }
    private void assertOutsideTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
}
