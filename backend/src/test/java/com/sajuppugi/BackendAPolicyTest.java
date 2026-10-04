package com.sajuppugi;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.common.outbox.OutboxPort;
import com.sajuppugi.gift.domain.GiftDeliveryPolicy;
import com.sajuppugi.gift.domain.GiftLifetimePolicy;
import com.sajuppugi.payment.application.TopUpUseCase;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletDebitPolicy;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BackendAPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private static final UUID USER = new UUID(0, 1);

    @Test
    void productSaleIncludesStartButExcludesEnd() {
        Product product = product(NOW, NOW.plusSeconds(60), true);
        assertThat(product.isAvailableAt(NOW)).isTrue();
        assertThat(product.isAvailableAt(NOW.minusNanos(1))).isFalse();
        assertThat(product.isAvailableAt(NOW.plusSeconds(60))).isFalse();
        assertThat(product(NOW, NOW.plusSeconds(60), false).isAvailableAt(NOW)).isFalse();
    }

    @Test
    void quoteKeepsSnapshotAndExpiresExactlyAtThirtyMinutes() {
        Product original = product(null, null, true);
        PurchaseQuote quote = PurchaseQuote.issue(UUID.randomUUID(), USER, original, "a".repeat(64), NOW);
        assertThat(quote.productSnapshot()).isEqualTo(original);
        assertThat(quote.isExpiredAt(NOW.plusSeconds(1799))).isFalse();
        assertThat(quote.isExpiredAt(NOW.plusSeconds(1800))).isTrue();
        assertThatThrownBy(() -> PurchaseQuote.issue(UUID.randomUUID(), USER,
                product(null, null, false), "a".repeat(64), NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pricesAndBalancesRejectInvalidAmounts() {
        assertThatThrownBy(() -> new Price(Price.Currency.TURTLE_SHELL, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalletBalance(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalletBalance(Integer.MAX_VALUE, 1))
                .isInstanceOf(ArithmeticException.class);
        assertThat(new WalletBalance(30, 5).balance()).isEqualTo(35);
    }

    @Test
    void debitOrderUsesExpiryThenBonusThenGrantTimeThenId() {
        Instant expiry = NOW.plusSeconds(500);
        WalletLot paid = lot(1, WalletLot.BalanceType.PAID, expiry, NOW.minusSeconds(10), 10);
        WalletLot bonusLater = lot(4, WalletLot.BalanceType.BONUS, expiry, NOW.minusSeconds(5), 10);
        WalletLot bonusFirstId = lot(2, WalletLot.BalanceType.BONUS, expiry, NOW.minusSeconds(10), 10);
        WalletLot bonusSecondId = lot(3, WalletLot.BalanceType.BONUS, expiry, NOW.minusSeconds(10), 10);
        WalletLot earlierPaid = lot(5, WalletLot.BalanceType.PAID, NOW.plusSeconds(100), NOW, 10);
        var input = new ArrayList<>(List.of(paid, bonusLater, bonusSecondId, bonusFirstId, earlierPaid));
        var before = List.copyOf(input);
        assertThat(WalletDebitPolicy.orderedUsableLots(input, NOW))
                .containsExactly(earlierPaid, bonusFirstId, bonusSecondId, bonusLater, paid);
        assertThat(input).containsExactlyElementsOf(before);
    }

    @Test
    void debitExcludesExpiredEmptyAndFutureLots() {
        WalletLot expired = lot(1, WalletLot.BalanceType.PAID, NOW, NOW.minusSeconds(10), 10);
        WalletLot empty = lot(2, WalletLot.BalanceType.PAID, NOW.plusSeconds(100), NOW, 0);
        WalletLot future = lot(3, WalletLot.BalanceType.PAID, NOW.plusSeconds(100), NOW.plusSeconds(1), 10);
        assertThat(WalletDebitPolicy.orderedUsableLots(List.of(expired, empty, future), NOW)).isEmpty();
    }

    @Test
    void ledgerRequiresUniqueLotsMatchingTotalAndSign() {
        var allocation = new WalletTransaction.Allocation(new UUID(0, 2), -10);
        assertThat(ledger(-10, List.of(allocation)).amount()).isEqualTo(-10);
        assertThatThrownBy(() -> ledger(-20, List.of(allocation, allocation)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger(-11, List.of(allocation))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger(10, List.of(allocation))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void giftGraceRequiresSubmissionBeforeExpiry() {
        Instant expiry = GiftLifetimePolicy.unusedExpiresAt(NOW);
        Instant validSubmission = expiry.minusSeconds(1);
        assertThat(GiftLifetimePolicy.canStart(NOW, validSubmission, expiry.plusSeconds(1))).isTrue();
        assertThat(GiftLifetimePolicy.canStart(NOW, validSubmission, expiry.plus(Duration.ofHours(24)))).isFalse();
        assertThat(GiftLifetimePolicy.canStart(NOW, expiry, expiry)).isFalse();
        assertThat(GiftLifetimePolicy.canStart(NOW, null, NOW.plusSeconds(1))).isFalse();
        assertThat(GiftLifetimePolicy.canStart(NOW, NOW.plusSeconds(10), NOW)).isFalse();
    }

    @Test
    void giftUsedPeriodStartsFromSuccessfulProvisionNotInputOrTokenRotation() {
        Instant firstProvided = NOW.plus(Duration.ofDays(89));
        assertThat(GiftLifetimePolicy.usedExpiresAt(firstProvided))
                .isEqualTo(firstProvided.plus(Duration.ofDays(90)));
    }

    @Test
    void automaticRetryDelaysAreRelativeToLastFailureAndStopAfterFourAttempts() {
        assertThat(GiftDeliveryPolicy.nextAutomaticAttempt(1, NOW)).contains(NOW.plusSeconds(60));
        assertThat(GiftDeliveryPolicy.nextAutomaticAttempt(2, NOW)).contains(NOW.plusSeconds(300));
        assertThat(GiftDeliveryPolicy.nextAutomaticAttempt(3, NOW)).contains(NOW.plusSeconds(1800));
        assertThat(GiftDeliveryPolicy.nextAutomaticAttempt(4, NOW)).isEmpty();
        assertThatThrownBy(() -> GiftDeliveryPolicy.nextAutomaticAttempt(0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualRequestsRespectMinuteIntervalAndPendingJob() {
        var waiting = GiftDeliveryPolicy.manualAvailability(1, NOW, false, NOW.plusSeconds(59));
        assertThat(waiting.canResend()).isFalse();
        assertThat(waiting.remainingResends()).isEqualTo(2);
        assertThat(waiting.nextResendAt()).isEqualTo(NOW.plusSeconds(60));
        var ready = GiftDeliveryPolicy.manualAvailability(1, NOW, false, NOW.plusSeconds(60));
        assertThat(ready.canResend()).isTrue();
        assertThat(ready.nextResendAt()).isNull();
        assertThat(GiftDeliveryPolicy.manualAvailability(1, NOW, true, NOW.plusSeconds(60)).canResend()).isFalse();
    }

    @Test
    void dailyManualLimitResetsAtKoreanMidnightWithoutSkippingMinuteInterval() {
        Instant beforeMidnight = Instant.parse("2026-10-03T14:59:30Z");
        var blocked = GiftDeliveryPolicy.manualAvailability(3, beforeMidnight, false, beforeMidnight);
        assertThat(blocked.remainingResends()).isZero();
        assertThat(blocked.nextResendAt()).isEqualTo(Instant.parse("2026-10-03T15:00:30Z"));
        var newDay = GiftDeliveryPolicy.manualAvailability(0, beforeMidnight, false,
                Instant.parse("2026-10-03T15:00:00Z"));
        assertThat(newDay.canResend()).isFalse();
        assertThat(newDay.remainingResends()).isEqualTo(3);
        assertThat(GiftDeliveryPolicy.manualAvailability(0, beforeMidnight, false,
                Instant.parse("2026-10-03T15:00:30Z")).canResend()).isTrue();
    }

    @Test
    void sensitiveContractValuesAreNotIncludedInToString() {
        assertThat(new TopUpUseCase.Confirmation("payment-secret", 1000).toString())
                .doesNotContain("payment-secret");
        assertThat(new IdempotencyKey("key-secret").toString()).doesNotContain("key-secret");
        assertThat(new OutboxPort.Message(UUID.randomUUID(), USER, "GIFT_DELIVERY", "payload-secret", NOW)
                .toString()).doesNotContain("payload-secret");
    }

    @Test
    void topUpSnapshotRejectsInvalidGrants() {
        assertThatThrownBy(() -> new TopUpOrder(UUID.randomUUID(), USER, "TEST_PRODUCT", 1000,
                0, 5, TopUpOrder.Status.CREATED, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private Product product(Instant starts, Instant ends, boolean active) {
        return new Product(UUID.randomUUID(), "TEST_PRODUCT", Product.Category.READING,
                new Price(Price.Currency.TURTLE_SHELL, 30), active, starts, ends, "test-v1");
    }

    private WalletLot lot(long id, WalletLot.BalanceType type, Instant expires, Instant created, int remaining) {
        return new WalletLot(new UUID(0, id), type, 10, remaining, expires, created);
    }

    private WalletTransaction ledger(int amount, List<WalletTransaction.Allocation> allocations) {
        return new WalletTransaction(UUID.randomUUID(), USER, WalletTransaction.Type.PURCHASE,
                amount, UUID.randomUUID(), null, allocations, NOW);
    }
}
