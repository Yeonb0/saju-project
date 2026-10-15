package com.sajuppugi.fortune.talisman.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.talisman.application.TalismanAddonQuoteService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/readings/{readingId}/talisman-addon-quotes", produces = "application/json")
public class TalismanAddonQuoteController {
    private final TalismanAddonQuoteService quotes;
    public TalismanAddonQuoteController(TalismanAddonQuoteService quotes) { this.quotes=quotes; }
    @PostMapping public ApiResponse<com.sajuppugi.catalog.application.QuoteFundingService.FundingView> issue(Authentication auth, @PathVariable UUID readingId) {
        try { return ApiResponse.of(quotes.issue(UUID.fromString(auth.getName()), readingId)); }
        catch (IllegalArgumentException invalid) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
