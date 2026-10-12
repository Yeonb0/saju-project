package com.sajuppugi.admin.api;

import com.sajuppugi.admin.application.AdminFinancialQueries;
import com.sajuppugi.admin.application.AdminFinancialQueries.*;
import com.sajuppugi.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
@RequestMapping("/api/v1/admin")
public class AdminFinancialController {
    private final AdminFinancialQueries queries;
    public AdminFinancialController(AdminFinancialQueries queries) { this.queries = queries; }
    @GetMapping("/top-up-orders")
    public ApiResponse<Page<AdminOrder>> orders(Authentication auth, @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(queries.orders(UUID.fromString(auth.getName()), after, limit));
    }
    @GetMapping("/refunds")
    public ApiResponse<Page<AdminRefund>> refunds(Authentication auth, @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(queries.refunds(UUID.fromString(auth.getName()), after, limit));
    }
    @GetMapping("/wallets/{userId}/transactions")
    public ApiResponse<Page<AdminLedger>> ledger(Authentication auth, @PathVariable UUID userId, @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(queries.ledger(UUID.fromString(auth.getName()), userId, after, limit));
    }
    @GetMapping("/wallets/{userId}")
    public ApiResponse<AdminWallet> wallet(Authentication auth, @PathVariable UUID userId) {
        return ApiResponse.of(queries.wallet(UUID.fromString(auth.getName()), userId));
    }
    @GetMapping("/incidents")
    public ApiResponse<Page<AdminIncident>> incidents(Authentication auth, @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(queries.incidents(UUID.fromString(auth.getName()), after, limit));
    }
    @GetMapping("/audit-events")
    public ApiResponse<Page<AdminAuditEvent>> audits(Authentication auth, @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(queries.audits(UUID.fromString(auth.getName()), after, limit));
    }
}
