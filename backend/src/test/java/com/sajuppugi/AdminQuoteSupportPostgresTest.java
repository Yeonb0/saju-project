package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sajuppugi.admin.application.AdminPurchaseSupportQueries;
import com.sajuppugi.admin.infrastructure.JdbcAdminAudit;
import com.sajuppugi.common.api.ApiException;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.admin.enabled=true",
        "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class AdminQuoteSupportPostgresTest extends GiftLifecyclePostgresTest {
    @Autowired AdminPurchaseSupportQueries support;
    @Autowired com.sajuppugi.catalog.application.CatalogUseCase catalog;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoSpyBean JdbcAdminAudit adminAudit;
    UUID operator;

    @BeforeEach void operator() {
        operator = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,kakao_subject,status,created_at,updated_at) VALUES (?,?,'ACTIVE',?,?)",
                operator, "quote-support-" + operator, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO admin_operator_grants(user_id,active,can_recover,created_at,updated_at) VALUES (?,TRUE,FALSE,?,?)",
                operator, Timestamp.from(now), Timestamp.from(now));
    }
    @AfterEach void removeOperator() {
        reset(org.springframework.test.util.AopTestUtils.<JdbcAdminAudit>getUltimateTargetObject(adminAudit));
        jdbc.update("DELETE FROM admin_audit_events WHERE operator_id = ?", operator);
        jdbc.update("DELETE FROM admin_operator_grants WHERE user_id = ?", operator);
        jdbc.update("DELETE FROM users WHERE id = ?", operator);
    }

    @Test void expiredQuoteWithoutDebitIsQueryableAndAuditedWithoutPrivateContext() throws Exception {
        var order = draft();
        jdbc.update("UPDATE purchase_quotes SET created_at = ?,expires_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.minusSeconds(1800)), order.quoteId());
        String body = mvc.perform(get("/api/v1/admin/purchase-quotes/{id}", order.quoteId()).with(user(operator.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.financialState").value("NO_DEBIT_RECEIPT"))
                .andExpect(jsonPath("$.data.giftOrderId").value(order.orderId().toString()))
                .andExpect(jsonPath("$.data.quotedAmount").value(10)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("contextHash", "context_hash", "recipient", "Good luck!", "keyHash", "birth", "token");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ? AND action = 'ADMIN_PURCHASE_QUOTE_VIEW' AND target_id = ?", Integer.class, operator, order.quoteId())).isEqualTo(1);
        assertThat(balance()).isEqualTo(20); assertThat(returns()).isZero();
    }

    @Test void debitAndReturnAreDurableFactsAndRepeatedLookupDoesNotMutateLedger() {
        var order = active();
        var debited = support.quote(operator, order.quoteId());
        assertThat(debited.financialState()).isEqualTo("DEBIT_COMMITTED");
        assertThat(debited.debitedShells()).isEqualTo(10); assertThat(debited.debitedAt()).isNotNull();
        assertThat(debited.returnTransactionId()).isNull();
        cancellations.cancel(owner, order.orderId(), "support-return");
        var returned = support.quote(operator, order.quoteId());
        assertThat(returned.financialState()).isEqualTo("RETURN_COMMITTED");
        assertThat(returned.returnedShells()).isEqualTo(10); assertThat(returned.returnedAt()).isNotNull();
        assertThat(support.quote(operator, order.quoteId())).isEqualTo(returned);
        assertThat(returned.debitTransactionId()).isEqualTo(debited.debitTransactionId());
        assertThat(balance()).isEqualTo(20); assertThat(returns()).isEqualTo(1);
    }

    @Test void noClientRoleOrRevokedOperatorCanAccessAndUnknownQuoteIsHidden() throws Exception {
        var order = draft();
        mvc.perform(get("/api/v1/admin/purchase-quotes/{id}", order.quoteId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/purchase-quotes/{id}", order.quoteId()).with(user(owner.toString()).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/purchase-quotes/{id}", UUID.randomUUID()).with(user(operator.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/purchase-quotes/invalid").with(user(operator.toString()))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", operator);
        assertThatThrownBy(() -> support.quote(operator, order.quoteId())).isInstanceOf(ApiException.class);
    }

    @Test void auditFailureBlocksResponseWithoutFinancialWrites() throws Exception {
        var order = active();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx ->
                doThrow(new IllegalStateException("synthetic-support-audit-loss")).when(adminAudit).append(any()));
        mvc.perform(get("/api/v1/admin/purchase-quotes/{id}", order.quoteId()).with(user(operator.toString()))).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ?", Integer.class, operator)).isZero();
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }

    @Test void readingQuoteReturnsBoundReferenceWithoutInventingGiftOrGenerationState() {
        var product = catalog.availableProducts(com.sajuppugi.catalog.domain.Product.Category.READING).getFirst();
        var quote = catalog.issueQuote(new com.sajuppugi.catalog.application.CatalogUseCase.IssueQuote(owner, product.code(), "b".repeat(64)));
        UUID bound = UUID.randomUUID();
        jdbc.update("UPDATE purchase_quotes SET purchase_id = ? WHERE id = ?", bound, quote.id());
        var view = support.quote(operator, quote.id());
        assertThat(view.category()).isEqualTo("READING");
        assertThat(view.quotedAmount()).isEqualTo(product.price().amount());
        assertThat(view.giftOrderId()).isNull(); assertThat(view.boundReferenceId()).isEqualTo(bound);
        assertThat(view.financialState()).isEqualTo("NO_DEBIT_RECEIPT");
        assertThat(balance()).isEqualTo(20); assertThat(returns()).isZero();
    }
}
