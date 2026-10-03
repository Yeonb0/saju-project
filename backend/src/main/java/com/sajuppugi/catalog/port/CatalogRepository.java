package com.sajuppugi.catalog.port;

import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository {
    List<Product> findAvailableProducts(Product.Category category, Instant now);

    Optional<Product> findProductByCode(String code);

    Optional<PurchaseQuote> findQuoteById(UUID quoteId);

    void saveQuote(PurchaseQuote quote);

    /** Atomically connect a quote to one purchase; never implemented by check-then-insert. */
    boolean bindQuoteToPurchase(UUID quoteId, UUID purchaseId);
}
