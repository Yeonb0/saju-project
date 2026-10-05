package com.sajuppugi.catalog.infrastructure;

import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.port.CatalogRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCatalogRepository implements CatalogRepository {
    private final JdbcTemplate jdbc;

    public JdbcCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> findAvailableProducts(Product.Category category, Instant now) {
        String filter = category == null ? "" : " AND category = ?";
        Object[] args = category == null ? new Object[] {timestamp(now), timestamp(now)}
                : new Object[] {timestamp(now), timestamp(now), category.name()};
        return jdbc.query("""
                SELECT * FROM products WHERE active = TRUE
                AND (sale_start_at IS NULL OR sale_start_at <= ?)
                AND (sale_end_at IS NULL OR sale_end_at > ?)
                """ + filter + " ORDER BY code", (rs, row) -> product(rs, "id", "code"), args);
    }

    @Override
    public Optional<Product> findProductByCode(String code) {
        return jdbc.query("SELECT * FROM products WHERE code = ?",
                (rs, row) -> product(rs, "id", "code"), code).stream().findFirst();
    }

    @Override
    public Optional<PurchaseQuote> findQuoteById(UUID quoteId) {
        return jdbc.query("SELECT * FROM purchase_quotes WHERE id = ?", (rs, row) ->
                new PurchaseQuote(rs.getObject("id", UUID.class), rs.getObject("requester_user_id", UUID.class),
                        product(rs, "product_id", "product_code"), rs.getString("context_hash"),
                        instant(rs, "created_at"), instant(rs, "expires_at")), quoteId).stream().findFirst();
    }

    @Override
    public void saveQuote(PurchaseQuote quote) {
        Product product = quote.productSnapshot();
        jdbc.update("""
                INSERT INTO purchase_quotes (id, requester_user_id, product_id, product_code, category,
                price_currency, price_amount, active, sale_start_at, sale_end_at, catalog_version,
                context_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, quote.id(), quote.requesterUserId(), product.id(), product.code(), product.category().name(),
                product.price().currency().name(), product.price().amount(), product.active(),
                timestamp(product.saleStartsAt()), timestamp(product.saleEndsAt()), product.catalogVersion(),
                quote.contextHash(), timestamp(quote.createdAt()), timestamp(quote.expiresAt()));
    }

    @Override
    public boolean bindQuoteToPurchase(UUID quoteId, UUID purchaseId) {
        java.util.Objects.requireNonNull(purchaseId, "purchaseId");
        return jdbc.update("UPDATE purchase_quotes SET purchase_id = ? WHERE id = ? AND purchase_id IS NULL",
                purchaseId, quoteId) == 1;
    }

    private static Product product(ResultSet rs, String idColumn, String codeColumn) throws SQLException {
        return new Product(rs.getObject(idColumn, UUID.class), rs.getString(codeColumn),
                Product.Category.valueOf(rs.getString("category")),
                new Price(Price.Currency.valueOf(rs.getString("price_currency")), rs.getInt("price_amount")),
                rs.getBoolean("active"), instant(rs, "sale_start_at"), instant(rs, "sale_end_at"),
                rs.getString("catalog_version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
