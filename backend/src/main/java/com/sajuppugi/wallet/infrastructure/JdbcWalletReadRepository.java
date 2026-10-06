package com.sajuppugi.wallet.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.application.WalletQueryUseCase.TransactionPage;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.domain.WalletTransaction.Allocation;
import com.sajuppugi.wallet.port.WalletReadRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWalletReadRepository implements WalletReadRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcWalletReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public WalletBalance usableBalance(UUID userId, Instant now) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN balance_type = 'PAID' THEN remaining_amount ELSE 0 END), 0) AS paid,
                COALESCE(SUM(CASE WHEN balance_type = 'BONUS' THEN remaining_amount ELSE 0 END), 0) AS bonus
                FROM wallet_lots WHERE wallet_user_id = ? AND created_at <= ? AND expires_at > ?
                """, (rs, row) -> new WalletBalance(Math.toIntExact(rs.getLong("paid")),
                        Math.toIntExact(rs.getLong("bonus"))), userId, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public TransactionPage transactions(UUID userId, UUID afterTransactionId, int limit) {
        var args = new ArrayList<Object>();
        args.add(userId);
        String cursorFilter = "";
        if (afterTransactionId != null) {
            var times = jdbc.query("SELECT created_at FROM wallet_transactions WHERE wallet_user_id = ? AND id = ?",
                    (rs, row) -> rs.getTimestamp("created_at"), userId, afterTransactionId);
            if (times.isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_CURSOR);
            }
            cursorFilter = " AND (created_at < ? OR (created_at = ? AND id < ?))";
            args.add(times.getFirst());
            args.add(times.getFirst());
            args.add(afterTransactionId);
        }
        args.add(limit + 1);
        List<TransactionRow> rows = jdbc.query("SELECT * FROM wallet_transactions WHERE wallet_user_id = ?"
                + cursorFilter + " ORDER BY created_at DESC, id DESC LIMIT ?", (rs, row) ->
                new TransactionRow(rs.getObject("id", UUID.class), WalletTransaction.Type.valueOf(rs.getString("type")),
                        rs.getInt("total_amount"), rs.getObject("reference_id", UUID.class),
                        rs.getObject("reversal_of_id", UUID.class), rs.getTimestamp("created_at").toInstant()),
                args.toArray());
        boolean hasMore = rows.size() > limit;
        List<TransactionRow> page = rows.subList(0, Math.min(limit, rows.size()));
        if (page.isEmpty()) {
            return new TransactionPage(List.of(), null);
        }
        Map<UUID, List<Allocation>> allocations = new HashMap<>();
        namedJdbc.query("""
                SELECT transaction_id, lot_id, amount FROM wallet_transaction_lines
                WHERE wallet_user_id = :userId AND transaction_id IN (:ids) ORDER BY lot_id
                """, Map.of("userId", userId, "ids", page.stream().map(TransactionRow::id).toList()),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> allocations
                        .computeIfAbsent(rs.getObject("transaction_id", UUID.class), id -> new ArrayList<>())
                        .add(new Allocation(rs.getObject("lot_id", UUID.class), rs.getInt("amount"))));
        List<WalletTransaction> items = page.stream().map(row -> new WalletTransaction(row.id(), userId,
                row.type(), row.amount(), row.referenceId(), row.reversalOfId(),
                allocations.getOrDefault(row.id(), List.of()), row.createdAt())).toList();
        return new TransactionPage(items, hasMore ? page.getLast().id().toString() : null);
    }

    private record TransactionRow(UUID id, WalletTransaction.Type type, int amount,
                                  UUID referenceId, UUID reversalOfId, Instant createdAt) {}
}
