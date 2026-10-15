package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.gift.infrastructure.JdbcGiftFulfillment;
import com.sajuppugi.gift.infrastructure.JdbcGiftPrivacy;
import com.sajuppugi.gift.infrastructure.JdbcGiftSettlement;
import com.sajuppugi.gift.infrastructure.JdbcGiftRecipientProfiles;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftFulfillmentJobs;
import com.sajuppugi.gift.infrastructure.JdbcGiftResultSnapshots;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Import(GiftRecipientPostgresTest.PrivacyConfig.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class GiftRecipientPostgresTest extends GiftLifecyclePostgresTest {
    @Autowired JdbcGiftFulfillment recipients;
    @Autowired JdbcGiftSettlement settlement;
    @Autowired JdbcGiftFulfillmentJobs fulfillmentJobs;
    @Autowired JdbcGiftResultSnapshots resultSnapshots;
    @Autowired @Qualifier("recipientPrivacy") JdbcGiftPrivacy privacy;
    @Autowired GiftPayloadCipher cipher;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    Instant time;
    final String snapshotHash = "a".repeat(64);
    @BeforeEach void setClock() {
        time = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(1);
        when(clock.instant()).thenAnswer(call -> time);
        when(clock.getZone()).thenReturn(java.time.ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(clock);
    }
    void advance(long seconds) { time = time.plusSeconds(seconds); }
    com.sajuppugi.gift.application.GiftFulfillmentPort.Receipt accept(UUID order, UUID request) throws Exception {
        return recipients.accept(token(order), request, snapshotHash, () -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue());
    }
    @Test void timelyReceiptSurvivesExpiryAndReviewThenStartsNinetyDaysOnProvision() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID request = UUID.randomUUID();
        advance(90L * 86400 - 1); var receipt = accept(order.orderId(), request);
        advance(2L * 86400); recipients.review(order.orderId());
        assertThat(access.verify(raw).giftId()).isEqualTo(receipt.giftId());
        assertThat(privacy.candidates()).doesNotContain(order.orderId()); assertThat(privacy.purge(order.orderId())).isFalse();
        var replay = recipients.accept(raw, request, snapshotHash, () -> { throw new AssertionError("Replay must not restore personal data"); });
        assertThat(replay.state()).isEqualTo("REVIEW_REQUIRED");
        UUID content = UUID.randomUUID(); var provided = recipients.provide(receipt.receiptId(), content, () -> {});
        assertThat(provided.firstProvidedAt()).isEqualTo(time);
        assertThat(provided.accessExpiresAt()).isEqualTo(time.plus(Duration.ofDays(90)));
        advance(1); assertThat(recipients.provide(receipt.receiptId(), content, () -> { throw new AssertionError(); }).firstProvidedAt()).isEqualTo(provided.firstProvidedAt());
        assertThatThrownBy(() -> recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {})).isInstanceOf(ApiException.class);
    }
    @Test void exactSubmissionDeadlineRejectsNewRequestWithoutWritingData() throws Exception {
        var order = active(); String raw = token(order.orderId()); advance(90L * 86400);
        assertThatThrownBy(() -> recipients.accept(raw, UUID.randomUUID(), snapshotHash, () -> { throw new AssertionError(); })).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_receipts WHERE order_id = ?", Integer.class, order.orderId())).isZero();
    }
    @Test void submissionBindingRejectsChangedSnapshotAndConcurrentReplaySavesOnce() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID request = UUID.randomUUID(); var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<UUID> task = () -> recipients.accept(raw, request, snapshotHash, calls::incrementAndGet).receiptId();
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS)); assertThat(calls).hasValue(1);
        } finally { pool.shutdownNow(); }
        assertThatThrownBy(() -> recipients.accept(raw, request, "b".repeat(64), () -> {})).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> recipients.accept(raw, UUID.randomUUID(), snapshotHash, () -> {})).isInstanceOf(ApiException.class);
    }
    @Test void recipientAndContentStorageFailureRollBackReceiptAndFirstProvision() throws Exception {
        var order = active(); String raw = token(order.orderId());
        assertThatThrownBy(() -> recipients.accept(raw, UUID.randomUUID(), snapshotHash, () -> {
            jdbc.update("UPDATE gift_order_drafts SET product_name = 'synthetic-rollback' WHERE id = ?", order.orderId());
            throw new IllegalStateException("Synthetic recipient write failure");
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT product_name FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isNotEqualTo("synthetic-rollback");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_receipts WHERE order_id = ?", Integer.class, order.orderId())).isZero();
        var receipt = accept(order.orderId(), UUID.randomUUID());
        assertThatThrownBy(() -> recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {
            jdbc.update("UPDATE gift_order_drafts SET product_name = 'synthetic-content-rollback' WHERE id = ?", order.orderId());
            throw new IllegalStateException("Synthetic content write failure");
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT first_provided_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order.orderId())).isNull();
        assertThat(jdbc.queryForObject("SELECT state FROM gift_recipient_receipts WHERE order_id = ?", String.class, order.orderId())).isEqualTo("ACCEPTED");
    }
    @Test void auditFailureRollsBackAcceptAndProvision() throws Exception {
        var order = active(); String raw = token(order.orderId());
        jdbc.execute("ALTER TABLE gift_delivery_events ADD CONSTRAINT synthetic_recipient_audit CHECK (action <> 'RECIPIENT_ACCEPTED')");
        try { assertThatThrownBy(() -> recipients.accept(raw, UUID.randomUUID(), snapshotHash, () -> {})).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_events DROP CONSTRAINT synthetic_recipient_audit"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_receipts WHERE order_id = ?", Integer.class, order.orderId())).isZero();
        var receipt = accept(order.orderId(), UUID.randomUUID());
        jdbc.execute("ALTER TABLE gift_delivery_events ADD CONSTRAINT synthetic_provision_audit CHECK (action <> 'CONTENT_FIRST_PROVIDED')");
        try { assertThatThrownBy(() -> recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {})).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_delivery_events DROP CONSTRAINT synthetic_provision_audit"); }
        assertThat(jdbc.queryForObject("SELECT first_provided_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order.orderId())).isNull();
    }
    @Test void cancellationBeforeProvisionRejectsLateContentAndReturnsOnlyOnce() throws Exception {
        var order = active(); var receipt = accept(order.orderId(), UUID.randomUUID());
        cancellations.cancel(owner, order.orderId(), "cancel-before-content");
        assertThatThrownBy(() -> recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> { throw new AssertionError(); })).isInstanceOf(ApiException.class);
        assertThat(returns()).isEqualTo(1);
        assertThat(privacy.candidates()).contains(order.orderId());
        assertThat(privacy.purge(order.orderId())).isTrue();
    }
    @Test void provisionCommitsUnderGiftLockBeforeCancellationAndSettlesDebit() throws Exception {
        var order = active(); var receipt = accept(order.orderId(), UUID.randomUUID());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var provision = pool.submit(() -> recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {
                entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError(); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var cancel = pool.submit(() -> { try { cancellations.cancel(owner, order.orderId(), "race-cancel"); return false; } catch (RuntimeException rejected) { return true; } });
            release.countDown(); provision.get(10, TimeUnit.SECONDS); assertThat(cancel.get(10, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); pool.shutdownNow(); }
        UUID transaction = jdbc.queryForObject("SELECT wallet_transaction_id FROM gift_purchase_work WHERE order_id = ?", UUID.class, order.orderId());
        assertThat(settlement.fulfilled(owner, order.quoteId(), transaction)).isTrue();
        assertThat(settlement.fulfilled(UUID.randomUUID(), order.quoteId(), transaction)).isFalse(); assertThat(returns()).isZero();
    }
    @Test void contentPrivacyExpiresExactlyAtFirstProvisionPlusNinetyDays() throws Exception {
        var order = active(); var receipt = accept(order.orderId(), UUID.randomUUID());
        recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {});
        advance(90L * 86400 - 1); assertThat(privacy.purge(order.orderId())).isFalse();
        advance(1); assertThat(privacy.candidates()).contains(order.orderId()); assertThat(privacy.purge(order.orderId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEmpty();
        assertThat(settlement.fulfilled(owner, order.quoteId(), jdbc.queryForObject("SELECT wallet_transaction_id FROM gift_purchase_work WHERE order_id = ?", UUID.class, order.orderId()))).isTrue();
    }
    @Test void linkedAccountRetainsGiftAndWithdrawalMakesPersonalDataImmediatelyEligible() throws Exception {
        var order = active(); String raw = token(order.orderId()); var receipt = accept(order.orderId(), UUID.randomUUID());
        recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {});
        var link = recipients.link(raw, owner); assertThat(link.giftId()).isEqualTo(receipt.giftId());
        recipients.link(raw, owner);
        assertThatThrownBy(() -> recipients.link(raw, UUID.randomUUID())).isInstanceOf(ApiException.class);
        advance(91L * 86400); assertThat(privacy.purge(order.orderId())).isFalse();
        assertThat(recipients.owned(owner, receipt.giftId()).contentId()).isNotNull();
        assertThatThrownBy(() -> recipients.owned(UUID.randomUUID(), receipt.giftId())).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING' WHERE id = ?", owner);
        assertThatThrownBy(() -> recipients.owned(owner, receipt.giftId())).isInstanceOf(ApiException.class);
        assertThat(privacy.candidates()).contains(order.orderId()); assertThat(privacy.purge(order.orderId())).isTrue();
    }
    @Test void freshUnclaimedExpiryHasNoExtraTwentyFourHourRetention() throws Exception {
        var order = active(); advance(90L * 86400);
        assertThat(privacy.purge(order.orderId())).isTrue(); assertThat(returns()).isZero();
    }
    @Test void purposeEndPurgesRecipientCiphertextAndPreventsPendingFulfillment() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        recipientService.submit(raw, "recipient-for-purge", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        assertThat(jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE gift_id = ?", String.class, giftId))
                .isNotBlank();
        cancellations.cancel(owner, order.orderId(), "cancel-profile-purpose");
        assertThat(privacy.purge(order.orderId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE gift_id = ?", String.class, giftId)).isNull();
        assertThat(jdbc.queryForObject("SELECT purged_at IS NOT NULL FROM gift_recipient_profiles WHERE gift_id = ?", Boolean.class, giftId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT purge_policy_version FROM gift_recipient_profiles WHERE gift_id = ?", String.class, giftId))
                .isEqualTo("gift-purpose-ended-v1");
        assertThat(jdbc.queryForObject("SELECT state FROM gift_fulfillment_jobs WHERE gift_id = ?", String.class, giftId)).isEqualTo("PURGED");
    }
    @Test void purposeEndWaitsForAnUnexpiredFulfillmentLease() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        recipientService.submit(raw, "recipient-lease", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        jdbc.update("UPDATE gift_fulfillment_jobs SET state = 'GENERATING', lease_token = ?, lease_until = ? WHERE gift_id = ?",
                UUID.randomUUID(), Timestamp.from(time.plusSeconds(60)), giftId);
        cancellations.cancel(owner, order.orderId(), "cancel-active-lease");
        assertThat(privacy.purge(order.orderId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE gift_id = ?", String.class, giftId))
                .isNotBlank();
        advance(61);
        assertThat(privacy.purge(order.orderId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE gift_id = ?", String.class, giftId)).isNull();
    }
    @Test void recipientProfileSupportsVersionedKeyRotationWithoutRestoringPurgedData() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        recipientService.submit(raw, "recipient-key-rotation", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        UUID profile = jdbc.queryForObject("SELECT id FROM gift_recipient_profiles WHERE gift_id = ?", UUID.class, giftId);
        String context = "gift-recipient-profile|" + giftId + "|" + profile;
        String before = jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE id = ?", String.class, profile);
        var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("test");
        var rotatedCipher = new GiftPayloadCipher(true, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", environment,
                "next", "{\"next\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"}", mapper);
        var profiles = new JdbcGiftRecipientProfiles(jdbc, rotatedCipher);
        Boolean changed = new TransactionTemplate(transactions).execute(status -> profiles.reencryptForOrder(order.orderId()));
        assertThat(changed).isTrue();
        String after = jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE id = ?", String.class, profile);
        assertThat(after).startsWith("v2:next:"); assertThat(rotatedCipher.decrypt(after, context)).isEqualTo(cipher.decrypt(before, context));
        assertThat(jdbc.queryForObject("SELECT encryption_key_id FROM gift_recipient_profiles WHERE id = ?", String.class, profile)).isEqualTo("next");
        cancellations.cancel(owner, order.orderId(), "purge-after-rotation"); assertThat(privacy.purge(order.orderId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE id = ?", String.class, profile)).isNull();
    }
    @Test void fulfillmentClaimUsesFenceToRejectLateWorkerCompletion() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        recipientService.submit(raw, "recipient-claim", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        UUID job = jdbc.queryForObject("SELECT id FROM gift_fulfillment_jobs WHERE gift_id = ?", UUID.class, giftId);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<java.util.Optional<JdbcGiftFulfillmentJobs.Lease>> claim = () ->
                    fulfillmentJobs.claim(job, time, Duration.ofSeconds(30), 5);
            var first = pool.submit(claim); var second = pool.submit(claim);
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertThat(java.util.stream.Stream.of(a, b).filter(java.util.Optional::isPresent)).hasSize(1);
            var original = a.or(() -> b).orElseThrow();
            assertThat(original.attempt()).isEqualTo(1); assertThat(original.fence()).isEqualTo(1);
            advance(31);
            var replacement = fulfillmentJobs.claim(job, time, Duration.ofSeconds(30), 5).orElseThrow();
            assertThat(replacement.fence()).isEqualTo(2);
            assertThat(fulfillmentJobs.complete(original, JdbcGiftFulfillmentJobs.State.READY, time)).isFalse();
            assertThat(fulfillmentJobs.complete(replacement, JdbcGiftFulfillmentJobs.State.WAITING_TALISMAN, time)).isTrue();
            assertThat(jdbc.queryForObject("SELECT state FROM gift_fulfillment_jobs WHERE id = ?", String.class, job)).isEqualTo("WAITING_TALISMAN");
        } finally { pool.shutdownNow(); }
    }
    @Test void exhaustedFulfillmentClaimMovesToReviewInsteadOfRetryingForever() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        recipientService.submit(raw, "recipient-exhausted", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        UUID job = jdbc.queryForObject("SELECT id FROM gift_fulfillment_jobs WHERE gift_id = ?", UUID.class, giftId);
        jdbc.update("UPDATE gift_fulfillment_jobs SET attempt_count = 5, state = 'RETRY', next_attempt_at = ? WHERE id = ?", Timestamp.from(time), job);
        assertThat(fulfillmentJobs.claim(job, time, Duration.ofSeconds(30), 5)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT state FROM gift_fulfillment_jobs WHERE id = ?", String.class, job)).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT last_failure_code FROM gift_fulfillment_jobs WHERE id = ?", String.class, job))
                .isEqualTo("FULFILLMENT_RETRY_EXHAUSTED");
    }
    @Test void immutableResultSnapshotRejectsConflictingWorkerOutput() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        var submission = recipientService.submit(raw, "result-snapshot", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        UUID contentId = UUID.randomUUID();
        var snapshot = new JdbcGiftResultSnapshots.Snapshot(UUID.randomUUID(), giftId, submission.receiptId(), contentId,
                "GIFT_SUNEUNG_TALISMAN_ONLY", "gift-v1", "fallback-v1", "{\"talisman\":{\"status\":\"PENDING\"}}",
                "b".repeat(64), time);
        new TransactionTemplate(transactions).executeWithoutResult(status -> resultSnapshots.insert(snapshot));
        assertThat(resultSnapshots.find(giftId)).contains(snapshot);
        var conflict = new JdbcGiftResultSnapshots.Snapshot(UUID.randomUUID(), giftId, submission.receiptId(), UUID.randomUUID(),
                "GIFT_SUNEUNG_TALISMAN_ONLY", "gift-v1", "fallback-v1", "{\"talisman\":{\"status\":\"PENDING\"}}",
                "c".repeat(64), time);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> resultSnapshots.insert(conflict)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Gift result snapshot conflict");
        assertThat(resultSnapshots.find(giftId)).contains(snapshot);
    }
    @Test void resultSnapshotCannotBindAnotherGiftsReceipt() throws Exception {
        var first = active(); var second = active();
        String firstToken = token(first.orderId()), secondToken = token(second.orderId());
        UUID firstGift = access.verify(firstToken).giftId();
        var secondReceipt = recipientService.submit(secondToken, "other-gift-receipt", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        var mismatch = new JdbcGiftResultSnapshots.Snapshot(UUID.randomUUID(), firstGift, secondReceipt.receiptId(), UUID.randomUUID(),
                "GIFT_SUNEUNG_TALISMAN_ONLY", "gift-v1", "fallback-v1", "{\"talisman\":{\"status\":\"PENDING\"}}",
                "e".repeat(64), time);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(transaction -> resultSnapshots.insert(mismatch)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("does not belong");
        assertThat(resultSnapshots.find(firstGift)).isEmpty();
    }
    @Test void publicResultPollsThenProvidesTheImmutableSnapshotOnce() throws Exception {
        var order = active(); String raw = token(order.orderId()); UUID giftId = access.verify(raw).giftId();
        mvc.perform(post("/api/v1/public/gifts/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.state").value("PROCESSING"));
        var submission = recipientService.submit(raw, "public-result", new com.sajuppugi.gift.application.GiftRecipientService.Command(
                "recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE));
        UUID contentId = UUID.randomUUID();
        var snapshot = new JdbcGiftResultSnapshots.Snapshot(UUID.randomUUID(), giftId, submission.receiptId(), contentId,
                "GIFT_SUNEUNG_TALISMAN_ONLY", "gift-v1", "fallback-v1", "{\"talisman\":{\"status\":\"PENDING\"}}",
                "d".repeat(64), time);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> resultSnapshots.insert(snapshot));
        mvc.perform(post("/api/v1/public/gifts/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("READY"))
                .andExpect(jsonPath("$.data.option").value("TALISMAN_ONLY"))
                .andExpect(jsonPath("$.data.result.talisman.status").value("PENDING"));
        assertThat(jdbc.queryForObject("SELECT state FROM gift_recipient_receipts WHERE id = ?", String.class, submission.receiptId()))
                .isEqualTo("PROVIDED");
        assertThat(jdbc.queryForObject("SELECT first_provided_at IS NOT NULL FROM gift_purchase_work WHERE gift_id = ?", Boolean.class, giftId)).isTrue();
    }
    @Test void linkedWithdrawalBeforeExpiryDoesNotWaitThirtyDaysOrContentDeadline() throws Exception {
        var order = active(); String raw = token(order.orderId()); var receipt = accept(order.orderId(), UUID.randomUUID());
        recipients.provide(receipt.receiptId(), UUID.randomUUID(), () -> {}); recipients.link(raw, owner);
        advance(1); jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING' WHERE id = ?", owner);
        assertThat(privacy.candidates()).contains(order.orderId()); assertThat(privacy.purge(order.orderId())).isTrue();
        assertThatThrownBy(() -> access.verify(raw)).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, order.orderId())).isEmpty();
        assertThat(returns()).isZero();
    }
    @Test void privacyFailureRetriesAreBoundedAndDoNotBlockHealthyExpiry() throws Exception {
        var bad = active(); var healthy = active(); advance(90L * 86400);
        jdbc.execute("ALTER TABLE gift_order_drafts ADD CONSTRAINT synthetic_privacy_block CHECK (id <> '" + bad.orderId() + "' OR private_payload <> '')");
        try {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertThatThrownBy(() -> privacy.purge(bad.orderId())).isInstanceOf(RuntimeException.class);
                privacy.failed(bad.orderId());
                assertThat(privacy.candidates()).doesNotContain(bad.orderId()).contains(healthy.orderId());
                advance(Math.min(600, 30L << (attempt - 1)) + 1);
            }
        } finally { jdbc.execute("ALTER TABLE gift_order_drafts DROP CONSTRAINT synthetic_privacy_block"); }
        assertThat(jdbc.queryForObject("SELECT private_cleanup_failures FROM gift_order_drafts WHERE id = ?", Integer.class, bad.orderId())).isEqualTo(5);
        assertThat(privacy.candidates()).doesNotContain(bad.orderId()); assertThat(privacy.purge(healthy.orderId())).isTrue();
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class PrivacyConfig {
        @Bean("recipientPrivacy") JdbcGiftPrivacy privacy(org.springframework.jdbc.core.JdbcTemplate jdbc,
                com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork work, com.sajuppugi.gift.infrastructure.JdbcGiftDeliveryWork audit,
                com.sajuppugi.gift.infrastructure.JdbcGiftRecipientProfiles profiles, Clock clock) {
            return new JdbcGiftPrivacy(jdbc, work, audit, profiles, clock, true);
        }
    }
}
