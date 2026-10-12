package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.admin.application.AdminPaymentRecoveryService;
import com.sajuppugi.admin.application.AuditLogPort;
import com.sajuppugi.admin.infrastructure.JdbcAdminAudit;
import com.sajuppugi.admin.infrastructure.JdbcAdminRecovery;
import com.sajuppugi.admin.infrastructure.JdbcAdminRecovery.Reason;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.payment.port.PaymentProviderPort.*;
import com.sajuppugi.wallet.application.WalletTopUpPort;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.admin.enabled=true", "app.admin.recovery-enabled=true", "app.admin.recovery-poll-delay=PT1H", "app.toss.refunds-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminPaymentRecoveryTest {
    @Autowired AdminPaymentRecoveryService service;
    @Autowired JdbcAdminRecovery commands;
    @Autowired JdbcCashRefunds refunds;
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletTopUpPort wallet;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean Clock clock;
    @MockitoBean PaymentProviderPort provider;
    @MockitoSpyBean JdbcAdminAudit audit;
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    UUID operator, viewer, customer;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        operator = member(); viewer = member(); customer = member();
        grant(operator, true); grant(viewer, false);
        when(provider.lookup(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            UUID order = call.getArgument(0); return order == null ? null : evidence(order, Outcome.APPROVED);
        });
    }
    @AfterEach void cleanup() {
        verify(provider, never()).confirm(any(), any());
        verify(provider, never()).cancel(any(), any(), anyInt(), anyString());
        jdbc.update("DELETE FROM admin_recovery_commands WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", customer);
        jdbc.update("DELETE FROM admin_audit_events WHERE operator_id IN (?, ?, ?)", operator, viewer, customer);
        jdbc.update("DELETE FROM wallet_cash_refund_returns WHERE refund_id IN (SELECT id FROM top_up_refunds WHERE user_id = ?)", customer);
        jdbc.update("DELETE FROM top_up_refund_requests WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM top_up_refunds WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", customer);
        jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_integrity_findings WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_transaction_lines WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM admin_operator_grants WHERE user_id IN (?, ?, ?)", operator, viewer, customer);
        jdbc.update("DELETE FROM user_sessions WHERE user_id IN (?, ?, ?)", operator, viewer, customer);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", operator, viewer, customer);
    }
    @Test void verifiedApprovalRecoversGrantOnceAndRequestReplayDoesNotCallPgAgain() {
        UUID order = order(false);
        var first = service.request(operator, order, "intent", Reason.SYSTEM_RECOVERY);
        var second = service.request(operator, order, "intent", Reason.SYSTEM_RECOVERY);
        assertThat(first.state()).isEqualTo("COMPLETED"); assertThat(first.resultCode()).isEqualTo("CREDITED");
        assertThat(second.commandId()).isEqualTo(first.commandId());
        assertThat(balance()).isEqualTo(56);
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", order)).isEqualTo(1);
        verify(provider, times(1)).lookup(order);
        assertThat(count("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ? AND action = 'ADMIN_RECOVERY_COMPLETED'", operator)).isEqualTo(1);
    }
    @Test void reusedKeyWithDifferentOrderOrReasonFailsBeforeProviderCall() {
        UUID first = order(false), second = order(false);
        service.request(operator, first, "same", Reason.SYSTEM_RECOVERY);
        assertThatThrownBy(() -> service.request(operator, second, "same", Reason.SYSTEM_RECOVERY)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.request(operator, first, "same", Reason.CUSTOMER_INQUIRY)).isInstanceOf(RuntimeException.class);
        verify(provider, times(1)).lookup(any());
    }
    @Test void simultaneousDifferentCommandsStillCreditTheSameOrderOnlyOnce() throws Exception {
        UUID order = order(false);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = pool.submit(() -> service.request(operator, order, "one", Reason.SYSTEM_RECOVERY));
            var two = pool.submit(() -> service.request(operator, order, "two", Reason.SYSTEM_RECOVERY));
            assertThat(one.get(15, TimeUnit.SECONDS).state()).isEqualTo("COMPLETED");
            assertThat(two.get(15, TimeUnit.SECONDS).state()).isEqualTo("COMPLETED");
        }
        assertThat(balance()).isEqualTo(56);
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", order)).isEqualTo(1);
    }
    @Test void wrongEvidenceIsBoundedAndNeverChangesWalletOrResetsFinancialRetryCounters() {
        UUID order = order(false);
        doReturn(new Result(Outcome.APPROVED, order, "wrong", 1000, "KRW", null)).when(provider).lookup(order);
        var view = service.request(operator, order, "intent", Reason.SYSTEM_RECOVERY);
        assertThat(view.state()).isEqualTo("RETRY");
        for (int i = 1; i <= 2; i++) { when(clock.instant()).thenReturn(NOW.plusSeconds(31L * i)); service.recover(view.commandId()); }
        assertThat(commands.view(operator, view.commandId()).state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", order)).isZero();
        assertThat(count("SELECT attempts FROM top_up_executions WHERE order_id = ?", order)).isEqualTo(5);
    }
    @Test void revokedOperatorCannotFinishFinancialRecoveryAfterProviderLookup() {
        UUID order = order(false);
        doAnswer(call -> {
            jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", operator);
            return evidence(order, Outcome.APPROVED);
        }).when(provider).lookup(order);
        assertThatThrownBy(() -> service.request(operator, order, "intent", Reason.SYSTEM_RECOVERY)).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", order)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM admin_recovery_commands WHERE order_id = ?", String.class, order)).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void staleCommandLeaseCannotCreditAndNewLeaseCanComplete() {
        UUID order = order(false);
        UUID command = commands.begin(operator, order, "intent", Reason.SYSTEM_RECOVERY);
        var stale = commands.claim(command).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(181));
        var fresh = commands.claim(command).orElseThrow();
        commands.finish(stale, evidence(order, Outcome.APPROVED));
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", order)).isZero();
        commands.finish(fresh, evidence(order, Outcome.APPROVED));
        assertThat(balance()).isEqualTo(56);
    }
    @Test void auditFailureRollsBackGrantAndCommandCompletionTogetherThenLookupRecoverySucceeds() {
        UUID order = order(false);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> doAnswer(call -> {
            AuditLogPort.Entry entry = call.getArgument(0);
            if ("ADMIN_RECOVERY_COMPLETED".equals(entry.action())) throw new IllegalStateException("Synthetic audit failure");
            return call.callRealMethod();
        }).when(audit).append(any()));
        assertThatThrownBy(() -> service.request(operator, order, "intent", Reason.SYSTEM_RECOVERY)).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", order)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM top_up_orders WHERE id = ?", String.class, order)).isEqualTo("PAYMENT_PENDING");
        UUID command = jdbc.queryForObject("SELECT id FROM admin_recovery_commands WHERE order_id = ?", UUID.class, order);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> doCallRealMethod().when(audit).append(any()));
        when(clock.instant()).thenReturn(NOW.plusSeconds(181)); service.recover(command);
        assertThat(commands.view(operator, command).state()).isEqualTo("COMPLETED"); assertThat(balance()).isEqualTo(56);
    }
    @Test void canceledPreviouslyReservedRefundCanBeClosedFromVerifiedLookupWithoutNewCancel() {
        UUID order = order(true);
        refunds.prepare(customer, order, "refund", NOW);
        jdbc.update("UPDATE top_up_refunds SET state = 'REVIEW_REQUIRED', attempts = 5 WHERE order_id = ?", order);
        doReturn(evidence(order, Outcome.CANCELED)).when(provider).lookup(order);
        var view = service.request(operator, order, "resolve", Reason.CUSTOMER_INQUIRY);
        assertThat(view.resultCode()).isEqualTo("REFUNDED"); assertThat(view.state()).isEqualTo("COMPLETED");
        assertThat(balance()).isZero(); assertThat(refunds.owned(customer, order).state()).isEqualTo("REFUNDED");
    }
    @Test void stillApprovedUnknownRefundCannotBeReportedAsResolvedOrReturned() {
        UUID order = order(true); refunds.prepare(customer, order, "refund", NOW);
        jdbc.update("UPDATE top_up_refunds SET state = 'REVIEW_REQUIRED', attempts = 5 WHERE order_id = ?", order);
        var result = service.request(operator, order, "look-only", Reason.CUSTOMER_INQUIRY);
        assertThat(result.state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(balance()).isZero(); assertThat(refunds.owned(customer, order).state()).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void ledgerCorruptionPreventsAdditionalFinancialRecovery() {
        UUID first = order(true), pending = order(false);
        jdbc.update("UPDATE wallets SET paid_balance = 99 WHERE user_id = ?", customer);
        var result = service.request(operator, pending, "inspect", Reason.SYSTEM_RECOVERY);
        assertThat(result.state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(count("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", pending)).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND resolved_at IS NULL", customer)).isEqualTo(1);
    }
    @Test void actualSessionCsrfAndReadOnlyCapabilityAreEnforcedForRecoveryHttp() throws Exception {
        UUID order = order(false); String path = "/api/v1/admin/top-up-orders/" + order + "/reconcile";
        mvc.perform(post(path).with(user(viewer.toString()).roles("ADMIN")).with(csrf()).header("Idempotency-Key", "intent")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SYSTEM_RECOVERY\"}")).andExpect(status().isForbidden());
        String raw = "synthetic-admin-" + UUID.randomUUID();
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO user_sessions(id,user_id,token_hash,csrf_secret_hash,expires_at,last_seen_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), operator, hash, "b".repeat(64), Timestamp.from(NOW.plusSeconds(3600)), Timestamp.from(NOW));
        var cookie = new Cookie("SAJUPPUGI_SESSION", raw);
        mvc.perform(post(path).cookie(cookie).header("Idempotency-Key", "intent").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"SYSTEM_RECOVERY\"}")).andExpect(status().isForbidden());
        String session = mvc.perform(get("/api/v1/session").cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String csrf = mapper.readTree(session).path("data").path("csrfToken").asText();
        mvc.perform(post(path).cookie(cookie).header("X-CSRF-Token", csrf).header("Idempotency-Key", "intent")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SYSTEM_RECOVERY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("COMPLETED"));
    }
    private UUID order(boolean credited) {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at) VALUES (?,?,?,'TEST',1000,50,6,?,?)", id, customer, id.toString(), credited ? "CREDITED" : "PAYMENT_PENDING", Timestamp.from(NOW));
            jdbc.update("INSERT INTO top_up_confirmation_intents(order_id,payment_key_hash,amount_krw,created_at) VALUES (?,?,1000,?)", id, hash(id), Timestamp.from(NOW));
            jdbc.update("INSERT INTO top_up_executions(order_id,state,attempts,confirmation_started,next_attempt_at,updated_at) VALUES (?,'REVIEW_REQUIRED',5,TRUE,?,?)", id, Timestamp.from(NOW), Timestamp.from(NOW));
            if (credited) wallet.credit(customer, id, 50, 6);
        }); return id;
    }
    private UUID member() {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO users(id,kakao_subject,nickname,status,created_at,updated_at) VALUES (?,?,'Synthetic member','ACTIVE',?,?)", id, "synthetic-" + id, Timestamp.from(NOW), Timestamp.from(NOW)); return id;
    }
    private void grant(UUID user, boolean recovery) { jdbc.update("INSERT INTO admin_operator_grants(user_id,active,can_recover,created_at,updated_at) VALUES (?,TRUE,?,?,?)", user, recovery, Timestamp.from(NOW), Timestamp.from(NOW)); }
    private String hash(UUID order) { return order.toString().replace("-", "").repeat(2); }
    private Result evidence(UUID order, Outcome state) { return new Result(state, order, hash(order), 1000, "KRW", null); }
    private int count(String sql, UUID id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private int balance() { return count("SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?", customer); }
}
