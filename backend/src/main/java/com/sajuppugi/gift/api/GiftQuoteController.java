package com.sajuppugi.gift.api;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gift-quotes")
public class GiftQuoteController {
    private final GiftQuoteService gifts;
    public GiftQuoteController(GiftQuoteService gifts) { this.gifts = gifts; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGiftQuote", description = "One CSAT recipient. Quote only; no debit, token or delivery.")
    public ApiResponse<GiftQuoteResponse> create(Authentication authentication, @Valid @RequestBody Request request) {
        return ApiResponse.of(response(gifts.issue(user(authentication), request.fortuneType(), request.option())));
    }

    @GetMapping("/{quoteId}")
    @Operation(operationId = "getGiftQuote")
    public ApiResponse<GiftQuoteResponse> get(Authentication authentication, @PathVariable UUID quoteId) {
        return ApiResponse.of(response(gifts.get(user(authentication), quoteId)));
    }

    private UUID user(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException exception) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
    private GiftQuoteResponse response(GiftQuoteService.View view) {
        var funding = view.funding();
        var quote = funding.quote();
        var product = quote.productSnapshot();
        return new GiftQuoteResponse(quote.id(), product.code(), product.productName(), product.price(),
                quote.expiresAt(), funding.walletBalance(), funding.balanceAfter(), funding.shortage(),
                funding.recommendedTopUp(), view.fortuneType(), view.option(), 1, view.includedContents());
    }
    public record Request(@NotNull Product.FortuneType fortuneType, @NotNull GiftQuoteService.Option option) {}
    @Schema(requiredProperties = {"quoteId", "productCode", "productName", "charged", "expiresAt",
            "walletBalance", "balanceAfter", "shortage", "recommendedTopUp", "fortuneType", "option",
            "recipientCount", "includedContents"})
    public record GiftQuoteResponse(UUID quoteId, String productCode, String productName, Price charged,
            Instant expiresAt, int walletBalance, @Schema(types = {"integer", "null"}) Integer balanceAfter,
            int shortage, @Schema(types = {"string", "null"}) String recommendedTopUp, Product.FortuneType fortuneType,
            GiftQuoteService.Option option, int recipientCount, List<GiftQuoteService.Content> includedContents) {}
}
