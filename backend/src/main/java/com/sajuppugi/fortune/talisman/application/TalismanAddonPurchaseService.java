package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.port.CatalogRepository;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.talisman.port.TalismanAddonPurchaseRepository;
import com.sajuppugi.fortune.talisman.port.TalismanAddonPurchaseRepository.Claim;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateFromStoredFacts;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One short, all-or-nothing addon transaction: claim, ledger debit, pending talisman and render intent. */
@Service
public class TalismanAddonPurchaseService {
    private final TalismanAddonPurchaseRepository purchases;
    private final CatalogUseCase catalog;
    private final CatalogRepository catalogRepository;
    private final ObjectProvider<WalletPurchasePort> wallets;
    private final TalismanFulfillmentPort talismans;
    private final Clock clock;

    public TalismanAddonPurchaseService(TalismanAddonPurchaseRepository purchases, CatalogUseCase catalog,
            CatalogRepository catalogRepository, ObjectProvider<WalletPurchasePort> wallets,
            TalismanFulfillmentPort talismans, Clock clock) {
        this.purchases = purchases; this.catalog = catalog; this.catalogRepository = catalogRepository;
        this.wallets = wallets; this.talismans = talismans; this.clock = clock;
    }

    @Transactional(timeout = 10)
    public View purchase(UUID ownerUserId, UUID readingId, UUID quoteId, IdempotencyKey key) {
        if (ownerUserId == null || readingId == null || quoteId == null || key == null || key.value().length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Claim existing = purchases.findByReadingId(readingId).orElse(null);
        if (existing != null) return view(existing, true);

        var reading = purchases.findOwnedEligibleReading(ownerUserId, readingId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        PurchaseQuote quote = catalog.getPayableQuote(ownerUserId, quoteId);
        validate(quote, reading, readingId);
        Product readingOnly = catalog.availableProducts(Product.Category.READING).stream()
                .filter(p -> p.fortuneType() != null && p.fortuneType().name().equals(reading.fortuneType().name())
                        && p.productOption() == Product.ProductOption.READING_ONLY).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        Product bundled = new TalismanAddonPricing().paired(catalog.availableProducts(Product.Category.READING), readingOnly);
        Instant now = clock.instant();
        Claim claimed = purchases.insert(new Claim(UUID.randomUUID(), readingId, ownerUserId, readingOnly.code(),
                bundled.code(), quote.productSnapshot().price().amount(), quoteId, null, "QUOTED", null, now, now));
        if (!claimed.quoteId().equals(quoteId)) return view(claimed, true);
        if (!catalogRepository.bindQuoteToPurchase(quoteId, claimed.id())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        WalletPurchasePort wallet = wallets.getIfAvailable();
        if (wallet == null) throw new ApiException(ErrorCode.READING_FULFILLMENT_UNAVAILABLE);
        var debit = wallet.debit(ownerUserId, quoteId, new IdempotencyKey("talisman-addon:" + claimed.id()));
        if (debit.debitedAmount() != quote.productSnapshot().price().amount()) {
            throw new ApiException(ErrorCode.PURCHASE_DEBIT_MISMATCH);
        }
        purchases.markDebited(claimed.id(), debit.transactionId(), clock.instant());
        var talisman = talismans.createFromStoredFacts(new CreateFromStoredFacts(ownerUserId, readingId,
                reading.fortuneType(), reading.fiveElements(), reading.contentVersion()));
        purchases.markPendingTalisman(claimed.id(), talisman.talismanId(), clock.instant());
        return new View(claimed.id(), readingId, quoteId, debit.transactionId(), talisman.talismanId(),
                talisman.status().name(), quote.productSnapshot().price().amount(), false);
    }

    private void validate(PurchaseQuote quote, TalismanAddonPurchaseRepository.StoredReading reading, UUID readingId) {
        Product product = quote.productSnapshot();
        if (product.category() != Product.Category.TALISMAN_ADDON || product.price().currency() != Price.Currency.TURTLE_SHELL
                || product.fortuneType() == null || !product.fortuneType().name().equals(reading.fortuneType().name())
                || !TalismanAddonQuoteService.context(readingId).equals(quote.contextHash())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }

    private View view(Claim value, boolean reused) {
        return new View(value.id(), value.readingId(), value.quoteId(), value.walletTransactionId(), value.talismanId(),
                value.state().equals("PENDING_TALISMAN") ? "PENDING" : value.state(), value.quotedAmount(), reused);
    }

    public record View(UUID purchaseId, UUID readingId, UUID quoteId, UUID walletTransactionId, UUID talismanId,
                       String status, int chargedShells, boolean reused) {}
}
