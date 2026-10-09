package com.sajuppugi.wallet.infrastructure;

import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletPurchaseRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcWalletPurchaseRepository implements WalletPurchaseRepository {
    private final JdbcTemplate jdbc;
    public JdbcWalletPurchaseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<WalletTransaction> findTransaction(UUID id) {
        var allocations = jdbc.query("SELECT lot_id, amount FROM wallet_transaction_lines WHERE transaction_id = ? ORDER BY lot_id",
                (rs, row) -> new WalletTransaction.Allocation(rs.getObject(1, UUID.class), rs.getInt(2)), id);
        return jdbc.query("SELECT * FROM wallet_transactions WHERE id = ?", (rs, row) -> new WalletTransaction(
                id, rs.getObject("wallet_user_id", UUID.class), WalletTransaction.Type.valueOf(rs.getString("type")),
                rs.getInt("total_amount"), rs.getObject("reference_id", UUID.class),
                rs.getObject("reversal_of_id", UUID.class), allocations, rs.getTimestamp("created_at").toInstant()),
                id).stream().findFirst();
    }

    @Override
    public Optional<Receipt> findDebit(UUID userId, UUID quoteId) {
        return findReceipt("wallet_purchase_debits", "quote_id", userId, quoteId);
    }

    @Override
    public Optional<Receipt> findCompensation(UUID userId, UUID originalId) {
        return findReceipt("wallet_purchase_compensations", "original_transaction_id", userId, originalId);
    }

    private Optional<Receipt> findReceipt(String table, String column, UUID userId, UUID target) {
        var rows = jdbc.query("SELECT transaction_id, paid_balance, bonus_balance FROM " + table
                + " WHERE wallet_user_id = ? AND " + column + " = ?", (rs, row) -> new ReceiptRow(
                rs.getObject(1, UUID.class), new WalletBalance(rs.getInt(2), rs.getInt(3))), userId, target);
        return rows.stream().findFirst().map(value -> new Receipt(findTransaction(value.id()).orElseThrow(), value.balance()));
    }

    @Override
    public Optional<Command> findCommand(UUID userId, String keyHash) {
        var rows = jdbc.query("""
                SELECT request_hash, transaction_id, paid_balance, bonus_balance FROM wallet_purchase_commands
                WHERE wallet_user_id = ? AND key_hash = ?
                """, (rs, row) -> new CommandRow(rs.getString(1), rs.getObject(2, UUID.class),
                new WalletBalance(rs.getInt(3), rs.getInt(4))), userId, keyHash);
        return rows.stream().findFirst().map(value -> new Command(value.hash(),
                new Receipt(findTransaction(value.id()).orElseThrow(), value.balance())));
    }

    @Override
    public void saveDebit(UUID quoteId, Receipt receipt) {
        saveReceipt("wallet_purchase_debits", "quote_id", quoteId, receipt);
    }

    @Override
    public void saveCompensation(UUID originalId, Receipt receipt) {
        saveReceipt("wallet_purchase_compensations", "original_transaction_id", originalId, receipt);
    }

    private void saveReceipt(String table, String column, UUID target, Receipt receipt) {
        jdbc.update("INSERT INTO " + table + " (" + column
                + ", wallet_user_id, transaction_id, paid_balance, bonus_balance) VALUES (?, ?, ?, ?, ?)",
                target, receipt.transaction().userId(), receipt.transaction().id(),
                receipt.balance().paidBalance(), receipt.balance().bonusBalance());
    }

    @Override
    public void saveCommand(UUID userId, String keyHash, String requestHash, Receipt receipt, Instant now) {
        jdbc.update("""
                INSERT INTO wallet_purchase_commands(wallet_user_id, key_hash, request_hash, transaction_id,
                paid_balance, bonus_balance, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, keyHash, requestHash, receipt.transaction().id(), receipt.balance().paidBalance(),
                receipt.balance().bonusBalance(), Timestamp.from(now));
    }

    private record ReceiptRow(UUID id, WalletBalance balance) {}
    private record CommandRow(String hash, UUID id, WalletBalance balance) {}
}
