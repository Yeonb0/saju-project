package com.sajuppugi;

import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.domain.WalletTransaction.Allocation;
import com.sajuppugi.wallet.domain.WalletTransaction.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class WalletTransactionTest {
    private static final UUID ID = new UUID(0, 1);
    private static final UUID USER = new UUID(0, 2);
    private static final UUID REFERENCE = new UUID(0, 3);
    private static final UUID LOT = new UUID(0, 4);
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

    @ParameterizedTest
    @CsvSource({"TOP_UP,10", "PURCHASE,-10", "EXPIRY,-10",
            "REFUND,10", "REFUND,-10", "ADJUSTMENT,10", "ADJUSTMENT,-10"})
    void acceptsValidDirections(Type type, int amount) {
        assertThat(transaction(type, amount, List.of(new Allocation(LOT, amount))).amount())
                .isEqualTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,-10", "PURCHASE,10", "EXPIRY,10"})
    void rejectsWrongDirectionEvenWhenAllocationsMatch(Type type, int amount) {
        assertThatThrownBy(() -> transaction(type, amount, List.of(new Allocation(LOT, amount))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ledger amount direction does not match transaction type");
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void rejectsZeroTotalForEveryType(Type type) {
        assertThatThrownBy(() -> transaction(type, 0, List.of(new Allocation(LOT, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,10", "PURCHASE,-10", "EXPIRY,-10",
            "REFUND,10", "REFUND,-10", "ADJUSTMENT,10", "ADJUSTMENT,-10"})
    void rejectsEmptyAllocations(Type type, int amount) {
        assertThatThrownBy(() -> transaction(type, amount, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateLotsEvenWhenTotalMatches() {
        assertThatThrownBy(() -> transaction(Type.PURCHASE, -10,
                List.of(new Allocation(LOT, -4), new Allocation(LOT, -6))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,10,-10", "PURCHASE,-10,10", "EXPIRY,-10,10",
            "REFUND,10,-10", "REFUND,-10,10", "ADJUSTMENT,10,-10", "ADJUSTMENT,-10,10"})
    void rejectsAllocationsInOppositeDirection(Type type, int amount, int allocationAmount) {
        assertThatThrownBy(() -> transaction(type, amount, List.of(new Allocation(LOT, allocationAmount))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMixedDirectionsEvenWhenTotalMatches() {
        assertThatThrownBy(() -> transaction(Type.ADJUSTMENT, 10,
                List.of(new Allocation(LOT, 20), new Allocation(new UUID(0, 5), -10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,10,9", "PURCHASE,-10,-9", "EXPIRY,-10,-9",
            "REFUND,10,9", "REFUND,-10,-9", "ADJUSTMENT,10,9", "ADJUSTMENT,-10,-9"})
    void rejectsMismatchedTotal(Type type, int amount, int allocationAmount) {
        assertThatThrownBy(() -> transaction(type, amount, List.of(new Allocation(LOT, allocationAmount))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ledger allocations do not match transaction amount");
    }

    @Test
    void allocationRejectsZeroAndMissingLot() {
        assertThatThrownBy(() -> new Allocation(LOT, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Allocation(null, 10)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingAllocationListAndEntries() {
        assertThatThrownBy(() -> transaction(Type.TOP_UP, 10, null))
                .isInstanceOf(NullPointerException.class);
        var entries = new ArrayList<Allocation>();
        entries.add(null);
        assertThatThrownBy(() -> transaction(Type.TOP_UP, 10, entries))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingRequiredMetadata() {
        var entries = List.of(new Allocation(LOT, 10));
        assertThatThrownBy(() -> new WalletTransaction(null, USER, Type.TOP_UP, 10, REFERENCE, null, entries, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WalletTransaction(ID, null, Type.TOP_UP, 10, REFERENCE, null, entries, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> transaction(null, 10, entries)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WalletTransaction(ID, USER, Type.TOP_UP, 10, null, null, entries, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WalletTransaction(ID, USER, Type.TOP_UP, 10, REFERENCE, null, entries, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sumsAllocationsWithoutIntegerWraparound() {
        // In int arithmetic this negative sum would wrap to -2 and falsely match.
        assertThatThrownBy(() -> transaction(Type.PURCHASE, -2,
                List.of(new Allocation(LOT, Integer.MIN_VALUE),
                        new Allocation(new UUID(0, 5), Integer.MIN_VALUE),
                        new Allocation(new UUID(0, 6), -2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ledger allocations do not match transaction amount");
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,2147483647", "PURCHASE,-2147483648", "EXPIRY,-2147483648"})
    void validatesIntegerBoundariesWithoutNegatingAmount(Type type, int amount) {
        assertThat(transaction(type, amount, List.of(new Allocation(LOT, amount))).amount()).isEqualTo(amount);
    }

    @Test
    void acceptsMultipleLotsWithMatchingTotal() {
        assertThat(transaction(Type.PURCHASE, -10,
                List.of(new Allocation(LOT, -4), new Allocation(new UUID(0, 5), -6))).amount())
                .isEqualTo(-10);
    }

    @Test
    void cannotMutateStoredAllocationsThroughInputOrAccessor() {
        var entries = new ArrayList<>(List.of(new Allocation(LOT, 10)));
        var transaction = transaction(Type.TOP_UP, 10, entries);
        entries.clear();
        assertThat(transaction.allocations()).containsExactly(new Allocation(LOT, 10));
        assertThatThrownBy(() -> transaction.allocations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsSelfReversalButAllowsReferenceToAnotherTransaction() {
        var entries = List.of(new Allocation(LOT, 10));
        assertThatThrownBy(() -> new WalletTransaction(ID, USER, Type.REFUND, 10, REFERENCE, ID, entries, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ledger transaction cannot reverse itself");
        assertThat(new WalletTransaction(ID, USER, Type.REFUND, 10, REFERENCE, REFERENCE, entries, NOW)
                .reversalOfId()).isEqualTo(REFERENCE);
    }

    private WalletTransaction transaction(Type type, int amount, List<Allocation> allocations) {
        return new WalletTransaction(ID, USER, type, amount, REFERENCE, null, allocations, NOW);
    }
}
