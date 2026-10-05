package com.sajuppugi.catalog.application;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.domain.TopUpProduct;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.application.WalletQueryUseCase;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QuoteFundingService {
    private final CatalogUseCase catalog;
    private final WalletQueryUseCase wallets;

    public QuoteFundingService(CatalogUseCase catalog, WalletQueryUseCase wallets) {
        this.catalog = catalog;
        this.wallets = wallets;
    }

    /** Caller is the authenticated server user; the quote and context are not supplied by the HTTP client. */
    public FundingView get(UUID requesterUserId, UUID quoteId) {
        PurchaseQuote quote = catalog.getQuote(requesterUserId, quoteId);
        Product product = quote.productSnapshot();
        if (product.price().currency() != Price.Currency.TURTLE_SHELL
                || (product.category() != Product.Category.READING
                    && product.category() != Product.Category.TALISMAN_ADDON)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        int balance = wallets.getBalance(requesterUserId).balance();
        int shortage = Math.max(0, product.price().amount() - balance);
        String recommendation = null;
        if (shortage > 0) {
            recommendation = catalog.availableTopUps().stream()
                    .filter(topUp -> topUp.creditedAmount() >= shortage)
                    .min(Comparator.comparingInt((TopUpProduct topUp) -> topUp.product().price().amount())
                            .thenComparing(topUp -> topUp.product().code()))
                    .map(topUp -> topUp.product().code()).orElse(null);
        }
        return new FundingView(quote, balance, shortage, recommendation);
    }

    // Null recommendation means no eligible single product, not that the wallet is sufficient.
    public record FundingView(PurchaseQuote quote, int walletBalance, int shortage, String recommendedTopUp) {}
}
