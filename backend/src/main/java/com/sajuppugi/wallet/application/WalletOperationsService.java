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
    private final org.springframework.beans.factory.ObjectProvider<WalletPurchaseSettlementPort> settlements;

    public WalletOperationsService(WalletRepository wallets, JdbcTemplate jdbc, Clock clock,
                                   org.springframework.beans.factory.ObjectProvider<WalletPurchaseSettlementPort> settlements) {
        this.wallets = wallets; this.jdbc = jdbc; this.clock = clock; this.settlements = settlements;
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

    @Transactional(timeout = 10)
    public RemainingTopUpRefund remainingTopUp(UUID user, UUID order, int originalAmount, int paid, int bonus) {
        var before = wallets.lock(user);
        assertUsable(user);
        Instant now = clock.instant();
        if (!inspect(before, now)) throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.WALLET_REVIEW_REQUIRED);
        var granted = jdbc.query("""
                SELECT l.lot_id FROM wallet_transaction_lines l JOIN wallet_top_up_receipts r
                ON r.transaction_id = l.transaction_id WHERE r.order_id = ? AND r.wallet_user_id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), order, user);
        var lots = before.lots().stream().filter(l -> granted.contains(l.id())).toList();
        if (lots.isEmpty() || lots.size() != granted.size()
                || lots.stream().filter(l -> l.balanceType() == WalletLot.BalanceType.PAID).mapToInt(WalletLot::grantedAmount).sum() != paid
                || lots.stream().filter(l -> l.balanceType() == WalletLot.BalanceType.BONUS).mapToInt(WalletLot::grantedAmount).sum() != bonus)
            throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.REFUND_NOT_ELIGIBLE);
        // An unfinished purchase could still restore these lots; settle it before cash cancellation.
        var unsettled = jdbc.query("""
                SELECT DISTINCT d.quote_id,d.transaction_id FROM wallet_purchase_debits d
                JOIN wallet_transaction_lines used ON used.transaction_id = d.transaction_id
                JOIN wallet_transaction_lines granted ON granted.lot_id = used.lot_id
                JOIN wallet_top_up_receipts g ON g.transaction_id = granted.transaction_id
                WHERE g.order_id = ? AND used.amount < 0
                AND NOT EXISTS (SELECT 1 FROM wallet_purchase_compensations c WHERE c.original_transaction_id = d.transaction_id)
                """, (rs, row) -> new UnsettledPurchase(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), order);
        if (unsettled.stream().anyMatch(p -> settlements.stream().noneMatch(s -> s.fulfilled(user, p.quoteId(), p.transactionId()))))
            throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.REFUND_NOT_ELIGIBLE);
        int remainingPaid = lots.stream().filter(l -> l.balanceType() == WalletLot.BalanceType.PAID && l.isUsableAt(now))
                .mapToInt(WalletLot::remainingAmount).sum();
        if (remainingPaid == 0) throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.REFUND_NOT_ELIGIBLE);
        int remainingBonus = lots.stream().filter(l -> l.balanceType() == WalletLot.BalanceType.BONUS)
                .mapToInt(WalletLot::remainingAmount).sum();
        return new RemainingTopUpRefund(RemainingTopUpRefund.proratedAmount(originalAmount, paid, remainingPaid), remainingPaid, remainingBonus);
    }

    @Transactional(timeout = 10)
    public RemainingTopUpRefund reclaimRemainingTopUp(UUID user, UUID order, UUID refund, int amount, int paid, int bonus,
                                                     RemainingTopUpRefund expected) {
        var quote = remainingTopUp(user, order, amount, paid, bonus);
        if (!quote.equals(expected)) throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.REFUND_QUOTE_CHANGED);
        var before = wallets.lock(user);
        var granted = jdbc.query("""
                SELECT l.lot_id FROM wallet_transaction_lines l JOIN wallet_top_up_receipts r
                ON r.transaction_id = l.transaction_id WHERE r.order_id = ? AND r.wallet_user_id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), order, user);
        var lots = before.lots().stream().filter(l -> granted.contains(l.id()) && l.remainingAmount() > 0).toList();
        var after = before.lots().stream().map(l -> granted.contains(l.id()) ? zero(l) : l).toList();
        var lines = lots.stream().map(l -> new WalletTransaction.Allocation(l.id(), -l.remainingAmount())).toList();
        Instant now = clock.instant();
        wallets.record(before, after, balance(after, now), new WalletTransaction(UUID.randomUUID(), user,
                WalletTransaction.Type.REFUND, -Math.addExact(quote.paidShells(), quote.bonusShells()), refund, null, lines, now));
        return quote;
    }
    @Transactional(timeout = 10)
    public void returnRejectedCashRefund(UUID user, UUID refund) {
        var before = wallets.lock(user);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM wallet_cash_refund_returns WHERE refund_id = ?", Integer.class, refund) != 0) return;
        Instant now = clock.instant();
        if (!inspect(before, now)) throw new IllegalStateException("Refund return requires a consistent wallet");
        var originals = jdbc.query("SELECT id, total_amount FROM wallet_transactions WHERE wallet_user_id = ? AND reference_id = ? AND type = 'REFUND' AND total_amount < 0 AND reversal_of_id IS NULL",
                (rs, row) -> new Original(rs.getObject(1, UUID.class), rs.getInt(2)), user, refund);
        if (originals.size() != 1) throw new IllegalStateException("Refund reservation must have one original ledger entry");
        var original = originals.getFirst();
        var lines = jdbc.query("SELECT lot_id, amount FROM wallet_transaction_lines WHERE transaction_id = ? AND wallet_user_id = ?",
                (rs, row) -> new WalletTransaction.Allocation(rs.getObject(1, UUID.class), -rs.getInt(2)), original.id(), user);
        var deltas = lines.stream().collect(java.util.stream.Collectors.toMap(WalletTransaction.Allocation::lotId, WalletTransaction.Allocation::amount));
        if (!before.lots().stream().map(WalletLot::id).toList().containsAll(deltas.keySet())) throw new IllegalStateException("Refund return has missing lots");
        var after = before.lots().stream().map(l -> new WalletLot(l.id(), l.balanceType(), l.grantedAmount(),
                Math.addExact(l.remainingAmount(), deltas.getOrDefault(l.id(), 0)), l.expiresAt(), l.createdAt())).toList();
        UUID transaction = UUID.randomUUID();
        wallets.record(before, after, balance(after, now), new WalletTransaction(transaction, user,
                WalletTransaction.Type.ADJUSTMENT, -original.amount(), refund, original.id(), lines, now));
        jdbc.update("INSERT INTO wallet_cash_refund_returns(refund_id, original_transaction_id, transaction_id, created_at) VALUES (?, ?, ?, ?)",
                refund, original.id(), transaction, Timestamp.from(now));
    }
    private record Original(UUID id, int amount) {}
    private record UnsettledPurchase(UUID quoteId, UUID transactionId) {}
    public void assertUsable(UUID user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND kind = 'EXTERNAL_CANCEL_REVIEW' AND resolved_at IS NULL", Integer.class, user);
        if (count != null && count > 0) throw new com.sajuppugi.common.api.ApiException(com.sajuppugi.common.api.ErrorCode.WALLET_REVIEW_REQUIRED);
    }
    @Transactional(timeout = 10)
    public boolean inspectForRecovery(UUID user) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, user) == 0) return true;
        try { assertUsable(user); }
        catch (com.sajuppugi.common.api.ApiException restricted) { return false; }
        return inspect(wallets.lock(user), clock.instant());
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
