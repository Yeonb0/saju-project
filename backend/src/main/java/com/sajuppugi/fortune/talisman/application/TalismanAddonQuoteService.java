package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.application.QuoteFundingService;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TalismanAddonQuoteService {
    private final ReadingRepository readings; private final CatalogUseCase catalog; private final QuoteFundingService funding;
    public TalismanAddonQuoteService(ReadingRepository readings, CatalogUseCase catalog, QuoteFundingService funding) {
        this.readings=readings; this.catalog=catalog; this.funding=funding;
    }
    public QuoteFundingService.FundingView issue(UUID user, UUID readingId) {
        OwnedReading reading = readings.findOwnedReading(user, readingId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (reading.productOption() != OwnedReading.ProductOption.READING_ONLY || reading.talismanId() != null)
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        Product addon = catalog.availableProducts(Product.Category.TALISMAN_ADDON).stream()
                .filter(value -> value.fortuneType() != null && value.fortuneType().name().equals(reading.fortuneType().name()))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        var quote = catalog.issueQuote(new CatalogUseCase.IssueQuote(user, addon.code(), context(readingId)));
        return funding.get(user, quote.id());
    }
    static String context(UUID readingId) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(("talisman-addon-v1|" + readingId).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
}
