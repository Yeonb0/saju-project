package com.sajuppugi.wallet.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletDebitPolicy;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletPurchaseRepository;
import com.sajuppugi.wallet.port.WalletPurchaseRepository.Receipt;
import com.sajuppugi.wallet.port.WalletRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal port only. Enable after purchase recovery and subject integration are ready. */
@Service
@ConditionalOnProperty(name = "app.wallet.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class WalletPurchaseService implements WalletPurchasePort {
    private final WalletRepository wallets;
    private final WalletPurchaseRepository purchases;
    private final CatalogUseCase catalog;
    private final Clock clock;

    public WalletPurchaseService(WalletRepository wallets, WalletPurchaseRepository purchases,
                                 CatalogUseCase catalog, Clock clock) {
        this.wallets = wallets;
        this.purchases = purchases;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public DebitResult debit(UUID userId, UUID quoteId, IdempotencyKey key) {
        validate(userId, quoteId, key);
        var before = wallets.lock(userId);
        String hash = hash("DEBIT:" + quoteId);
        Receipt receipt = replay(userId, key, hash);
        if (receipt == null) {
            receipt = purchases.findDebit(userId, quoteId).orElse(null);
            if (receipt == null) {
                var quote = catalog.getPayableQuote(userId, quoteId);
                var price = quote.productSnapshot().price();
                if (price.currency() != Price.Currency.TURTLE_SHELL || price.amount() <= 0) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST);
                }
                Instant now = clock.instant();
                if (balance(before.lots(), now).balance() < price.amount()) {
                    throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
                }
                var allocations = WalletDebitPolicy.allocateDebit(before.lots(), price.amount(), now);
                var after = changed(before.lots(), allocations);
                var transaction = new WalletTransaction(UUID.randomUUID(), userId, WalletTransaction.Type.PURCHASE,
                        -price.amount(), quoteId, null, allocations, now);
                receipt = new Receipt(transaction, balance(after, now));
                wallets.record(before, after, receipt.balance(), transaction);
                purchases.saveDebit(quoteId, receipt);
            }
            purchases.saveCommand(userId, hash(key.value()), hash, receipt, clock.instant());
        }
        return new DebitResult(receipt.transaction().id(), -receipt.transaction().amount(), receipt.balance());
    }

    @Override
    public CompensationResult compensate(UUID originalId, String reasonCode, IdempotencyKey key) {
        if (originalId == null || key == null || reasonCode == null || !reasonCode.matches("[A-Z_]{1,80}")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        var original = purchases.findTransaction(originalId)
                .filter(value -> value.type() == WalletTransaction.Type.PURCHASE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        UUID userId = original.userId();
        validate(userId, originalId, key);
        var before = wallets.lock(userId);
        String hash = hash("COMPENSATE:" + originalId + ":" + reasonCode);
        Receipt receipt = replay(userId, key, hash);
        if (receipt == null) {
            receipt = purchases.findCompensation(userId, originalId).orElse(null);
            if (receipt == null) {
                // Restore the original lots, including their original expiry; never mint new credits.
                var allocations = original.allocations().stream()
                        .map(value -> new WalletTransaction.Allocation(value.lotId(), -value.amount())).toList();
                var after = changed(before.lots(), allocations);
                Instant now = clock.instant();
                var transaction = new WalletTransaction(UUID.randomUUID(), userId, WalletTransaction.Type.REFUND,
                        -original.amount(), original.referenceId(), originalId, allocations, now);
                receipt = new Receipt(transaction, balance(after, now));
                wallets.record(before, after, receipt.balance(), transaction);
                purchases.saveCompensation(originalId, receipt);
            }
            purchases.saveCommand(userId, hash(key.value()), hash, receipt, clock.instant());
        }
        return new CompensationResult(receipt.transaction().id(), originalId, receipt.balance());
    }

    private Receipt replay(UUID userId, IdempotencyKey key, String requestHash) {
        var command = purchases.findCommand(userId, hash(key.value())).orElse(null);
        if (command == null) return null;
        if (!command.requestHash().equals(requestHash)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        return command.receipt();
    }

    private void validate(UUID userId, UUID targetId, IdempotencyKey key) {
        if (userId == null || targetId == null || key == null || key.value().length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }

    private List<WalletLot> changed(List<WalletLot> lots, List<WalletTransaction.Allocation> allocations) {
        Map<UUID, Integer> deltas = allocations.stream()
                .collect(Collectors.toMap(WalletTransaction.Allocation::lotId, WalletTransaction.Allocation::amount));
        if (!lots.stream().map(WalletLot::id).collect(Collectors.toSet()).containsAll(deltas.keySet())) {
            throw new IllegalStateException("Original ledger lot is missing");
        }
        return lots.stream().map(lot -> new WalletLot(lot.id(), lot.balanceType(), lot.grantedAmount(),
                Math.addExact(lot.remainingAmount(), deltas.getOrDefault(lot.id(), 0)),
                lot.expiresAt(), lot.createdAt())).toList();
    }

    private WalletBalance balance(List<WalletLot> lots, Instant now) {
        int paid = 0;
        int bonus = 0;
        for (WalletLot lot : lots) {
            if (lot.isUsableAt(now)) {
                if (lot.balanceType() == WalletLot.BalanceType.PAID) paid = Math.addExact(paid, lot.remainingAmount());
                else bonus = Math.addExact(bonus, lot.remainingAmount());
            }
        }
        return new WalletBalance(paid, bonus);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
