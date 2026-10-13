package com.sajuppugi.wallet.application;

import com.sajuppugi.wallet.port.WalletPurchaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
public class WalletPurchaseReceiptService implements WalletPurchaseReceiptPort {
    private final WalletPurchaseRepository repository;
    public WalletPurchaseReceiptService(WalletPurchaseRepository repository) { this.repository = repository; }

    @Override
    public Optional<WalletPurchasePort.DebitResult> findDebit(UUID user, UUID quote) {
        if (user == null || quote == null) return Optional.empty();
        return repository.findDebit(user, quote).map(receipt -> new WalletPurchasePort.DebitResult(
                receipt.transaction().id(), -receipt.transaction().amount(), receipt.balance()));
    }

    @Override
    public Optional<DebitEvidence> findDebitEvidence(UUID user, UUID quote) {
        if (user == null || quote == null) return Optional.empty();
        return repository.findDebit(user, quote).map(receipt -> new DebitEvidence(
                new WalletPurchasePort.DebitResult(receipt.transaction().id(), -receipt.transaction().amount(), receipt.balance()),
                receipt.transaction().createdAt()));
    }

    @Override
    public Optional<WalletPurchasePort.CompensationResult> findCompensation(UUID user, UUID quote, UUID original) {
        if (user == null || quote == null || original == null) return Optional.empty();
        if (repository.findDebit(user, quote).filter(receipt -> receipt.transaction().id().equals(original)).isEmpty()) {
            return Optional.empty();
        }
        return repository.findCompensation(user, original).map(receipt -> new WalletPurchasePort.CompensationResult(
                receipt.transaction().id(), original, receipt.balance()));
    }
}
