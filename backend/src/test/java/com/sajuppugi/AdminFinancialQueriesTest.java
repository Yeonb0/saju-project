package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sajuppugi.admin.application.*;
import com.sajuppugi.admin.infrastructure.JdbcAdminAudit;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.admin.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminFinancialQueriesTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminFinancialQueries queries;
    @Autowired com.sajuppugi.wallet.application.WalletTopUpPort wallet;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @MockitoSpyBean JdbcAdminAudit audit;
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    UUID operator, customer;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        operator = member(); customer = member();
        jdbc.update("INSERT INTO admin_operator_grants(user_id,active,can_recover,created_at,updated_at) VALUES (?,TRUE,FALSE,?,?)", operator, Timestamp.from(NOW), Timestamp.from(NOW));
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM admin_audit_events WHERE operator_id IN (?, ?)", operator, customer);
        jdbc.update("DELETE FROM admin_operator_grants WHERE user_id IN (?, ?)", operator, customer);
        jdbc.update("DELETE FROM top_up_orders WHERE user_id IN (?, ?)", operator, customer);
        jdbc.update("DELETE FROM wallet_integrity_findings WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_transaction_lines WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", customer);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", customer);
        jdbc.update("DELETE FROM user_sessions WHERE user_id IN (?, ?)", operator, customer);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", operator, customer);
    }
    @Test void roleClaimsCannotGrantAdminAccessAndAuthorizedViewsAreAudited() throws Exception {
        mvc.perform(get("/api/v1/admin/top-up-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/top-up-orders").with(user(customer.toString()).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/top-up-orders").with(user(operator.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
        mvc.perform(head("/api/v1/admin/top-up-orders").with(user(operator.toString()))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ?", Integer.class, operator)).isEqualTo(2);
    }
    @Test void revokedOrWithdrawnOperatorsLoseAccessImmediately() throws Exception {
        jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", operator);
        mvc.perform(get("/api/v1/admin/refunds").with(user(operator.toString()))).andExpect(status().isForbidden());
        jdbc.update("UPDATE admin_operator_grants SET active = TRUE WHERE user_id = ?", operator);
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING' WHERE id = ?", operator);
        mvc.perform(get("/api/v1/admin/refunds").with(user(operator.toString()))).andExpect(status().isForbidden());
    }
    @Test void realOpaqueSessionCookieUsesDatabaseGrantRatherThanClientRole() throws Exception {
        String raw = "synthetic-admin-" + UUID.randomUUID();
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO user_sessions(id,user_id,token_hash,csrf_secret_hash,expires_at,last_seen_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), operator, hash, "b".repeat(64), Timestamp.from(NOW.plusSeconds(3600)), Timestamp.from(NOW));
        var cookie = new Cookie("SAJUPPUGI_SESSION", raw);
        mvc.perform(get("/api/v1/admin/incidents").cookie(cookie)).andExpect(status().isOk());
        jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", operator);
        mvc.perform(get("/api/v1/admin/incidents").cookie(cookie).header("X-Role", "ADMIN")).andExpect(status().isForbidden());
    }
    @Test void paginationIsBoundedAndPrivatePaymentIdentityIsNeverProjected() throws Exception {
        for (int i = 0; i < 3; i++) jdbc.update("INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at) VALUES (?,?,?,'TEST',1000,50,6,'PAYMENT_PENDING',?)", UUID.randomUUID(), customer, UUID.randomUUID().toString(), Timestamp.from(NOW));
        var page = queries.orders(operator, null, 2);
        assertThat(page.items()).hasSize(2); assertThat(page.hasNext()).isTrue(); assertThat(page.nextCursor()).isNotNull();
        var next = queries.orders(operator, page.nextCursor(), 2);
        assertThat(next.items()).extracting(AdminFinancialQueries.AdminOrder::orderId).doesNotContainAnyElementsOf(page.items().stream().map(AdminFinancialQueries.AdminOrder::orderId).toList());
        String body = mvc.perform(get("/api/v1/admin/top-up-orders").with(user(operator.toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("keyHash", "key_hash", "paymentKey", "secret");
        mvc.perform(get("/api/v1/admin/refunds?limit=101").with(user(operator.toString()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/refunds?limit=0").with(user(operator.toString()))).andExpect(status().isBadRequest());
    }
    @Test void auditFailureDoesNotExposeTheQueriedFinancialResponse() throws Exception {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx ->
                doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).append(any()));
        mvc.perform(get("/api/v1/admin/top-up-orders").with(user(operator.toString()))).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ?", Integer.class, operator)).isZero();
    }
    @Test void walletInspectionShowsProjectionAndUsableLotsWithoutRepairingThem() {
        UUID order = UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at) VALUES (?,?,?,'TEST',1000,50,6,'CREDITED',?)", order, customer, order.toString(), Timestamp.from(NOW));
            wallet.credit(customer, order, 50, 6);
        });
        jdbc.update("UPDATE wallets SET paid_balance = 99 WHERE user_id = ?", customer);
        var view = queries.wallet(operator, customer);
        assertThat(view.storedPaidBalance()).isEqualTo(99); assertThat(view.usablePaidBalance()).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT paid_balance FROM wallets WHERE user_id = ?", Integer.class, customer)).isEqualTo(99);
        assertThat(queries.ledger(operator, customer, null, 10).items()).hasSize(1);
        assertThat(queries.audits(operator, null, 10).items()).isNotEmpty();
    }
    private UUID member() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,kakao_subject,nickname,status,created_at,updated_at) VALUES (?,?,'Synthetic member','ACTIVE',?,?)", id, "synthetic-" + id, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }
}
