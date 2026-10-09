package com.sajuppugi.catalog.api;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    private final CatalogUseCase catalog;

    public ProductController(CatalogUseCase catalog) { this.catalog = catalog; }

    @GetMapping("/api/v1/products")
    @SecurityRequirements
    @Operation(operationId = "listTopUpProducts",
            description = "Currently supports category=TOP_UP only. Returns available products, not a price reservation.")
    public ApiResponse<ProductsResponse> list(@RequestParam Category category,
            @RequestParam(required = false) String fortuneType) {
        if (fortuneType != null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        var items = catalog.availableTopUps().stream().map(item -> new TopUpResponse(
                item.product().code(), category, item.product().price(), item.paidAmount(),
                item.bonusAmount(), item.creditedAmount(), item.product().active())).toList();
        return ApiResponse.of(new ProductsResponse(items));
    }

    @Schema(name = "TopUpCatalogCategory")
    public enum Category { TOP_UP }

    @Schema(requiredProperties = {"items"})
    public record ProductsResponse(List<TopUpResponse> items) {}

    @Schema(requiredProperties = {"code", "category", "price", "paidAmount", "bonusAmount",
            "creditedAmount", "active"})
    public record TopUpResponse(String code, Category category, Price price, int paidAmount,
            int bonusAmount, int creditedAmount, boolean active) {}
}
