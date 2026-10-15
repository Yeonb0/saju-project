package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.ReceivedGiftQueryService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/received-gifts", produces = "application/json")
public class ReceivedGiftController {
    private final ReceivedGiftQueryService gifts;
    public ReceivedGiftController(ReceivedGiftQueryService gifts) { this.gifts = gifts; }
    @GetMapping public ApiResponse<ReceivedGiftQueryService.Page> list(Authentication auth, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) { return ApiResponse.of(gifts.list(user(auth), cursor, size)); }
    private UUID user(Authentication auth) { try { return UUID.fromString(auth.getName()); }
        catch (RuntimeException bad) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); } }
}
