package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Server-side delta only; the client never supplies an addon amount. */
@Component
public class TalismanAddonPricing {
    public Price difference(Product readingOnly, Product withTalisman) {
        if (readingOnly == null || withTalisman == null || readingOnly.category() != Product.Category.READING
                || withTalisman.category() != Product.Category.READING
                || readingOnly.productOption() != Product.ProductOption.READING_ONLY
                || withTalisman.productOption() != Product.ProductOption.READING_WITH_TALISMAN
                || readingOnly.fortuneType() != withTalisman.fortuneType()
                || readingOnly.price().currency() != Price.Currency.TURTLE_SHELL
                || withTalisman.price().currency() != Price.Currency.TURTLE_SHELL) throw new ApiException(ErrorCode.INVALID_REQUEST);
        int amount = Math.subtractExact(withTalisman.price().amount(), readingOnly.price().amount());
        if (amount <= 0) throw new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        return new Price(Price.Currency.TURTLE_SHELL, amount);
    }
    public Product paired(List<Product> products, Product readingOnly) {
        return products.stream().filter(product -> product.category() == Product.Category.READING
                && product.fortuneType() == readingOnly.fortuneType()
                && product.productOption() == Product.ProductOption.READING_WITH_TALISMAN).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE));
    }
}
