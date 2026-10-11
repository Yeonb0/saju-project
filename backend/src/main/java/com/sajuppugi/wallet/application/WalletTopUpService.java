package com.sajuppugi.wallet.application;

import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.infrastructure.JdbcWalletTopUps;
import com.sajuppugi.wallet.port.WalletRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class WalletTopUpService implements WalletTopUpPort {
    private final WalletRepository wallets;
    private final JdbcWalletTopUps topUps;
    private final Clock clock;
    public WalletTopUpService(WalletRepository wallets, JdbcWalletTopUps topUps, Clock clock) {
        this.wallets = wallets; this.topUps = topUps; this.clock = clock;
    }

    @Override
    public Receipt credit(UUID user, UUID order, int paid, int bonus) {
        if (user == null || order == null || paid <= 0 || bonus < 0) throw new IllegalArgumentException("Invalid grant");
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        topUps.ensureWallet(user, now);
        var wallet = wallets.lock(user);
        var existing = topUps.find(user, order);
        if (existing.isPresent()) return existing.get();
        int paidBalance = paid;
        int bonusBalance = bonus;
        for (var lot : wallet.lots()) {
            if (lot.isUsableAt(now)) {
                if (lot.balanceType() == WalletLot.BalanceType.PAID) paidBalance = Math.addExact(paidBalance, lot.remainingAmount());
                else bonusBalance = Math.addExact(bonusBalance, lot.remainingAmount());
            }
        }
        var grants = new ArrayList<WalletLot>();
        var local = now.atZone(ZoneId.of("Asia/Seoul"));
        grants.add(new WalletLot(UUID.randomUUID(), WalletLot.BalanceType.PAID, paid, paid, local.plusYears(5).toInstant(), now));
        if (bonus > 0) grants.add(new WalletLot(UUID.randomUUID(), WalletLot.BalanceType.BONUS, bonus, bonus, local.plusYears(1).toInstant(), now));
        return topUps.save(wallet, order, grants, new WalletBalance(paidBalance, bonusBalance), now);
    }
}
