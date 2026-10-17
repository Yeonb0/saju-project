package com.sajuppugi.gift.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.application.QuoteFundingService;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GiftQuoteService {
    private final CatalogUseCase catalog;
    private final QuoteFundingService funding;
    public GiftQuoteService(CatalogUseCase catalog, QuoteFundingService funding) {
        this.catalog = catalog; this.funding = funding;
    }

    @Transactional
    public View issue(UUID user, Product.FortuneType fortuneType, Option option) {
        if (user == null || fortuneType != Product.FortuneType.SUNEUNG || option == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        var quote = catalog.issueQuote(new CatalogUseCase.IssueQuote(user, option.productCode(), context(option)));
        return get(user, quote.id());
    }

    public View get(UUID user, UUID quoteId) {
        var quote = catalog.getQuote(user, quoteId);
        var product = quote.productSnapshot();
        Option option = java.util.Arrays.stream(Option.values())
                .filter(value -> value.productCode().equals(product.code())).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (product.category() != Product.Category.GIFT || !quote.contextHash().equals(context(option))) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new View(funding.get(user, quoteId), Product.FortuneType.SUNEUNG, option, option.includedContents());
    }

    private static String context(Option option) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("gift-quote-v1|SUNEUNG|" + option.name() + "|1").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public enum Content { LETTER, TALISMAN, READING, SUNEUNG_CHECKLIST }
    public enum Option {
        TALISMAN_ONLY, READING_WITH_TALISMAN;
        public String productCode() { return "GIFT_SUNEUNG_" + name(); }
        public List<Content> includedContents() {
            return this == TALISMAN_ONLY ? List.of(Content.LETTER, Content.TALISMAN)
                    : List.of(Content.LETTER, Content.TALISMAN, Content.READING, Content.SUNEUNG_CHECKLIST);
        }
    }
    public record View(QuoteFundingService.FundingView funding, Product.FortuneType fortuneType,
                       Option option, List<Content> includedContents) {}
}
