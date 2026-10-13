package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sajuppugi.admin.application.AdminGiftQueries;
import com.sajuppugi.admin.application.AdminGiftMaintenanceService;
import com.sajuppugi.admin.application.AdminGiftMaintenanceService.Reason;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.gift.application.GiftMaintenanceRecoveryPort.Kind;
import com.sajuppugi.gift.infrastructure.JdbcGiftPhoneCleanup;
import com.sajuppugi.gift.infrastructure.JdbcGiftPrivacy;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.admin.enabled=true", "app.admin.recovery-enabled=true",
        "app.admin.recovery-poll-delay=PT1H", "app.gift.phone-cleanup-enabled=true", "app.gift.retention-enabled=true",
        "app.gift.phone-cleanup-poll-delay=PT1H", "app.gift.retention-poll-delay=PT1H",
        "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.gift.payload-active-key-id=test", "app.gift.payload-keyring={\"test\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"}",
        "app.gift.payload-reencryption-enabled=true", "app.gift.payload-reencryption-poll-delay=PT1H"})
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class AdminGiftOperationsPostgresTest extends GiftLifecyclePostgresTest {
    @Autowired AdminGiftQueries admin;
    @Autowired AdminGiftMaintenanceService maintenance;
    @Autowired JdbcGiftPhoneCleanup phones;
    @Autowired JdbcGiftPrivacy privacy;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    UUID operator, viewer;
    @BeforeEach void operators() {
        operator = member(); viewer = member(); grantRole(operator, true); grantRole(viewer, false);
    }
    @AfterEach void removeOperators() {
        jdbc.update("DELETE FROM admin_gift_maintenance_requests WHERE operator_id IN (?,?)", operator, viewer);
        jdbc.update("DELETE FROM admin_audit_events WHERE operator_id IN (?,?)", operator, viewer);
        jdbc.update("DELETE FROM admin_operator_grants WHERE user_id IN (?,?)", operator, viewer);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", operator, viewer);
    }
    UUID member() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,kakao_subject,status,created_at,updated_at) VALUES (?,?,'ACTIVE',?,?)",
                id, "admin-gift-" + id, Timestamp.from(now), Timestamp.from(now)); return id;
    }
    void grantRole(UUID id, boolean recovery) {
        jdbc.update("INSERT INTO admin_operator_grants(user_id,active,can_recover,created_at,updated_at) VALUES (?,TRUE,?,?,?)",
                id, recovery, Timestamp.from(now), Timestamp.from(now));
    }
    void exhausted(UUID order) { jdbc.update("UPDATE gift_order_drafts SET phone_cleanup_failures = 5 WHERE id = ?", order); }
    void request(UUID order, String key) { maintenance.request(operator, order, Kind.PHONE_CLEANUP, key, Reason.SYSTEM_RECOVERY); }
    int failures(UUID order) { return jdbc.queryForObject("SELECT phone_cleanup_failures FROM gift_order_drafts WHERE id = ?", Integer.class, order); }
    long audits(String action) { return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_events WHERE operator_id = ? AND action = ?", Long.class, operator, action); }

    @Test void authenticatedRoleClaimCannotGrantAccessAndViewsDoNotLeakPayloads() throws Exception {
        var order = active(); String rawToken = token(order.orderId());
        mvc.perform(get("/api/v1/admin/gift-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/gift-orders").with(user(owner.toString()).roles("ADMIN"))).andExpect(status().isForbidden());
        String json = mvc.perform(get("/api/v1/admin/gift-orders/{id}", order.orderId()).with(user(viewer.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.order.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.order.chargedShells").value(10)).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("01012345678", "Good luck!", rawToken, "private_payload", "key_hash", "lease_token", "recipientPhone", "recipientName");
        assertThat(admin.detail(operator, order.orderId()).deliveries()).hasSize(1);
        assertThatThrownBy(() -> admin.detail(operator, UUID.randomUUID())).isInstanceOf(ApiException.class);
    }
    @Test void reviewFilterAndStableCursorIncludeFailuresAndOverdueAcceptedReceipts() {
        var first = active(); var second = draft(); exhausted(first.orderId());
        var page = admin.list(operator, owner, null, 1, false);
        assertThat(page.items()).hasSize(1); assertThat(page.hasNext()).isTrue();
        var next = admin.list(operator, owner, page.nextCursor(), 1, false);
        assertThat(next.items()).hasSize(1); assertThat(next.items().getFirst().orderId()).isNotEqualTo(page.items().getFirst().orderId());
        assertThat(admin.list(operator, owner, null, 20, true).items()).extracting(AdminGiftQueries.Summary::orderId).containsExactly(first.orderId());
        assertThat(admin.detail(operator, second.orderId()).order().status()).isEqualTo("CREATED");
        jdbc.update("UPDATE gift_order_drafts SET phone_cleanup_failures = 0 WHERE id = ?", first.orderId());
        jdbc.update("INSERT INTO gift_recipient_receipts(id,order_id,request_hash,submitted_at,review_at,state) VALUES (?,?,?,?,?,'ACCEPTED')",
                UUID.randomUUID(), first.orderId(), "a".repeat(64), Timestamp.from(now.minusSeconds(90000)), Timestamp.from(now.minusSeconds(1)));
        assertThat(admin.list(operator, owner, null, 20, true).items()).hasSize(1);
        assertThatThrownBy(() -> admin.list(operator, owner, null, 51, false)).isInstanceOf(ApiException.class);
    }
    @Test void reopeningRequiresRecoveryPermissionAndCsrf() throws Exception {
        var order = active(); exhausted(order.orderId());
        String body = "{\"kind\":\"PHONE_CLEANUP\",\"reasonCode\":\"SYSTEM_RECOVERY\"}";
        mvc.perform(post("/api/v1/admin/gift-orders/{id}/maintenance-reopens", order.orderId()).with(user(operator.toString()))
                .header("Idempotency-Key", "csrf").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/gift-orders/{id}/maintenance-reopens", order.orderId()).with(user(viewer.toString())).with(csrf())
                .header("Idempotency-Key", "viewer").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/gift-orders/{id}/maintenance-reopens", order.orderId()).with(user(operator.toString())).with(csrf())
                .header("Idempotency-Key", "allowed").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("REOPENED"));
        assertThat(failures(order.orderId())).isZero();
        assertThat(admin.detail(operator, order.orderId()).order().phonePurgedAt()).isNull();
    }
    @Test void replayCannotResetAnotherFailedCycleAndChangedBindingIsRejected() {
        var order = active(); exhausted(order.orderId()); var other = draft();
        var original = maintenance.request(operator, order.orderId(), Kind.PHONE_CLEANUP, "replay", Reason.SYSTEM_RECOVERY);
        exhausted(order.orderId());
        assertThat(maintenance.request(operator, order.orderId(), Kind.PHONE_CLEANUP, "replay", Reason.SYSTEM_RECOVERY)).isEqualTo(original);
        assertThat(failures(order.orderId())).isEqualTo(5);
        assertThat(audits("ADMIN_GIFT_MAINTENANCE_REOPEN")).isEqualTo(1);
        assertThatThrownBy(() -> request(other.orderId(), "replay")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> maintenance.request(operator, order.orderId(), Kind.PRIVATE_CLEANUP, "replay", Reason.SYSTEM_RECOVERY)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> maintenance.request(operator, order.orderId(), Kind.PHONE_CLEANUP, "replay", Reason.CUSTOMER_INQUIRY)).isInstanceOf(ApiException.class);
    }
    @Test void concurrentSameIntentReopensExactlyOnce() throws Exception {
        var order = active(); exhausted(order.orderId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { request(order.orderId(), "parallel"); });
            var b = pool.submit(() -> { request(order.orderId(), "parallel"); });
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(audits("ADMIN_GIFT_MAINTENANCE_REOPEN")).isEqualTo(1);
        assertThat(admin.detail(operator, order.orderId()).maintenance().getFirst().requestedCycles()).isEqualTo(1);
    }
    @Test void differentOperatorsShareThreeCycleLimit() {
        var order = active(); grantRoleForViewer();
        for (int cycle = 0; cycle < 3; cycle++) {
            exhausted(order.orderId()); maintenance.request(cycle == 1 ? viewer : operator, order.orderId(), Kind.PHONE_CLEANUP, "cycle-" + cycle, Reason.SYSTEM_RECOVERY);
        }
        exhausted(order.orderId()); assertThatThrownBy(() -> request(order.orderId(), "fourth")).isInstanceOf(ApiException.class);
        assertThat(failures(order.orderId())).isEqualTo(5);
        assertThat(admin.detail(operator, order.orderId()).maintenance().getFirst().remainingCycles()).isZero();
    }
    void grantRoleForViewer() { jdbc.update("UPDATE admin_operator_grants SET can_recover = TRUE WHERE user_id = ?", viewer); }
    @Test void healthyOrAlreadyPurgedWorkCannotBeReopened() {
        var order = active();
        assertThatThrownBy(() -> request(order.orderId(), "healthy")).isInstanceOf(ApiException.class);
        exhausted(order.orderId()); jdbc.update("UPDATE gift_order_drafts SET phone_purged_at = ? WHERE id = ?", Timestamp.from(now), order.orderId());
        assertThatThrownBy(() -> request(order.orderId(), "purged")).isInstanceOf(ApiException.class);
        assertThat(audits("ADMIN_GIFT_MAINTENANCE_REOPEN")).isZero();
    }
    @Test void auditFailureRollsBackRequestAndRetryResetAndBlocksReadResponse() {
        var order = active(); exhausted(order.orderId());
        jdbc.execute("ALTER TABLE admin_audit_events ADD CONSTRAINT synthetic_gift_admin_audit CHECK (action NOT IN ('ADMIN_GIFT_MAINTENANCE_REOPEN','ADMIN_GIFT_DETAIL_VIEW'))");
        try {
            assertThatThrownBy(() -> request(order.orderId(), "audit-fault")).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> admin.detail(operator, order.orderId())).isInstanceOf(RuntimeException.class);
        } finally { jdbc.execute("ALTER TABLE admin_audit_events DROP CONSTRAINT synthetic_gift_admin_audit"); }
        assertThat(failures(order.orderId())).isEqualTo(5);
        assertThat(admin.detail(operator, order.orderId()).maintenance()).isEmpty();
    }
    @Test void revokedOperatorCannotReplayIntent() {
        var order = active(); exhausted(order.orderId()); request(order.orderId(), "revoke");
        jdbc.update("UPDATE admin_operator_grants SET active = FALSE WHERE user_id = ?", operator);
        assertThatThrownBy(() -> request(order.orderId(), "revoke")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> admin.detail(operator, order.orderId())).isInstanceOf(ApiException.class);
    }
    @Test void cleanupRetryDoesNotBypassPurposeOrRestorePersonalData() {
        var order = active(); exhausted(order.orderId()); request(order.orderId(), "purpose");
        assertThat(phones.purge(order.orderId())).isFalse();
        jdbc.update("UPDATE gift_order_drafts SET private_cleanup_failures = 5 WHERE id = ?", order.orderId());
        maintenance.request(operator, order.orderId(), Kind.PRIVATE_CLEANUP, "private", Reason.SYSTEM_RECOVERY);
        assertThat(privacy.purge(order.orderId())).isFalse();
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }
    @Test void successfulWorkerCleanupAfterReopenAndReplayNeverRestoresPhone() {
        var order = active(); exhausted(order.orderId()); request(order.orderId(), "worker");
        jdbc.update("UPDATE gift_purchase_work SET approved_at = ? WHERE order_id = ?", Timestamp.from(now.minusSeconds(31L * 86400)), order.orderId());
        assertThat(phones.candidates()).contains(order.orderId()); assertThat(phones.purge(order.orderId())).isTrue();
        request(order.orderId(), "worker");
        assertThat(admin.detail(operator, order.orderId()).order().phonePurgedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE order_id = ?", String.class, order.orderId())).isEmpty();
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }
    @Test void disabledMaintenanceWorkersCannotBeBypassed() {
        var order = active(); exhausted(order.orderId());
        var blocked = new com.sajuppugi.gift.infrastructure.JdbcGiftMaintenanceRecovery(jdbc,
                new com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork(jdbc), cipher, false, false, false);
        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                .execute(status -> { blocked.reopen(order.orderId(), Kind.PHONE_CLEANUP, () -> true); return null; }))
                .isInstanceOf(ApiException.class);
        assertThat(failures(order.orderId())).isEqualTo(5);
    }
    @Test void cipherReviewResumesWithoutEditingCiphertextAndOtherKindsHaveIndependentLimits() {
        var order = active();
        String original = jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId());
        jdbc.update("INSERT INTO gift_payload_reencryption_work(order_id,active_key_id,failures,state,next_attempt_at,updated_at) VALUES (?,'test',5,'REVIEW_REQUIRED',?,?)",
                order.orderId(), Timestamp.from(now), Timestamp.from(now));
        maintenance.request(operator, order.orderId(), Kind.PAYLOAD_REENCRYPTION, "cipher", Reason.SYSTEM_RECOVERY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_payload_reencryption_work WHERE order_id = ?", Integer.class, order.orderId())).isZero();
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEqualTo(original);
        assertThat(balance()).isEqualTo(10);
    }
}
