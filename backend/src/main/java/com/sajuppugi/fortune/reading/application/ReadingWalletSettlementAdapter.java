package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.wallet.application.WalletPurchaseSettlementPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingWalletSettlementAdapter implements WalletPurchaseSettlementPort {
    private final ReadingRepository repository;
    public ReadingWalletSettlementAdapter(ReadingRepository repository) { this.repository = repository; }
    @Override
    @Transactional(readOnly = true)
    public boolean fulfilled(UUID buyer, UUID quote, UUID transaction) {
        return repository.findPurchaseByQuote(buyer, quote).filter(p -> buyer.equals(p.buyerUserId()) && quote.equals(p.quoteId())
                && transaction.equals(p.walletTransactionId()) && p.status() == ReadingPurchase.Status.FULFILLED).isPresent();
    }
}
