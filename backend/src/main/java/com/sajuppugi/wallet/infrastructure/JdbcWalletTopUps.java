package com.sajuppugi.wallet.infrastructure;

import com.sajuppugi.wallet.application.WalletTopUpPort.Receipt;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletRepository.LockedWallet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcWalletTopUps {
    private final JdbcTemplate jdbc;
    public JdbcWalletTopUps(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void ensureWallet(UUID user, Instant now) {
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?) ON CONFLICT DO NOTHING",
                user, Timestamp.from(now));
    }

    public Optional<Receipt> find(UUID user, UUID order) {
        return jdbc.query("SELECT * FROM wallet_top_up_receipts WHERE wallet_user_id = ? AND order_id = ?",
                (rs, row) -> new Receipt(rs.getObject("transaction_id", UUID.class),
                        new WalletBalance(rs.getInt("paid_balance"), rs.getInt("bonus_balance"))), user, order)
                .stream().findFirst();
    }

    public Receipt save(LockedWallet wallet, UUID order, List<WalletLot> grants, WalletBalance balance, Instant now) {
        var allocations = grants.stream().map(lot -> new WalletTransaction.Allocation(lot.id(), lot.grantedAmount())).toList();
        int amount = grants.stream().mapToInt(WalletLot::grantedAmount).reduce(0, Math::addExact);
        var transaction = new WalletTransaction(UUID.randomUUID(), wallet.userId(), WalletTransaction.Type.TOP_UP,
                amount, order, null, allocations, now);
        for (var lot : grants) {
            jdbc.update("""
                    INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                    expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, lot.id(), wallet.userId(), lot.balanceType().name(), lot.grantedAmount(), lot.remainingAmount(),
                    Timestamp.from(lot.expiresAt()), Timestamp.from(now));
        }
        jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at)
                VALUES (?, ?, 'TOP_UP', ?, ?, ?)
                """, transaction.id(), wallet.userId(), amount, order, Timestamp.from(now));
        for (var line : allocations) {
            jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, ?)",
                    transaction.id(), line.lotId(), wallet.userId(), line.amount());
        }
        if (jdbc.update("""
                UPDATE wallets SET paid_balance = ?, bonus_balance = ?, version = version + 1, updated_at = ?
                WHERE user_id = ? AND version = ?
                """, balance.paidBalance(), balance.bonusBalance(), Timestamp.from(now), wallet.userId(), wallet.version()) != 1) {
            throw new IllegalStateException("Top-up lost wallet lock");
        }
        jdbc.update("""
                INSERT INTO wallet_top_up_receipts(order_id, wallet_user_id, transaction_id, paid_balance, bonus_balance, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, order, wallet.userId(), transaction.id(), balance.paidBalance(), balance.bonusBalance(), Timestamp.from(now));
        return new Receipt(transaction.id(), balance);
    }
}
