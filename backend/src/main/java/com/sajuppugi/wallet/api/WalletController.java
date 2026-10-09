package com.sajuppugi.wallet.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.application.WalletQueryUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {
    private final WalletQueryUseCase wallets;

    public WalletController(WalletQueryUseCase wallets) {
        this.wallets = wallets;
    }

    @GetMapping
    public ApiResponse<WalletResponse> get(Authentication authentication) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        var balance = wallets.getBalance(userId);
        return ApiResponse.of(new WalletResponse("TURTLE_SHELL", balance.balance(),
                balance.paidBalance(), balance.bonusBalance()));
    }

    @Schema(requiredProperties = {"currency", "balance", "paidBalance", "bonusBalance"})
    public record WalletResponse(String currency, int balance, int paidBalance, int bonusBalance) {}
}
