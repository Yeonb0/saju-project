package com.sajuppugi.admin.api;

import com.sajuppugi.admin.application.AdminFinancialQueries;
import com.sajuppugi.admin.application.AdminGiftQueries;
import com.sajuppugi.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/gift-orders")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminGiftController {
    private final AdminGiftQueries queries;
    public AdminGiftController(AdminGiftQueries queries) { this.queries = queries; }
    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminListGiftOrders")
    public ApiResponse<AdminFinancialQueries.Page<AdminGiftQueries.Summary>> list(Authentication auth,
            @RequestParam(required = false) UUID buyerId, @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "false") boolean reviewOnly) {
        return ApiResponse.of(queries.list(UUID.fromString(auth.getName()), buyerId, after, limit, reviewOnly));
    }
    @GetMapping("/{orderId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "adminGetGiftOrder")
    public ApiResponse<AdminGiftQueries.Detail> detail(Authentication auth, @PathVariable UUID orderId) {
        return ApiResponse.of(queries.detail(UUID.fromString(auth.getName()), orderId));
    }
}
