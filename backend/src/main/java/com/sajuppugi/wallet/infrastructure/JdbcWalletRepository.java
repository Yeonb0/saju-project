package com.sajuppugi.wallet.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWalletRepository implements WalletRepository {
    private final JdbcTemplate jdbc;

    public JdbcWalletRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<WalletBalance> findBalance(UUID userId) {
        return jdbc.query("SELECT paid_balance, bonus_balance FROM wallets WHERE user_id = ?",
                (rs, row) -> new WalletBalance(rs.getInt(1), rs.getInt(2)), userId).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedWallet lock(UUID userId) {
        var rows = jdbc.query("SELECT * FROM wallets WHERE user_id = ? FOR UPDATE", (rs, row) ->
                new LockedWallet(userId, rs.getLong("version"),
                        new WalletBalance(rs.getInt("paid_balance"), rs.getInt("bonus_balance")), List.of()), userId);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
        var lots = jdbc.query("SELECT * FROM wallet_lots WHERE wallet_user_id = ? ORDER BY id FOR UPDATE",
                (rs, row) -> new WalletLot(rs.getObject("id", UUID.class),
                        WalletLot.BalanceType.valueOf(rs.getString("balance_type")), rs.getInt("granted_amount"),
                        rs.getInt("remaining_amount"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()), userId);
        var wallet = rows.getFirst();
        return new LockedWallet(userId, wallet.version(), wallet.balance(), lots);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(LockedWallet before, List<WalletLot> afterLots,
                       WalletBalance afterBalance, WalletTransaction transaction) {
        if (!transaction.userId().equals(before.userId()) || afterLots.size() != before.lots().size()) {
            throw new IllegalArgumentException("Wallet owner or lot set mismatch");
        }
        var deltas = transaction.allocations().stream().collect(Collectors.toMap(
                WalletTransaction.Allocation::lotId, WalletTransaction.Allocation::amount));
        var ids = afterLots.stream().map(WalletLot::id).collect(Collectors.toSet());
        if (ids.size() != afterLots.size() || !ids.containsAll(deltas.keySet())) {
            throw new IllegalArgumentException("Ledger allocations refer to a different lot set");
        }
        long paid = 0;
        long bonus = 0;
        for (WalletLot lot : afterLots) {
            if (lot.isUsableAt(transaction.createdAt())) {
                if (lot.balanceType() == WalletLot.BalanceType.PAID) paid += lot.remainingAmount();
                else bonus += lot.remainingAmount();
            }
        }
        if (!afterBalance.equals(new WalletBalance(Math.toIntExact(paid), Math.toIntExact(bonus)))) {
            throw new IllegalArgumentException("Wallet projection does not match usable lots");
        }
        for (WalletLot lot : afterLots) {
            WalletLot original = before.lots().stream().filter(value -> value.id().equals(lot.id()))
                    .findFirst().orElseThrow();
            if (original.balanceType() != lot.balanceType() || original.grantedAmount() != lot.grantedAmount()
                    || !original.expiresAt().equals(lot.expiresAt()) || !original.createdAt().equals(lot.createdAt())
                    || lot.remainingAmount() - original.remainingAmount() != deltas.getOrDefault(lot.id(), 0)) {
                throw new IllegalArgumentException("Lot changes do not match the immutable ledger");
            }
            requireOne(jdbc.update("""
                    UPDATE wallet_lots SET remaining_amount = ?
                    WHERE id = ? AND wallet_user_id = ? AND remaining_amount = ?
                    """, lot.remainingAmount(), lot.id(), before.userId(), original.remainingAmount()));
        }
        jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id,
                reversal_of_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, transaction.id(), transaction.userId(), transaction.type().name(), transaction.amount(),
                transaction.referenceId(), transaction.reversalOfId(), Timestamp.from(transaction.createdAt()));
        for (var allocation : transaction.allocations()) {
            jdbc.update("""
                    INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount)
                    VALUES (?, ?, ?, ?)
                    """, transaction.id(), allocation.lotId(), transaction.userId(), allocation.amount());
        }
        requireOne(jdbc.update("""
                UPDATE wallets SET paid_balance = ?, bonus_balance = ?, version = version + 1, updated_at = ?
                WHERE user_id = ? AND version = ?
                """, afterBalance.paidBalance(), afterBalance.bonusBalance(), Timestamp.from(transaction.createdAt()),
                before.userId(), before.version()));
    }

    private void requireOne(int count) {
        if (count != 1) throw new IllegalStateException("Wallet update lost its lock or version");
    }
}
