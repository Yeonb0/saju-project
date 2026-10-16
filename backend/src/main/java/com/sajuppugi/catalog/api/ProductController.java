package com.sajuppugi.catalog.api;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    private final CatalogUseCase catalog;
    private final Clock clock;

    public ProductController(CatalogUseCase catalog, Clock clock) { this.catalog = catalog; this.clock = clock; }

    @GetMapping("/api/v1/products")
    @SecurityRequirements
    @Operation(operationId = "listProducts",
            description = "Supports TOP_UP and FORTUNE. FORTUNE can explicitly include unavailable products with their display lifecycle; only AVAILABLE is purchasable. Not a price reservation.")
    public ApiResponse<ProductsResponse> list(@RequestParam Category category,
            @RequestParam(required = false) Product.FortuneType fortuneType,
            @RequestParam(defaultValue = "false") boolean includeUnavailable) {
        if (category == Category.TOP_UP && fortuneType != null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if (category == Category.TOP_UP && includeUnavailable) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.instant();
        var items = category == Category.TOP_UP
                ? catalog.availableTopUps().stream().map(item -> new ProductResponse(
                    item.product().code(), category, item.product().price(), item.paidAmount(),
                    item.bonusAmount(), item.creditedAmount(), item.product().active(),
                    item.product().productName(), item.product().regularPrice(), null, null,
                    item.product().saleEndsAt(), item.product().saleStatusAt(now))).toList()
                : (includeUnavailable ? catalog.products(Product.Category.READING)
                        : catalog.availableProducts(Product.Category.READING)).stream()
                    .filter(item -> item.fortuneType() != null && item.productOption() != null)
                    .filter(item -> fortuneType == null || item.fortuneType() == fortuneType)
                    .map(item -> new ProductResponse(item.code(), category, item.price(), null, null, null,
                        item.active(), item.productName(), item.regularPrice(), item.fortuneType(),
                        item.productOption(), item.saleEndsAt(), item.saleStatusAt(now))).toList();
        return ApiResponse.of(new ProductsResponse(items));
    }

    @Schema(name = "TopUpCatalogCategory")
    public enum Category { TOP_UP, FORTUNE }

    @Schema(requiredProperties = {"items"})
    public record ProductsResponse(List<ProductResponse> items) {}

    @Schema(requiredProperties = {"code", "category", "price", "paidAmount", "bonusAmount",
            "creditedAmount", "active", "productName", "regularPrice", "fortuneType", "option", "saleEndsAt", "saleStatus"})
    public record ProductResponse(String code, Category category, Price price,
            @Schema(types = {"integer", "null"}) Integer paidAmount,
            @Schema(types = {"integer", "null"}) Integer bonusAmount,
            @Schema(types = {"integer", "null"}) Integer creditedAmount, boolean active, String productName,
            @Schema(nullable = true) Price regularPrice,
            @Schema(nullable = true) Product.FortuneType fortuneType,
            @Schema(nullable = true) Product.ProductOption option,
            @Schema(nullable = true) Instant saleEndsAt, Product.SaleStatus saleStatus) {}
}
