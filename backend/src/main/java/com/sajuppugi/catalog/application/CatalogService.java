package com.sajuppugi.catalog.application;

import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.domain.TopUpProduct;
import com.sajuppugi.catalog.port.CatalogRepository;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService implements CatalogUseCase {
    private final CatalogRepository repository;
    private final Clock clock;

    public CatalogService(CatalogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<Product> availableProducts(Product.Category category) {
        return repository.findAvailableProducts(category, clock.instant());
    }

    @Override
    public List<TopUpProduct> availableTopUps() {
        return repository.findAvailableTopUps(clock.instant());
    }

    @Override
    @Transactional
    public PurchaseQuote issueQuote(IssueQuote command) {
        Objects.requireNonNull(command, "command");
        if (command.requesterUserId() == null || command.productCode() == null || command.productCode().isBlank()
                || command.contextHash() == null || !command.contextHash().matches("[a-f0-9]{64}")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Product product = repository.findProductByCode(command.productCode())
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));
        var now = clock.instant();
        if (!product.isAvailableAt(now)) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        PurchaseQuote quote = PurchaseQuote.issue(UUID.randomUUID(), command.requesterUserId(), product,
                command.contextHash(), now);
        repository.saveQuote(quote);
        return quote;
    }

    @Override
    public PurchaseQuote getQuote(UUID requesterUserId, UUID quoteId) {
        if (requesterUserId == null || quoteId == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        PurchaseQuote quote = repository.findQuoteById(quoteId)
                .filter(value -> value.requesterUserId().equals(requesterUserId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (quote.isExpiredAt(clock.instant())) {
            throw new ApiException(ErrorCode.QUOTE_EXPIRED);
        }
        return quote;
    }

    @Override
    public PurchaseQuote getPayableQuote(UUID requesterUserId, UUID quoteId) {
        PurchaseQuote quote = getQuote(requesterUserId, quoteId);
        Product current = repository.findProductByCode(quote.productSnapshot().code())
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!current.id().equals(quote.productSnapshot().id()) || !current.isAvailableAt(clock.instant())) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        return quote;
    }
}
