package com.sajuppugi.wallet.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.port.WalletReadRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WalletQueryService implements WalletQueryUseCase {
    private final WalletReadRepository repository;
    private final Clock clock;

    public WalletQueryService(WalletReadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public WalletBalance getBalance(UUID userId) {
        requireUser(userId);
        return repository.usableBalance(userId, clock.instant());
    }

    @Override
    public TransactionPage getTransactions(UUID userId, String cursor, int limit) {
        requireUser(userId);
        if (limit < 1 || limit > 100) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        UUID afterId = null;
        if (cursor != null) {
            try {
                afterId = UUID.fromString(cursor);
                if (!afterId.toString().equals(cursor)) {
                    throw new IllegalArgumentException("Noncanonical cursor");
                }
            } catch (IllegalArgumentException exception) {
                throw new ApiException(ErrorCode.INVALID_CURSOR);
            }
        }
        return repository.transactions(userId, afterId, limit);
    }

    private static void requireUser(UUID userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
