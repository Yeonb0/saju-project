package com.sajuppugi.admin.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.logging.TraceContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A-owned quote and ledger facts only; no B content, generation state or recovery command. */
@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, timeout = 5)
public class AdminPurchaseSupportQueries {
    private final JdbcTemplate jdbc;
    private final AdminAccessService access;
    private final AuditLogPort audit;
    private final Clock clock;

    public AdminPurchaseSupportQueries(JdbcTemplate jdbc, AdminAccessService access, AuditLogPort audit, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.audit = audit; this.clock = clock;
    }

    public QuoteSupport quote(UUID actor, UUID quoteId) {
        access.require(actor, false);
        if (quoteId == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var rows = jdbc.query("""
                SELECT q.id,q.requester_user_id,q.product_code,q.category,q.price_currency,q.price_amount,
                    q.created_at,q.expires_at,q.purchase_id AS bound_reference_id,g.id AS gift_order_id,
                    d.transaction_id AS debit_id,t.created_at AS debited_at,-t.total_amount AS debited_shells,
                    c.transaction_id AS return_id,rt.created_at AS returned_at,rt.total_amount AS returned_shells
                FROM purchase_quotes q
                LEFT JOIN wallet_purchase_debits d ON d.quote_id=q.id AND d.wallet_user_id=q.requester_user_id
                LEFT JOIN wallet_transactions t ON t.id=d.transaction_id AND t.wallet_user_id=d.wallet_user_id
                LEFT JOIN wallet_purchase_compensations c ON c.original_transaction_id=d.transaction_id AND c.wallet_user_id=d.wallet_user_id
                LEFT JOIN wallet_transactions rt ON rt.id=c.transaction_id AND rt.wallet_user_id=c.wallet_user_id
                LEFT JOIN gift_order_drafts g ON g.quote_id=q.id AND g.user_id=q.requester_user_id
                WHERE q.id=?
                """, (rs, row) -> new QuoteSupport(rs.getObject("id", UUID.class), rs.getObject("requester_user_id", UUID.class),
                rs.getString("product_code"), rs.getString("category"), rs.getString("price_currency"), rs.getInt("price_amount"),
                instant(rs, "created_at"), instant(rs, "expires_at"), rs.getObject("bound_reference_id", UUID.class),
                rs.getObject("gift_order_id", UUID.class), rs.getObject("return_id", UUID.class) != null ? "RETURN_COMMITTED"
                    : rs.getObject("debit_id", UUID.class) != null ? "DEBIT_COMMITTED" : "NO_DEBIT_RECEIPT",
                rs.getObject("debit_id", UUID.class), rs.getObject("debited_shells", Integer.class), instant(rs, "debited_at"),
                rs.getObject("return_id", UUID.class), rs.getObject("returned_shells", Integer.class), instant(rs, "returned_at")), quoteId);
        var result = rows.stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        audit.append(new AuditLogPort.Entry(actor, "ADMIN_PURCHASE_QUOTE_VIEW", "PURCHASE_QUOTE", quoteId,
                "SUPPORT_VIEW", TraceContext.currentId(), clock.instant()));
        return result;
    }

    private Instant instant(ResultSet rs, String field) throws SQLException {
        var value = rs.getTimestamp(field); return value == null ? null : value.toInstant();
    }

    public record QuoteSupport(UUID quoteId, UUID buyerId, String productCode, String category, String priceCurrency,
            int quotedAmount, Instant createdAt, Instant expiresAt, UUID boundReferenceId, UUID giftOrderId,
            String financialState, UUID debitTransactionId, Integer debitedShells, Instant debitedAt,
            UUID returnTransactionId, Integer returnedShells, Instant returnedAt) {}
}
