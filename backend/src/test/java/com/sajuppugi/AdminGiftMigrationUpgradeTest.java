package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class AdminGiftMigrationUpgradeTest {
    @TempDir Path mainMigrations;

    @Test void alreadyAppliedDirectLinkSchemaNeedsExplicitOneTimeOutOfOrderMigration() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv("TEST_DATABASE_USERNAME"), password = System.getenv("TEST_DATABASE_PASSWORD");
        String schema = "admin_gift_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Path source = Path.of(getClass().getClassLoader().getResource("db/migration").toURI());
        try (var files = Files.list(source)) {
            for (Path migration : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                if (!migration.getFileName().toString().startsWith("V202610140100__"))
                    Files.copy(migration, mainMigrations.resolve(migration.getFileName()));
            }
        }
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try {
                Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .locations("filesystem:" + mainMigrations).load().migrate();
                connection.createStatement().execute("SET search_path TO " + schema);
                UUID quote = UUID.randomUUID();
                try (var statement = connection.prepareStatement("""
                        INSERT INTO purchase_quotes(id,requester_user_id,product_id,product_code,category,
                            price_currency,price_amount,active,catalog_version,context_hash,created_at,expires_at)
                        SELECT ?,?,id,code,category,price_currency,price_amount,active,catalog_version,?,
                            CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 minutes' FROM products LIMIT 1
                        """)) {
                    statement.setObject(1, quote); statement.setObject(2, UUID.randomUUID());
                    statement.setString(3, "a".repeat(64)); assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                var normal = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).load();
                assertThat(normal.validateWithResult().validationSuccessful).isFalse();
                var upgrade = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .outOfOrder(true).load().migrate();
                assertThat(upgrade.migrationsExecuted).isEqualTo(1);
                normal.validate();
                connection.createStatement().execute("SET search_path TO " + schema);
                try (var statement = connection.prepareStatement("SELECT context_hash FROM purchase_quotes WHERE id = ?")) {
                    statement.setObject(1, quote);
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("a".repeat(64));
                    }
                }
                try (var rows = connection.createStatement().executeQuery("SELECT success FROM flyway_schema_history WHERE version = '202610140100'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isTrue();
                }
                try (var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM admin_gift_maintenance_requests")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
                }
                try (var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '" + schema + "' AND table_name = 'gift_purchase_tokens' AND column_name = 'token_ciphertext'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
            } finally {
                assertThat(schema).matches("admin_gift_upgrade_[a-f0-9]{32}");
                connection.createStatement().execute("SET search_path TO public");
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
