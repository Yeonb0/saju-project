package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sajuppugi.fortune.reading.application.ReadingWalletSettlementAdapter;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingWalletSettlementAdapterTest {
    @Test void onlyMatchingOwnerQuoteDebitAndFulfilledStatusAreSettled() {
        var repository = mock(ReadingRepository.class); var adapter = new ReadingWalletSettlementAdapter(repository);
        UUID buyer = UUID.randomUUID(), quote = UUID.randomUUID(), debit = UUID.randomUUID();
        for (var state : ReadingPurchase.Status.values()) {
            when(repository.findPurchaseByQuote(buyer, quote)).thenReturn(Optional.of(purchase(buyer, quote, debit, state)));
            assertThat(adapter.fulfilled(buyer, quote, debit)).isEqualTo(state == ReadingPurchase.Status.FULFILLED);
        }
        when(repository.findPurchaseByQuote(buyer, quote)).thenReturn(Optional.of(purchase(UUID.randomUUID(), quote, debit, ReadingPurchase.Status.FULFILLED)));
        assertThat(adapter.fulfilled(buyer, quote, debit)).isFalse();
        when(repository.findPurchaseByQuote(buyer, quote)).thenReturn(Optional.of(purchase(buyer, UUID.randomUUID(), debit, ReadingPurchase.Status.FULFILLED)));
        assertThat(adapter.fulfilled(buyer, quote, debit)).isFalse();
        when(repository.findPurchaseByQuote(buyer, quote)).thenReturn(Optional.of(purchase(buyer, quote, UUID.randomUUID(), ReadingPurchase.Status.FULFILLED)));
        assertThat(adapter.fulfilled(buyer, quote, debit)).isFalse();
        when(repository.findPurchaseByQuote(buyer, quote)).thenReturn(Optional.empty());
        assertThat(adapter.fulfilled(buyer, quote, debit)).isFalse();
    }
    private ReadingPurchase purchase(UUID buyer, UUID quote, UUID debit, ReadingPurchase.Status state) {
        return new ReadingPurchase(UUID.randomUUID(), buyer, UUID.randomUUID(), quote, UUID.randomUUID(), debit, state,
                UUID.randomUUID(), Instant.EPOCH, Instant.EPOCH);
    }
}
