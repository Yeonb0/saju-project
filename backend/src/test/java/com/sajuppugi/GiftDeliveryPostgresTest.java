package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.application.GiftDeliveryService;
import com.sajuppugi.gift.infrastructure.JdbcGiftDeliveryWork;
import com.sajuppugi.gift.infrastructure.JdbcGiftPrivacy;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork;
import com.sajuppugi.gift.infrastructure.JdbcGiftCipherMaintenance;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.application.GiftOrderQueryService;
import com.sajuppugi.gift.port.GiftAlimtalkProvider;
import com.sajuppugi.gift.port.GiftAlimtalkProvider.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.gift.delivery-enabled=true",
        "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Import(GiftDeliveryPostgresTest.PrivacyTestConfiguration.class)
class GiftDeliveryPostgresTest extends GiftLifecyclePostgresTest {
    @Autowired GiftDeliveryService deliveries;
    @Autowired JdbcGiftDeliveryWork deliveryStore;
    @Autowired GiftOrderQueryService queries;
    @Autowired JdbcGiftCipherMaintenance encryptionMaintenance;
    @Autowired @Qualifier("approvedTestPrivacy") JdbcGiftPrivacy privacy;
    @Autowired @Qualifier("jdbcGiftPrivacy") JdbcGiftPrivacy disabledPrivacy;
    @Autowired @Qualifier("approvedPhoneCleanup") com.sajuppugi.gift.infrastructure.JdbcGiftPhoneCleanup phoneCleanup;
    @Autowired @Qualifier("jdbcGiftPhoneCleanup") com.sajuppugi.gift.infrastructure.JdbcGiftPhoneCleanup disabledPhoneCleanup;
    @MockitoBean GiftAlimtalkProvider provider;
    @MockitoBean Clock clock;
    Instant time;
    @BeforeEach void providerSetup() {
        time = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(1); when(clock.instant()).thenAnswer(call -> time);
        when(provider.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Request request = call.getArgument(0);
            assertThat(request.recipientPhone()).isEqualTo("01012345678");
            assertThat(request.toString()).doesNotContain(request.recipientPhone(), request.token());
            return new Result(request.jobId(), Outcome.DELIVERED);
        });
        when(provider.lookup(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new Result(call.getArgument(0), Outcome.DELIVERED);
        });
    }
    void advance(long seconds) { time = time.plusSeconds(seconds); }
    UUID job(UUID order) { return jdbc.queryForObject("SELECT current_job_id FROM gift_delivery_outbox WHERE order_id = ?", UUID.class, order); }
    String state(UUID job) { return jdbc.queryForObject("SELECT state FROM gift_delivery_jobs WHERE id = ?", String.class, job); }
    void sent(UUID order) { deliveries.process(job(order)); assertThat(state(job(order))).isEqualTo("SENT"); }

    @Test void deliveredOnceClearsOutboxTokenButNotGiftAccess() throws Exception {
        var order = active(); String token = token(order.orderId()); UUID id = job(order.orderId());
        deliveries.process(id); deliveries.process(id);
        assertThat(state(id)).isEqualTo("SENT");
        assertThat(access.verify(token).giftId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE order_id = ?", String.class, order.orderId())).isEmpty();
        verify(provider, times(1)).send(any()); verify(provider, never()).lookup(any());
    }
    @Test void acceptedIsNotDeliveredAndOnlyLookupMayCompleteIt() {
        var order = active(); UUID id = job(order.orderId());
        doReturn(new Result(id, Outcome.ACCEPTED)).when(provider).send(any());
        deliveries.process(id); assertThat(state(id)).isEqualTo("UNKNOWN");
        assertThat(deliveries.get(owner, order.orderId()).canResend()).isFalse();
        advance(31); deliveries.recoverDue(); assertThat(state(id)).isEqualTo("SENT");
        verify(provider, times(1)).send(any()); verify(provider, times(1)).lookup(id);
    }
    @Test void lostSendResponseRecoversWithoutSecondSend() {
        var order = active(); UUID id = job(order.orderId());
        doThrow(new IllegalStateException("synthetic-response-loss")).when(provider).send(any());
        deliveries.process(id); advance(31); deliveries.recoverDue();
        assertThat(state(id)).isEqualTo("SENT"); verify(provider, times(1)).send(any()); verify(provider).lookup(id);
    }
    @Test void abandonedClaimUsesLookupBeforeConfirmedAbsentRetry() {
        var order = active(); UUID id = job(order.orderId());
        deliveryStore.claim(id).orElseThrow(); advance(181);
        when(provider.lookup(id)).thenReturn(new Result(id, Outcome.NOT_FOUND));
        deliveries.process(id); assertThat(state(id)).isEqualTo("RETRY"); verify(provider, never()).send(any());
        advance(61); deliveries.process(id); assertThat(state(id)).isEqualTo("SENT"); verify(provider, times(1)).send(any());
    }
    @Test void lateLeaseCannotOverwriteCurrentClaim() {
        var order = active(); UUID id = job(order.orderId());
        var old = deliveryStore.claim(id).orElseThrow(); advance(181);
        var current = deliveryStore.claim(id).orElseThrow();
        assertThat(current.lookup()).isTrue();
        deliveryStore.apply(old, new Result(id, Outcome.DELIVERED)); assertThat(state(id)).isEqualTo("IN_FLIGHT");
        deliveryStore.apply(current, new Result(id, Outcome.DELIVERED)); assertThat(state(id)).isEqualTo("SENT");
    }
    @Test void unknownLookupsStopAtFiveWithoutManualResend() {
        var order = active(); UUID id = job(order.orderId());
        doReturn(new Result(id, Outcome.UNKNOWN)).when(provider).send(any());
        when(provider.lookup(id)).thenReturn(new Result(id, Outcome.UNKNOWN));
        deliveries.process(id);
        for (int i = 0; i < 5; i++) { advance(31); deliveries.process(id); }
        assertThat(state(id)).isEqualTo("REVIEW_REQUIRED");
        assertThat(deliveries.get(owner, order.orderId()).canResend()).isFalse();
        assertThatThrownBy(() -> deliveries.resend(owner, order.orderId(), new IdempotencyKey("unsafe"))).isInstanceOf(ApiException.class);
        verify(provider, times(1)).send(any()); verify(provider, times(5)).lookup(id);
    }
    @Test void confirmedTransientRejectionHasExactlyThreeAutomaticRetries() {
        var order = active(); UUID id = job(order.orderId());
        doReturn(new Result(id, Outcome.RETRYABLE_REJECTION)).when(provider).send(any());
        for (long delay : new long[]{61, 301, 1801}) { deliveries.process(id); assertThat(state(id)).isEqualTo("RETRY"); advance(delay); }
        deliveries.process(id); assertThat(state(id)).isEqualTo("FAILED");
        verify(provider, times(4)).send(any()); verify(provider, never()).lookup(any());
    }
    @Test void acceptedButNotFoundRequiresReviewNeverResends() {
        var order = active(); UUID id = job(order.orderId());
        doReturn(new Result(id, Outcome.ACCEPTED)).when(provider).send(any());
        when(provider.lookup(id)).thenReturn(new Result(id, Outcome.NOT_FOUND));
        deliveries.process(id); advance(31); deliveries.process(id);
        assertThat(state(id)).isEqualTo("REVIEW_REQUIRED"); verify(provider, times(1)).send(any());
    }
    @Test void mismatchedResultCannotConfirmDelivery() {
        var order = active(); UUID id = job(order.orderId());
        doReturn(new Result(UUID.randomUUID(), Outcome.DELIVERED)).when(provider).send(any());
        deliveries.process(id); assertThat(state(id)).isEqualTo("UNKNOWN");
    }
    @Test void manualRotationIsIdempotentRevokesOldTokenAndNeverExtendsExpiry() throws Exception {
        var order = active(); String oldToken = token(order.orderId()); UUID oldJob = job(order.orderId());
        Timestamp expiry = jdbc.queryForObject("SELECT access_expires_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order.orderId());
        sent(order.orderId());
        deliveries.resend(owner, order.orderId(), new IdempotencyKey("manual")); UUID replacement = job(order.orderId());
        String newToken = token(order.orderId());
        deliveries.resend(owner, order.orderId(), new IdempotencyKey("manual"));
        assertThat(job(order.orderId())).isEqualTo(replacement).isNotEqualTo(oldJob);
        assertThatThrownBy(() -> access.verify(oldToken)).isInstanceOf(ApiException.class);
        assertThat(access.verify(newToken).giftId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT access_expires_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order.orderId())).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_delivery_commands WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(1);
        assertThatThrownBy(() -> deliveries.reissueLink(owner, order.orderId(), new IdempotencyKey("manual")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }
    @Test void resendAndReissueShareDailyLimitAndOneMinuteCooldown() {
        var order = active(); sent(order.orderId());
        for (int i = 0; i < 3; i++) {
            deliveries.reissueLink(owner, order.orderId(), new IdempotencyKey("manual-" + i)); sent(order.orderId());
            assertThatThrownBy(() -> deliveries.resend(owner, order.orderId(), new IdempotencyKey("too-soon"))).isInstanceOf(ApiException.class);
            advance(61);
        }
        assertThat(deliveries.get(owner, order.orderId()).remainingResends()).isZero();
        assertThatThrownBy(() -> deliveries.resend(owner, order.orderId(), new IdempotencyKey("fourth"))).isInstanceOf(ApiException.class);
    }
    @Test void koreaMidnightResetsCountButNotCooldown() {
        var order = active(); sent(order.orderId()); time = Instant.parse("2026-10-13T14:59:40Z");
        deliveries.resend(owner, order.orderId(), new IdempotencyKey("before-midnight")); sent(order.orderId()); advance(20);
        assertThat(deliveries.get(owner, order.orderId()).remainingResends()).isEqualTo(3);
        assertThat(deliveries.get(owner, order.orderId()).canResend()).isFalse(); advance(41);
        deliveries.reissueLink(owner, order.orderId(), new IdempotencyKey("next-day"));
        assertThat(deliveries.get(owner, order.orderId()).remainingResends()).isEqualTo(2);
    }
    @Test void concurrentWorkersSendOnlyOnce() throws Exception {
        var order = active(); UUID id = job(order.orderId());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); return new Result(id, Outcome.DELIVERED); }).when(provider).send(any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> deliveries.process(id)); assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var b = pool.submit(() -> deliveries.process(id)); b.get(10, TimeUnit.SECONDS); release.countDown(); a.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(state(id)).isEqualTo("SENT"); verify(provider, times(1)).send(any());
    }
    @Test void cancellationFencesInFlightResultAndRevokesAccess() throws Exception {
        var order = active(); UUID id = job(order.orderId()); String token = token(order.orderId());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); return new Result(id, Outcome.DELIVERED); }).when(provider).send(any());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var send = pool.submit(() -> deliveries.process(id)); assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            cancellations.cancel(owner, order.orderId(), "cancel"); release.countDown(); send.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(state(id)).isEqualTo("CANCELED"); assertThat(balance()).isEqualTo(20);
        assertThatThrownBy(() -> access.verify(token)).isInstanceOf(ApiException.class);
        assertThat(deliveries.get(owner, order.orderId()).canResend()).isFalse();
    }
    @Test void completionAuditFailureRecoversByLookupWithoutResending() {
        var order = active(); UUID id = job(order.orderId());
        jdbc.execute("ALTER TABLE gift_delivery_events ADD CONSTRAINT synthetic_delivery_audit CHECK (action <> 'DELIVERY_SENT')");
        try { assertThatThrownBy(() -> deliveries.process(id)).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_events DROP CONSTRAINT synthetic_delivery_audit"); }
        assertThat(state(id)).isEqualTo("IN_FLIGHT"); advance(181); deliveries.process(id);
        assertThat(state(id)).isEqualTo("SENT"); verify(provider, times(1)).send(any()); verify(provider).lookup(id);
    }
    @Test void manualAuditFailureRollsBackNewTokenAndJob() throws Exception {
        var order = active(); UUID id = job(order.orderId()); String token = token(order.orderId()); sent(order.orderId());
        jdbc.execute("ALTER TABLE gift_delivery_events ADD CONSTRAINT synthetic_manual_audit CHECK (action <> 'MANUAL_RESEND')");
        try { assertThatThrownBy(() -> deliveries.resend(owner, order.orderId(), new IdempotencyKey("manual"))).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_events DROP CONSTRAINT synthetic_manual_audit"); }
        assertThat(job(order.orderId())).isEqualTo(id); assertThat(access.verify(token).giftId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_delivery_commands WHERE order_id = ?", Integer.class, order.orderId())).isZero();
    }
    @Test void ownerHttpHasNoRawTokenAndRequiresCsrf() throws Exception {
        var order = active(); sent(order.orderId());
        mvc.perform(get("/api/v1/gift-orders/{id}/delivery", order.orderId()).with(user(UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/gift-orders/{id}/deliveries", order.orderId()).with(user(owner.toString())).header("Idempotency-Key", "m")).andExpect(status().isForbidden());
        String response = mvc.perform(post("/api/v1/gift-orders/{id}/deliveries", order.orderId()).with(user(owner.toString())).with(csrf()).header("Idempotency-Key", "m"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("01012345678", "token", "sender", "recipient");
    }
    @Test void expiredGiftStopsDeliveryWithoutRefundOrReactivation() {
        var order = active(); UUID id = job(order.orderId()); advance(91L * 86400); deliveries.process(id);
        assertThat(state(id)).isEqualTo("CANCELED"); verifyNoInteractions(provider);
        assertThat(returns()).isZero(); assertThat(work.get(owner, order.orderId()).status()).isEqualTo("ACTIVE");
    }
    @Test void ownerListPaginatesEqualTimestampsWithoutLeaksOrDuplicates() throws Exception {
        java.util.Set<UUID> expected = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) expected.add(draft().orderId());
        var first = queries.list(owner, null, 2); var second = queries.list(owner, first.nextCursor(), 2);
        assertThat(first.hasNext()).isTrue(); assertThat(second.hasNext()).isFalse(); assertThat(second.nextCursor()).isNull();
        java.util.Set<UUID> actual = new java.util.HashSet<>();
        first.items().forEach(item -> actual.add(item.orderId())); second.items().forEach(item -> actual.add(item.orderId()));
        assertThat(actual).isEqualTo(expected);
        assertThat(queries.list(UUID.randomUUID(), first.nextCursor(), 20).items()).isEmpty();
        String json = mvc.perform(get("/api/v1/gift-orders").with(user(owner.toString())).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("01012345678", "private_payload", "requestHash", "Good luck", "token");
        mvc.perform(get("/api/v1/gift-orders")).andExpect(status().isUnauthorized());
    }
    @Test void listRejectsInvalidSizeAndCursor() {
        assertThatThrownBy(() -> queries.list(owner, null, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> queries.list(owner, null, 51)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> queries.list(owner, "not-base64!", 20)).isInstanceOf(ApiException.class);
        String bad = java.util.Base64.getUrlEncoder().encodeToString("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> queries.list(owner, bad, 20)).isInstanceOf(ApiException.class);
    }
    @Test void disabledCleanupNeverDeletesAnythingAndZeroPeriodCannotEnableIt() {
        var order = active(); advance(200L * 86400);
        assertThat(disabledPrivacy.candidates()).isEmpty(); assertThat(disabledPrivacy.purge(order.orderId())).isFalse();
        assertThatThrownBy(() -> new JdbcGiftPrivacy(jdbc, null, deliveryStore, clock, true, java.time.Duration.ZERO)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isNotEmpty();
    }
    @Test void cleanupRetainsFinancialFactsAndDoesNotRehydrateReplayPersonalData() {
        var order = active(); cancellations.cancel(owner, order.orderId(), "cancel"); advance(2L * 86400);
        assertThat(privacy.candidates()).contains(order.orderId()); assertThat(privacy.purge(order.orderId())).isTrue();
        assertThat(privacy.purge(order.orderId())).isFalse(); assertThat(returns()).isEqualTo(1); assertThat(balance()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT recipient_phone_masked FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEqualTo("REDACTED");
        var replay = drafts.create(owner, order.quoteId(), new com.sajuppugi.gift.application.GiftOrderService.Payload("recipient", "01012345678", "sender", "Good luck!"), "replay-after-purge");
        assertThat(replay.orderId()).isEqualTo(order.orderId());
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEmpty();
    }
    @Test void cleanupDoesNotTouchUnexpiredOrReviewRequiredGifts() {
        var order = active(); advance(2L * 86400);
        assertThat(privacy.candidates()).doesNotContain(order.orderId()); assertThat(privacy.purge(order.orderId())).isFalse();
        jdbc.update("UPDATE gift_purchase_work SET status = 'REVIEW_REQUIRED' WHERE order_id = ?", order.orderId()); advance(200L * 86400);
        assertThat(privacy.candidates()).doesNotContain(order.orderId()); assertThat(privacy.purge(order.orderId())).isFalse();
    }
    @Test void expiredGiftPrivacyCleanupRevokesTokenWithoutRefund() {
        var order = active(); advance(92L * 86400);
        assertThat(privacy.purge(order.orderId())).isTrue(); assertThat(state(job(order.orderId()))).isEqualTo("CANCELED");
        assertThat(work.get(owner, order.orderId()).status()).isEqualTo("ACTIVE"); assertThat(returns()).isZero();
        assertThat(jdbc.queryForObject("SELECT revoked FROM gift_purchase_tokens WHERE order_id = ?", Boolean.class, order.orderId())).isTrue();
    }
    @Test void expiredDraftCanBePurgedButCannotLaterCharge() {
        var order = draft(); advance(2L * 86400); assertThat(privacy.purge(order.orderId())).isTrue();
        commands.purchase(owner, order.orderId(), "expired-buy");
        assertThat(purchases.resume(owner, order.orderId()).status()).isEqualTo("FAILED"); assertThat(balance()).isEqualTo(20);
    }
    @Test void privacyAuditFailureRollsBackPayloadRemovalAndRevocation() {
        var order = active(); advance(92L * 86400);
        jdbc.execute("ALTER TABLE gift_delivery_events ADD CONSTRAINT synthetic_privacy_audit CHECK (action <> 'PRIVATE_PAYLOAD_PURGED')");
        try { assertThatThrownBy(() -> privacy.purge(order.orderId())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_events DROP CONSTRAINT synthetic_privacy_audit"); }
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT revoked FROM gift_purchase_tokens WHERE order_id = ?", Boolean.class, order.orderId())).isFalse();
        assertThat(privacy.purge(order.orderId())).isTrue();
    }
    @Test void liveDeliveryLeaseBlocksPrivacyCleanup() {
        var order = active(); deliveryStore.claim(job(order.orderId())).orElseThrow(); advance(92L * 86400);
        jdbc.update("UPDATE gift_delivery_jobs SET lease_until = ? WHERE id = ?", Timestamp.from(time.plusSeconds(60)), job(order.orderId()));
        assertThat(privacy.purge(order.orderId())).isFalse(); assertThat(privacy.candidates()).doesNotContain(order.orderId());
        advance(61); assertThat(privacy.purge(order.orderId())).isTrue();
    }
    @Test void legacyPayloadReencryptionKeepsCommandFingerprintsAndToken() throws Exception {
        var order = active(); String originalToken = token(order.orderId());
        var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("test");
        var legacy = new GiftPayloadCipher(true, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", environment);
        UUID outbox = jdbc.queryForObject("SELECT id FROM gift_delivery_outbox WHERE order_id = ?", UUID.class, order.orderId());
        String body = jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId());
        String delivery = jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE id = ?", String.class, outbox);
        jdbc.update("UPDATE gift_order_drafts SET private_payload = ? WHERE id = ?", legacy.encrypt(cipher.decrypt(body, owner + "|" + order.orderId()), owner + "|" + order.orderId()), order.orderId());
        jdbc.update("UPDATE gift_delivery_outbox SET private_payload = ? WHERE id = ?", legacy.encrypt(cipher.decrypt(delivery, "gift-delivery|" + outbox), "gift-delivery|" + outbox), outbox);
        assertThat(encryptionMaintenance.candidates()).contains(order.orderId());
        assertThat(encryptionMaintenance.reencrypt(order.orderId())).isTrue();
        assertThat(encryptionMaintenance.reencrypt(order.orderId())).isFalse();
        assertThat(token(order.orderId())).isEqualTo(originalToken);
        assertThat(cipher.fingerprint("same-request")).isEqualTo(legacy.fingerprint("same-request"));
        sent(order.orderId()); assertThat(access.verify(originalToken).giftId()).isNotNull();
    }
    @Test void ciphertextRewriteRollsBackBothFieldsIfOutboxWriteFails() throws Exception {
        var order = active(); UUID outbox = jdbc.queryForObject("SELECT id FROM gift_delivery_outbox WHERE order_id = ?", UUID.class, order.orderId());
        var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("test");
        var legacy = new GiftPayloadCipher(true, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", environment);
        String body = cipher.decrypt(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId()), owner + "|" + order.orderId());
        String delivery = cipher.decrypt(jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE id = ?", String.class, outbox), "gift-delivery|" + outbox);
        String oldBody = legacy.encrypt(body, owner + "|" + order.orderId()), oldDelivery = legacy.encrypt(delivery, "gift-delivery|" + outbox);
        jdbc.update("UPDATE gift_order_drafts SET private_payload = ? WHERE id = ?", oldBody, order.orderId());
        jdbc.update("UPDATE gift_delivery_outbox SET private_payload = ? WHERE id = ?", oldDelivery, outbox);
        jdbc.execute("ALTER TABLE gift_delivery_outbox ADD CONSTRAINT synthetic_reencrypt_failure CHECK (private_payload NOT LIKE 'v2:%')");
        try { assertThatThrownBy(() -> encryptionMaintenance.reencrypt(order.orderId())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_outbox DROP CONSTRAINT synthetic_reencrypt_failure"); }
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEqualTo(oldBody);
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE id = ?", String.class, outbox)).isEqualTo(oldDelivery);
        assertThat(encryptionMaintenance.reencrypt(order.orderId())).isTrue();
    }
    @Test void poisonCiphertextHasBoundedRetriesAndCannotStarveHealthyRewrites() {
        var bad = active(); var healthy = active();
        var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("test");
        var legacy = new GiftPayloadCipher(true, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", environment);
        String context = owner + "|" + healthy.orderId();
        String original = jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, healthy.orderId());
        jdbc.update("UPDATE gift_order_drafts SET private_payload = ? WHERE id = ?", legacy.encrypt(cipher.decrypt(original, context), context), healthy.orderId());
        jdbc.update("UPDATE gift_order_drafts SET private_payload = 'v2:missing:invalid' WHERE id = ?", bad.orderId());
        for (int failure = 1; failure <= 5; failure++) {
            assertThatThrownBy(() -> encryptionMaintenance.reencrypt(bad.orderId()))
                    .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                    .hasMessageContaining("Gift payload authentication failed");
            encryptionMaintenance.failed(bad.orderId());
            assertThat(encryptionMaintenance.candidates()).doesNotContain(bad.orderId()).contains(healthy.orderId());
            advance(Math.min(600, 30L << (failure - 1)) + 1);
        }
        assertThat(jdbc.queryForObject("SELECT state FROM gift_payload_reencryption_work WHERE order_id = ?", String.class, bad.orderId())).isEqualTo("REVIEW_REQUIRED");
        assertThat(encryptionMaintenance.candidates()).doesNotContain(bad.orderId());
        assertThat(encryptionMaintenance.reencrypt(healthy.orderId())).isTrue(); assertThat(returns()).isZero();
    }
    @Test void deliveryMigrationBackfillsOldPendingOutboxWithoutChangingPayload() throws Exception {
        var order = active(); UUID originalJob = job(order.orderId());
        String originalPayload = jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE id = ?", String.class, originalJob);
        String schema = "gift_delivery_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("TEST_DATABASE_URL"), user = System.getenv("TEST_DATABASE_USERNAME"), password = System.getenv("TEST_DATABASE_PASSWORD");
        try (var connection = java.sql.DriverManager.getConnection(url, user, password)) {
            try {
                org.flywaydb.core.Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                        .target(org.flywaydb.core.api.MigrationVersion.fromVersion("202610132400")).load().migrate();
                connection.createStatement().execute("SET search_path TO " + schema);
                for (String sql : java.util.List.of(
                        "INSERT INTO purchase_quotes SELECT * FROM public.purchase_quotes WHERE id = '" + order.quoteId() + "'",
                        "INSERT INTO gift_order_drafts SELECT id,user_id,quote_id,request_hash,product_code,product_name,shell_amount,recipient_phone_masked,private_payload,status,created_at,expires_at FROM public.gift_order_drafts WHERE id = '" + order.orderId() + "'",
                        "INSERT INTO gift_purchase_work SELECT * FROM public.gift_purchase_work WHERE order_id = '" + order.orderId() + "'",
                        "INSERT INTO gift_purchase_tokens SELECT * FROM public.gift_purchase_tokens WHERE order_id = '" + order.orderId() + "'",
                        "INSERT INTO gift_delivery_outbox SELECT id,order_id,private_payload,status,created_at FROM public.gift_delivery_outbox WHERE order_id = '" + order.orderId() + "'"))
                    connection.createStatement().execute(sql);
                assertThat(org.flywaydb.core.Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                        .target(org.flywaydb.core.api.MigrationVersion.fromVersion("202610132500")).load().migrate().migrationsExecuted).isEqualTo(1);
                try (var rows = connection.createStatement().executeQuery("SELECT o.current_job_id,o.private_payload,j.state FROM gift_delivery_outbox o JOIN gift_delivery_jobs j ON j.id = o.current_job_id")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getObject(1, UUID.class)).isEqualTo(originalJob);
                    assertThat(rows.getString(2)).isEqualTo(originalPayload); assertThat(rows.getString(3)).isEqualTo("PENDING");
                }
            } finally {
                assertThat(schema).matches("gift_delivery_upgrade_[a-f0-9]{32}");
                connection.createStatement().execute("SET search_path TO public"); connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
    @Test void thirtyDayBoundaryBlocksDeliveryButPreservesNinetyDayAccess() throws Exception {
        var order = active(); String token = token(order.orderId()); sent(order.orderId());
        advance(30L * 86400 - 1);
        assertThat(deliveries.get(owner, order.orderId()).canResend()).isTrue();
        assertThat(phoneCleanup.purge(order.orderId())).isFalse();
        advance(1);
        assertThat(deliveries.get(owner, order.orderId()).canResend()).isFalse();
        assertThatThrownBy(() -> deliveries.resend(owner, order.orderId(), new IdempotencyKey("day30")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> deliveries.reissueLink(owner, order.orderId(), new IdempotencyKey("day30-reissue")))
                .isInstanceOf(ApiException.class);
        assertThat(phoneCleanup.candidates()).contains(order.orderId());
        assertThat(phoneCleanup.purge(order.orderId())).isTrue();
        assertThat(phoneCleanup.purge(order.orderId())).isFalse();
        String encrypted = jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId());
        String remaining = cipher.decrypt(encrypted, owner + "|" + order.orderId());
        assertThat(remaining).doesNotContain("recipientPhone", "01012345678").contains("Good luck!", "recipient", "sender");
        assertThat(queries.list(owner, null, 20).items().getFirst().recipientPhoneMasked()).isEqualTo("REDACTED");
        assertThat(access.verify(token).giftId()).isNotNull(); assertThat(returns()).isZero();
        var replay = drafts.create(owner, order.quoteId(), new com.sajuppugi.gift.application.GiftOrderService.Payload("recipient", "01012345678", "sender", "Good luck!"), "replay-phone-cleanup");
        assertThat(replay.orderId()).isEqualTo(order.orderId());
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEqualTo(encrypted);
    }
    @Test void phoneCleanupPreservesUnknownOutcomeLookupWithoutResending() throws Exception {
        var order = active(); String token = token(order.orderId()); UUID id = job(order.orderId());
        doThrow(new IllegalStateException("synthetic timeout")).when(provider).send(any());
        deliveries.process(id); assertThat(state(id)).isEqualTo("UNKNOWN");
        advance(30L * 86400); assertThat(phoneCleanup.purge(order.orderId())).isTrue();
        deliveries.process(id); assertThat(state(id)).isEqualTo("SENT");
        verify(provider, times(1)).send(any()); verify(provider, times(1)).lookup(id);
        assertThat(access.verify(token).giftId()).isNotNull();
    }
    @Test void verifiedAbsenceAfterThirtyDaysCannotScheduleAnotherSend() {
        var order = active(); UUID id = job(order.orderId());
        deliveryStore.claim(id).orElseThrow(); advance(30L * 86400);
        doReturn(new Result(id, Outcome.NOT_FOUND)).when(provider).lookup(id);
        phoneCleanup.purge(order.orderId()); deliveries.process(id);
        assertThat(state(id)).isEqualTo("CANCELED"); verify(provider, never()).send(any());
    }
    @Test void phoneCleanupFencesLateSendButRetainsLookupAndAccess() throws Exception {
        var order = active(); String token = token(order.orderId()); UUID id = job(order.orderId());
        advance(30L * 86400 - 1); var lease = deliveryStore.claim(id).orElseThrow();
        advance(1); assertThat(phoneCleanup.purge(order.orderId())).isTrue();
        deliveryStore.apply(lease, new Result(id, Outcome.DELIVERED));
        assertThat(state(id)).isEqualTo("IN_FLIGHT"); advance(181); deliveries.process(id);
        assertThat(state(id)).isEqualTo("SENT"); verify(provider, never()).send(any());
        assertThat(access.verify(token).giftId()).isNotNull();
    }
    @Test void freshPendingDeliveryStopsAtThirtyDaysWithoutTokenRevocation() throws Exception {
        var order = active(); String token = token(order.orderId()); advance(30L * 86400);
        assertThat(disabledPhoneCleanup.candidates()).isEmpty(); assertThat(disabledPhoneCleanup.purge(order.orderId())).isFalse();
        assertThat(phoneCleanup.purge(order.orderId())).isTrue(); deliveries.process(job(order.orderId()));
        assertThat(state(job(order.orderId()))).isEqualTo("CANCELED"); verify(provider, never()).send(any());
        assertThat(access.verify(token).giftId()).isNotNull();
    }
    @Test void phoneCleanupAuditFailureRollsBackPersonalDataRemoval() {
        var order = active(); advance(30L * 86400);
        String payload = jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId());
        jdbc.execute("ALTER TABLE gift_delivery_events ADD CONSTRAINT synthetic_phone_audit CHECK (action <> 'RECIPIENT_PHONE_PURGED')");
        try { assertThatThrownBy(() -> phoneCleanup.purge(order.orderId())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_events DROP CONSTRAINT synthetic_phone_audit"); }
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEqualTo(payload);
        assertThat(jdbc.queryForObject("SELECT phone_purged_at FROM gift_order_drafts WHERE id = ?", Timestamp.class, order.orderId())).isNull();
        assertThat(phoneCleanup.purge(order.orderId())).isTrue();
    }
    @Test void corruptedPhoneCleanupHasBoundedRetriesAndDoesNotStarveHealthyOrder() {
        var bad = active(); var healthy = active(); advance(30L * 86400);
        jdbc.update("UPDATE gift_order_drafts SET private_payload = 'invalid' WHERE id = ?", bad.orderId());
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> phoneCleanup.purge(bad.orderId())).isInstanceOf(RuntimeException.class);
            phoneCleanup.failed(bad.orderId());
            assertThat(phoneCleanup.candidates()).doesNotContain(bad.orderId()).contains(healthy.orderId());
            advance(Math.min(600, 30L << (attempt - 1)) + 1);
        }
        assertThat(jdbc.queryForObject("SELECT phone_cleanup_failures FROM gift_order_drafts WHERE id = ?", Integer.class, bad.orderId())).isEqualTo(5);
        assertThat(phoneCleanup.candidates()).doesNotContain(bad.orderId()); assertThat(phoneCleanup.purge(healthy.orderId())).isTrue();
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class PrivacyTestConfiguration {
        @Bean @org.springframework.context.annotation.Primary GiftPayloadCipher versionedTestCipher() {
            var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("test");
            byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 1);
            String encoded = java.util.Base64.getEncoder().encodeToString(key);
            return new GiftPayloadCipher(true, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", environment,
                    "rotation_2", "{\"rotation_2\":\"" + encoded + "\"}", new com.fasterxml.jackson.databind.ObjectMapper());
        }
        @Bean("approvedTestPrivacy") JdbcGiftPrivacy approved(org.springframework.jdbc.core.JdbcTemplate jdbc,
                JdbcGiftPurchaseWork work, JdbcGiftDeliveryWork deliveries, Clock clock) {
            return new JdbcGiftPrivacy(jdbc, work, deliveries, clock, true, java.time.Duration.ofDays(1));
        }
        @Bean("approvedPhoneCleanup") com.sajuppugi.gift.infrastructure.JdbcGiftPhoneCleanup phoneCleanup(org.springframework.jdbc.core.JdbcTemplate jdbc,
                JdbcGiftPurchaseWork work, JdbcGiftDeliveryWork deliveries, GiftPayloadCipher cipher, com.fasterxml.jackson.databind.ObjectMapper mapper, Clock clock) {
            return new com.sajuppugi.gift.infrastructure.JdbcGiftPhoneCleanup(jdbc, work, deliveries, cipher, mapper, clock, true);
        }
    }
}
