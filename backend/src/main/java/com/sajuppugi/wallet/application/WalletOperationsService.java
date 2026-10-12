package com.sajuppugi.wallet.application;

import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletOperationsService {
    private final WalletRepository wallets;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public WalletOperationsService(WalletRepository wallets, JdbcTemplate jdbc, Clock clock) {
        this.wallets = wallets; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional(timeout = 10)
    public boolean inspectAndExpire(UUID user) {
        var before = wallets.lock(user);
        Instant now = clock.instant();
        if (!inspect(before, now)) return false;
        var expired = before.lots().stream().filter(l -> l.remainingAmount() > 0 && !l.expiresAt().isAfter(now)).toList();
        if (expired.isEmpty()) return true;
        var ids = expired.stream().map(WalletLot::id).toList();
        var after = before.lots().stream().map(l -> ids.contains(l.id()) ? zero(l) : l).toList();
        var lines = expired.stream().map(l -> new WalletTransaction.Allocation(l.id(), -l.remainingAmount())).toList();
        wallets.record(before, after, balance(after, now), new WalletTransaction(UUID.randomUUID(), user,
                WalletTransaction.Type.EXPIRY, -expired.stream().mapToInt(WalletLot::remainingAmount).sum(),
                UUID.randomUUID(), null, lines, now));
        return true;
    }

    // The caller holds the order lock. The wallet lock serializes purchases, expiry and refund reservations.
    @Transactional(timeout = 10)
    public boolean reclaimUnusedTopUp(UUID user, UUID order, UUID refund, int paid, int bonus) {
        var before = wallets.lock(user);
        Instant now = clock.instant();
        if (!inspect(before, now)) return false;
        var granted = jdbc.query("""
                SELECT l.lot_id FROM wallet_transaction_lines l JOIN wallet_top_up_receipts r
                ON r.transaction_id = l.transaction_id WHERE r.order_id = ? AND r.wallet_user_id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), order, user);
        var lots = before.lots().stream().filter(l -> granted.contains(l.id())).toList();
        if (lots.isEmpty() || lots.size() != granted.size()
                || lots.stream().anyMatch(l -> l.remainingAmount() != l.grantedAmount() || !l.isUsableAt(now))
                || lots.stream().filter(l -> l.balanceType() == WalletLot.BalanceType.PAID).mapToInt(WalletLot::grantedAmount).sum() != paid
                || lots.stream().filter(l -> l.balanceType() == WalletLot.BalanceType.BONUS).mapToInt(WalletLot::grantedAmount).sum() != bonus) return false;
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(*) FROM wallet_transaction_lines l JOIN wallet_transaction_lines g ON g.lot_id = l.lot_id
                JOIN wallet_top_up_receipts r ON r.transaction_id = g.transaction_id
                WHERE r.order_id = ? AND l.amount < 0
                """, Integer.class, order);
        if (used == null || used != 0) return false;
        var after = before.lots().stream().map(l -> granted.contains(l.id()) ? zero(l) : l).toList();
        var lines = lots.stream().map(l -> new WalletTransaction.Allocation(l.id(), -l.remainingAmount())).toList();
        wallets.record(before, after, balance(after, now), new WalletTransaction(UUID.randomUUID(), user,
                WalletTransaction.Type.REFUND, -Math.addExact(paid, bonus), refund, null, lines, now));
        return true;
    }

    public List<UUID> batchAfter(UUID cursor, int size) {
        return cursor == null
                ? jdbc.query("SELECT user_id FROM wallets ORDER BY user_id LIMIT ?", (rs, row) -> rs.getObject(1, UUID.class), size)
                : jdbc.query("SELECT user_id FROM wallets WHERE user_id > ? ORDER BY user_id LIMIT ?", (rs, row) -> rs.getObject(1, UUID.class), cursor, size);
    }
    public void assertUsable(UUID user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND kind = 'EXTERNAL_CANCEL_REVIEW' AND resolved_at IS NULL", Integer.class, user);
        if (count != null && count > 0) throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.WALLET_REVIEW_REQUIRED);
    }

    @Transactional(timeout = 5)
    public void flagExternalCancellation(UUID user) {
        wallets.lock(user);
        if (jdbc.update("UPDATE wallet_integrity_findings SET resolved_at = NULL WHERE wallet_user_id = ? AND kind = 'EXTERNAL_CANCEL_REVIEW'", user) == 0)
            jdbc.update("INSERT INTO wallet_integrity_findings(wallet_user_id, kind, detected_at) VALUES (?, 'EXTERNAL_CANCEL_REVIEW', ?)", user, Timestamp.from(clock.instant()));
    }

    private boolean inspect(WalletRepository.LockedWallet wallet, Instant now) {
        var problems = new ArrayList<String>();
        var expected = balance(wallet.lots(), now);
        // Existing projections may retain expired lots until a debit/expiry operation refreshes them.
        int paidAll = wallet.lots().stream().filter(l -> l.balanceType() == WalletLot.BalanceType.PAID).mapToInt(WalletLot::remainingAmount).sum();
        int bonusAll = wallet.lots().stream().filter(l -> l.balanceType() == WalletLot.BalanceType.BONUS).mapToInt(WalletLot::remainingAmount).sum();
        if ((wallet.balance().paidBalance() != expected.paidBalance() && wallet.balance().paidBalance() != paidAll)
                || (wallet.balance().bonusBalance() != expected.bonusBalance() && wallet.balance().bonusBalance() != bonusAll)) problems.add("BALANCE_MISMATCH");
        for (var lot : wallet.lots()) {
            Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction_lines WHERE lot_id = ? AND wallet_user_id = ?", Long.class, lot.id(), wallet.userId());
            if (sum == null || sum != lot.remainingAmount()) { problems.add("LOT_LEDGER_MISMATCH"); break; }
        }
        Integer invalid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM wallet_transactions t WHERE t.wallet_user_id = ? AND
                t.total_amount <> (SELECT COALESCE(SUM(l.amount), 0) FROM wallet_transaction_lines l WHERE l.transaction_id = t.id)
                """, Integer.class, wallet.userId());
        if (invalid != null && invalid > 0) problems.add("TRANSACTION_MISMATCH");
        for (String kind : List.of("BALANCE_MISMATCH", "LOT_LEDGER_MISMATCH", "TRANSACTION_MISMATCH")) {
            if (problems.contains(kind)) {
                if (jdbc.update("UPDATE wallet_integrity_findings SET resolved_at = NULL WHERE wallet_user_id = ? AND kind = ?", wallet.userId(), kind) == 0)
                    jdbc.update("INSERT INTO wallet_integrity_findings(wallet_user_id, kind, detected_at) VALUES (?, ?, ?)", wallet.userId(), kind, Timestamp.from(now));
            } else jdbc.update("UPDATE wallet_integrity_findings SET resolved_at = ? WHERE wallet_user_id = ? AND kind = ? AND resolved_at IS NULL",
                    Timestamp.from(now), wallet.userId(), kind);
        }
        return problems.isEmpty();
    }

    private WalletLot zero(WalletLot lot) {
        return new WalletLot(lot.id(), lot.balanceType(), lot.grantedAmount(), 0, lot.expiresAt(), lot.createdAt());
    }
    private WalletBalance balance(List<WalletLot> lots, Instant now) {
        return new WalletBalance(lots.stream().filter(l -> l.isUsableAt(now) && l.balanceType() == WalletLot.BalanceType.PAID).mapToInt(WalletLot::remainingAmount).sum(),
                lots.stream().filter(l -> l.isUsableAt(now) && l.balanceType() == WalletLot.BalanceType.BONUS).mapToInt(WalletLot::remainingAmount).sum());
    }
}
