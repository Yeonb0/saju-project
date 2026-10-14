package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.application.GiftLinkService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GiftDirectLinkPostgresTest extends GiftLifecyclePostgresTest {
    @Autowired GiftLinkService links;
    @MockitoBean Clock linkClock;
    Instant linkNow;
    @BeforeEach void clock() {
        linkNow = now.plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        org.mockito.Mockito.when(linkClock.instant()).thenAnswer(call -> linkNow);
        org.mockito.Mockito.when(linkClock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    }
    private GiftLinkService.Link reissue(UUID id, String key) { return links.reissue(owner, id, new IdempotencyKey(key)); }

    @Test void replayKeepsItsOwnVersionAndNeverRevealsANewerToken() throws Exception {
        var order = active(); String old = token(order.orderId());
        var first = reissue(order.orderId(), "first");
        assertThat(reissue(order.orderId(), "first").shareUrl()).isEqualTo(first.shareUrl());
        linkNow = linkNow.plusSeconds(60);
        var second = reissue(order.orderId(), "second");
        var replay = reissue(order.orderId(), "first");
        assertThat(replay.tokenVersion()).isEqualTo(first.tokenVersion());
        assertThat(replay.shareUrl()).isNull(); assertThat(replay.reused()).isTrue();
        assertThat(links.get(owner, order.orderId()).shareUrl()).isEqualTo(second.shareUrl());
        assertThatThrownBy(() -> access.verify(old)).isInstanceOf(ApiException.class);
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }

    @Test void sameIntentConcurrentReissueRotatesExactlyOnce() throws Exception {
        var order = active(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return reissue(order.orderId(), "same"); });
            var b = pool.submit(() -> { start.await(); return reissue(order.orderId(), "same"); });
            start.countDown();
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertThat(first.shareUrl()).isEqualTo(second.shareUrl());
            assertThat(first.tokenVersion()).isEqualTo(2); assertThat(second.tokenVersion()).isEqualTo(2);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_link_commands WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(1);
    }

    @Test void concurrentCrossOrderKeyReuseReturnsBusinessConflictWithoutRotatingLoser() throws Exception {
        var first = active(); var second = active(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return conflictOrVersion(first.orderId()); });
            var b = pool.submit(() -> { start.await(); return conflictOrVersion(second.orderId()); });
            start.countDown(); assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(2L, -1L);
        }
        assertThat(jdbc.queryForObject("SELECT SUM(token_version) FROM gift_purchase_tokens WHERE order_id IN (?,?)", Long.class, first.orderId(), second.orderId())).isEqualTo(3);
        assertThat(balance()).isZero(); assertThat(returns()).isZero();
    }
    private long conflictOrVersion(UUID id) {
        try { return reissue(id, "cross-order").tokenVersion(); }
        catch (ApiException conflict) { assertThat(conflict.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED); return -1; }
    }

    @Test void expiredRevealReplayDoesNotRestoreCiphertextOrExtendGiftValidity() {
        var order = active(); var first = reissue(order.orderId(), "expired-replay");
        jdbc.update("UPDATE gift_purchase_tokens SET reveal_until = ? WHERE order_id = ?", Timestamp.from(now.minusSeconds(1)), order.orderId());
        links.purgeExpired();
        var replay = reissue(order.orderId(), "expired-replay");
        assertThat(replay.shareUrl()).isNull(); assertThat(replay.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(jdbc.queryForObject("SELECT token_ciphertext FROM gift_purchase_tokens WHERE order_id = ?", String.class, order.orderId())).isNull();
    }

    @Test void linkHttpRequiresOwnershipCsrfAndUsesNoStore() throws Exception {
        var order = active();
        mvc.perform(get("/api/v1/gift-orders/{id}/link", order.orderId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/gift-orders/{id}/link", order.orderId()).with(user(UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/gift-orders/{id}/link-reissues", order.orderId()).with(user(owner.toString())).header("Idempotency-Key", "csrf"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/gift-orders/{id}/link", order.orderId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test void minuteIntervalDailyThreeAndReplayAreEnforcedByServer() {
        var order = active(); Instant start = linkNow;
        reissue(order.orderId(), "one");
        assertThat(links.get(owner, order.orderId()).remainingReissues()).isEqualTo(2);
        reissue(order.orderId(), "one");
        linkNow = start.plusSeconds(59);
        assertThatThrownBy(() -> reissue(order.orderId(), "too-soon")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GIFT_LINK_REISSUE_LIMIT_EXCEEDED));
        linkNow = start.plusSeconds(60); reissue(order.orderId(), "two");
        linkNow = start.plusSeconds(120); reissue(order.orderId(), "three");
        linkNow = start.plusSeconds(180);
        assertThat(links.get(owner, order.orderId()).remainingReissues()).isZero();
        assertThatThrownBy(() -> reissue(order.orderId(), "four")).isInstanceOf(ApiException.class);
        linkNow = start.atZone(ZoneId.of("Asia/Seoul")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().plusSeconds(120);
        assertThat(links.get(owner, order.orderId()).remainingReissues()).isEqualTo(3);
        reissue(order.orderId(), "next-day");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_link_commands WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(4);
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }

    @Test void thirtyDayBoundaryUsesCommittedDebitAndDoesNotExtendExpiry() {
        var order = active();
        Instant approved = work.get(owner, order.orderId()).approvedAt();
        var before = links.get(owner, order.orderId());
        linkNow = approved.plusSeconds(30L * 86400 - 1);
        var issued = reissue(order.orderId(), "last-second");
        assertThat(issued.expiresAt()).isEqualTo(before.expiresAt());
        linkNow = approved.plusSeconds(30L * 86400);
        assertThat(links.get(owner, order.orderId()).canReissue()).isFalse();
        assertThatThrownBy(() -> reissue(order.orderId(), "after-thirty")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GIFT_LINK_REISSUE_NOT_ALLOWED));
        assertThat(reissue(order.orderId(), "last-second").reused()).isTrue();
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }

    @Test void midnightResetsCountButDoesNotBypassOneMinuteInterval() {
        var order = active();
        linkNow = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().minusSeconds(30);
        reissue(order.orderId(), "before-midnight");
        linkNow = linkNow.plusSeconds(30);
        assertThat(links.get(owner, order.orderId()).remainingReissues()).isEqualTo(3);
        assertThat(links.get(owner, order.orderId()).canReissue()).isFalse();
        assertThatThrownBy(() -> reissue(order.orderId(), "at-midnight")).isInstanceOf(ApiException.class);
        linkNow = linkNow.plusSeconds(30); reissue(order.orderId(), "after-minute");
    }
}
