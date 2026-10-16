package com.sajuppugi.share;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class PublicResultShareController {
    private final PublicResultShareService shares;
    public PublicResultShareController(PublicResultShareService shares){this.shares=shares;}
    @PostMapping("/readings/{readingId}/share") public ApiResponse<PublicResultShareService.Created> reading(Authentication a,@PathVariable UUID readingId){return ApiResponse.of(shares.reading(user(a),readingId));}
    @PostMapping("/talismans/{talismanId}/share") public ApiResponse<PublicResultShareService.Created> talisman(Authentication a,@PathVariable UUID talismanId){return ApiResponse.of(shares.talisman(user(a),talismanId));}
    @GetMapping("/public/result-shares/{token}") public ApiResponse<PublicResultShareService.PublicSnapshot> get(@PathVariable String token){return ApiResponse.of(shares.get(token));}
    @DeleteMapping("/result-shares/{shareId}") public void revoke(Authentication a,@PathVariable UUID shareId){shares.revoke(user(a),shareId);}
    private UUID user(Authentication a){try{return UUID.fromString(a.getName());}catch(Exception e){throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);}}
}
