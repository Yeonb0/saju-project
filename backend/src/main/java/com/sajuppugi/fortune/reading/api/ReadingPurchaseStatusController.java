package com.sajuppugi.fortune.reading.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseStatusService;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseStatusService.PurchaseStatus;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reading-purchases")
public class ReadingPurchaseStatusController {
    private final ReadingPurchaseStatusService service;

    public ReadingPurchaseStatusController(ReadingPurchaseStatusService service) {
        this.service = service;
    }

    @GetMapping("/by-quote/{quoteId}")
    public ApiResponse<PurchaseStatus> getByQuote(Authentication authentication, @PathVariable UUID quoteId) {
        return ApiResponse.of(service.getByQuote(userId(authentication), quoteId));
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
