package com.sajuppugi.catalog.port;

import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.domain.TopUpProduct;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository {
    List<Product> findAvailableProducts(Product.Category category, Instant now);

    /** Includes unavailable products for an explicitly requested public catalog lifecycle view. */
    List<Product> findProducts(Product.Category category);

    List<TopUpProduct> findAvailableTopUps(Instant now);

    Optional<Product> findProductByCode(String code);

    Optional<PurchaseQuote> findQuoteById(UUID quoteId);

    void saveQuote(PurchaseQuote quote);

    /** Atomically connect a quote to one purchase; never implemented by check-then-insert. */
    boolean bindQuoteToPurchase(UUID quoteId, UUID purchaseId);
}
