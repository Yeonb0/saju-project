package com.sajuppugi.fortune.talisman.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.port.CatalogRepository;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.talisman.port.TalismanAddonPurchaseRepository;
import com.sajuppugi.fortune.talisman.port.TalismanAddonPurchaseRepository.Claim;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort;
import com.sajuppugi.payment.application.DirectPaymentConsumptionService;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TalismanAddonPurchaseServiceTest {

    @Test
    void doesNotConsumeDirectPaymentWhenExistingAddonBelongsToAnotherOwner() {
        var purchases = mock(TalismanAddonPurchaseRepository.class);
        var directPayments = mock(DirectPaymentConsumptionService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<WalletPurchasePort> wallets = mock(ObjectProvider.class);
        var service = new TalismanAddonPurchaseService(purchases, mock(CatalogUseCase.class),
                mock(CatalogRepository.class), wallets, mock(TalismanFulfillmentPort.class), Clock.systemUTC(),
                directPayments);
        UUID reading = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID existingOwner = UUID.randomUUID();
        Instant now = Instant.now();
        when(purchases.findByReadingId(reading)).thenReturn(Optional.of(new Claim(UUID.randomUUID(), reading,
                existingOwner, "", "", 1000, UUID.randomUUID(), null, "PENDING_TALISMAN", UUID.randomUUID(),
                now, now)));

        assertThatThrownBy(() -> service.purchaseDirect(owner, UUID.randomUUID(), reading, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.errorCode())
                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verifyNoInteractions(directPayments);
    }
}
