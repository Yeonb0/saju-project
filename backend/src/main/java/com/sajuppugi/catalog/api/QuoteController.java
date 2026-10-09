package com.sajuppugi.catalog.api;

import com.sajuppugi.catalog.application.QuoteFundingService;
import com.sajuppugi.catalog.application.QuoteFundingService.FundingView;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteFundingService funding;

    public QuoteController(QuoteFundingService funding) {
        this.funding = funding;
    }

    @GetMapping("/{quoteId}")
    public ApiResponse<FundedQuoteResponse> get(Authentication authentication, @PathVariable UUID quoteId) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return ApiResponse.of(FundedQuoteResponse.from(funding.get(userId, quoteId)));
    }

    @Schema(requiredProperties = {"quoteId", "productCode", "charged", "expiresAt", "walletBalance",
            "balanceAfter", "shortage", "recommendedTopUp"})
    public record FundedQuoteResponse(UUID quoteId, String productCode, Charged charged, Instant expiresAt,
                                      int walletBalance, @Schema(types = {"integer", "null"}) Integer balanceAfter,
                                      int shortage, @Schema(types = {"string", "null"}) String recommendedTopUp) {
        public static FundedQuoteResponse from(FundingView funding) {
            var quote = funding.quote();
            var price = quote.productSnapshot().price();
            return new FundedQuoteResponse(quote.id(), quote.productSnapshot().code(),
                    new Charged(price.currency().name(), price.amount()), quote.expiresAt(),
                    funding.walletBalance(), funding.balanceAfter(), funding.shortage(), funding.recommendedTopUp());
        }
    }

    public record Charged(String currency, int amount) {}
}
