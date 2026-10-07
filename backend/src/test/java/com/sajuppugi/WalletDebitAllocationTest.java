package com.sajuppugi;

import com.sajuppugi.wallet.domain.WalletDebitPolicy;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletLot.BalanceType;
import com.sajuppugi.wallet.domain.WalletTransaction.Allocation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class WalletDebitAllocationTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    void usesExpiryThenBonusThenGrantTimeThenIdAndStopsAtRequestedAmount() {
        var expiryFirst = lot(1, BalanceType.PAID, 3, NOW.plusSeconds(100), NOW.minusSeconds(10));
        var bonusFirst = lot(2, BalanceType.BONUS, 4, NOW.plusSeconds(200), NOW.minusSeconds(10));
        var bonusSecond = lot(3, BalanceType.BONUS, 4, NOW.plusSeconds(200), NOW.minusSeconds(10));
        var bonusLater = lot(4, BalanceType.BONUS, 4, NOW.plusSeconds(200), NOW.minusSeconds(5));
        var paid = lot(5, BalanceType.PAID, 20, NOW.plusSeconds(200), NOW.minusSeconds(20));
        assertThat(WalletDebitPolicy.allocateDebit(List.of(paid, bonusLater, bonusSecond, expiryFirst, bonusFirst), 13, NOW))
                .containsExactly(new Allocation(expiryFirst.id(), -3), new Allocation(bonusFirst.id(), -4),
                        new Allocation(bonusSecond.id(), -4), new Allocation(bonusLater.id(), -2));
    }

    @Test
    void acceptsExactBalanceAndDoesNotCreateZeroAllocations() {
        var first = lot(1, BalanceType.PAID, 10, NOW.plusSeconds(100), NOW);
        var second = lot(2, BalanceType.PAID, 10, NOW.plusSeconds(200), NOW);
        assertThat(WalletDebitPolicy.allocateDebit(List.of(first, second), 10, NOW))
                .containsExactly(new Allocation(first.id(), -10));
        assertThat(WalletDebitPolicy.allocateDebit(List.of(first, second), 20, NOW))
                .containsExactly(new Allocation(first.id(), -10), new Allocation(second.id(), -10));
    }

    @Test
    void excludesExpiredEmptyAndFutureLots() {
        var expired = lot(1, BalanceType.PAID, 100, NOW, NOW.minusSeconds(10));
        var empty = new WalletLot(new UUID(0, 2), BalanceType.PAID, 100, 0, NOW.plusSeconds(100), NOW);
        var future = lot(3, BalanceType.PAID, 100, NOW.plusSeconds(100), NOW.plusSeconds(1));
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(List.of(expired, empty, future), 1, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Insufficient usable wallet balance");
    }

    @Test
    void shortageDoesNotReturnPartialAllocationOrChangeInput() {
        var input = new ArrayList<>(List.of(lot(1, BalanceType.PAID, 5, NOW.plusSeconds(100), NOW)));
        var before = List.copyOf(input);
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(input, 6, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Insufficient usable wallet balance");
        assertThat(input).containsExactlyElementsOf(before);
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(List.of(), 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonpositiveRequests(int amount) {
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(List.of(), amount, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Debit amount must be positive");
    }

    @Test
    void rejectsDuplicateIdsInsteadOfSpendingOneLotTwice() {
        var lot = lot(1, BalanceType.PAID, 5, NOW.plusSeconds(100), NOW);
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(List.of(lot, lot), 8, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate wallet lot");
    }

    @Test
    void leavesInputUntouchedAndReturnsImmutableAllocations() {
        var input = new ArrayList<>(List.of(lot(2, BalanceType.PAID, 5, NOW.plusSeconds(200), NOW),
                lot(1, BalanceType.PAID, 5, NOW.plusSeconds(100), NOW)));
        var before = List.copyOf(input);
        var result = WalletDebitPolicy.allocateDebit(input, 7, NOW);
        assertThat(input).containsExactlyElementsOf(before);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void handlesLargeBalancesWithoutSummingIntoAnInt() {
        var first = lot(1, BalanceType.PAID, Integer.MAX_VALUE - 1, NOW.plusSeconds(100), NOW);
        var second = lot(2, BalanceType.PAID, Integer.MAX_VALUE, NOW.plusSeconds(200), NOW);
        assertThat(WalletDebitPolicy.allocateDebit(List.of(first, second), Integer.MAX_VALUE, NOW))
                .containsExactly(new Allocation(first.id(), -(Integer.MAX_VALUE - 1)), new Allocation(second.id(), -1));
    }

    @Test
    void rejectsMissingInputs() {
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(null, 1, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(List.of(), 1, null)).isInstanceOf(NullPointerException.class);
        var entries = new ArrayList<WalletLot>();
        entries.add(null);
        assertThatThrownBy(() -> WalletDebitPolicy.allocateDebit(entries, 1, NOW)).isInstanceOf(NullPointerException.class);
    }

    private WalletLot lot(long id, BalanceType type, int balance, Instant expires, Instant created) {
        return new WalletLot(new UUID(0, id), type, balance, balance, expires, created);
    }
}
