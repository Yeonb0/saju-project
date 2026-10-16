package com.sajuppugi.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiError;
import com.sajuppugi.common.api.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** No order/confirmation/fulfillment writes during the PG-disconnected first deployment. */
@Component
@Profile("prelaunch")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PrelaunchPurchaseFilter extends OncePerRequestFilter {
    private static final List<String> BLOCKED_ROOTS = List.of(
            "/api/v1/direct-payment-batches", "/api/v1/top-up-orders",
            "/api/v1/reading-purchases", "/api/v1/gift-orders", "/api/v1/gift-batches",
            "/api/v1/direct-gift-batches", "/api/v1/public/gifts", "/api/v1/webhooks/toss");
    private final ObjectMapper mapper;

    public PrelaunchPurchaseFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return BLOCKED_ROOTS.stream().noneMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), ApiError.of(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE));
    }
}
