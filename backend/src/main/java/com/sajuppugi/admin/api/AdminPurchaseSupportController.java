package com.sajuppugi.admin.api;

import com.sajuppugi.admin.application.AdminPurchaseSupportQueries;
import com.sajuppugi.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
@RequestMapping("/api/v1/admin/purchase-quotes")
public class AdminPurchaseSupportController {
    private final AdminPurchaseSupportQueries queries;
    public AdminPurchaseSupportController(AdminPurchaseSupportQueries queries) { this.queries = queries; }

    @GetMapping("/{quoteId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminGetPurchaseQuote",
            description = "Exact quote support lookup with DB operator grant and mandatory audit. Durable debit/return facts only; not fulfillment or cash refund state.")
    public ApiResponse<AdminPurchaseSupportQueries.QuoteSupport> quote(Authentication auth, @PathVariable UUID quoteId) {
        return ApiResponse.of(queries.quote(UUID.fromString(auth.getName()), quoteId));
    }
}
