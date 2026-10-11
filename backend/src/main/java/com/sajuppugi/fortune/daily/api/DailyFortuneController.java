package com.sajuppugi.fortune.daily.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.daily.application.DailyFortuneService;
import com.sajuppugi.fortune.daily.application.DailyFortuneService.DailyFortune;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/daily-fortunes")
public class DailyFortuneController {
    private final DailyFortuneService service;

    public DailyFortuneController(DailyFortuneService service) {
        this.service = service;
    }

    @GetMapping("/today")
    public ApiResponse<DailyFortune> today(Authentication authentication,
                                           @RequestParam UUID personId) {
        return ApiResponse.of(service.today(userId(authentication), personId));
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
