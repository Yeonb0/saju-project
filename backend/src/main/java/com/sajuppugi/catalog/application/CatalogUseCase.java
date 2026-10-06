package com.sajuppugi.catalog.application;

import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.domain.TopUpProduct;
import java.util.List;
import java.util.UUID;

/** Internal commands only; authentication and HTTP DTO mapping are separate. */
public interface CatalogUseCase {
    List<Product> availableProducts(Product.Category category);

    List<TopUpProduct> availableTopUps();

    PurchaseQuote issueQuote(IssueQuote command);

    PurchaseQuote getQuote(UUID requesterUserId, UUID quoteId);

    record IssueQuote(UUID requesterUserId, String productCode, String contextHash) {
    }
}
