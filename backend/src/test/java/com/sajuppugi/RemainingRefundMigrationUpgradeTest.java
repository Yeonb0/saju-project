package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class RemainingRefundMigrationUpgradeTest {
    @Test void latestMainSchemaUpgradesWithoutChangingAnExistingFullRefund() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        String username = System.getenv("TEST_DATABASE_USERNAME"), password = System.getenv("TEST_DATABASE_PASSWORD");
        String schema = "refund_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, username, password)) {
            try {
                Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                        .target(MigrationVersion.fromVersion("202610131100")).load().migrate();
                connection.createStatement().execute("SET search_path TO " + schema);
                UUID order = UUID.randomUUID(), customer = UUID.randomUUID(), refund = UUID.randomUUID();
                try (var statement = connection.prepareStatement("""
                        INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at)
                        VALUES (?,?,?,'TOPUP_5000',5000,500,50,'CREDITED',CURRENT_TIMESTAMP)
                        """)) {
                    statement.setObject(1, order); statement.setObject(2, customer); statement.setString(3, "a".repeat(64)); statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("""
                        INSERT INTO top_up_refunds(id,order_id,user_id,key_hash,state,amount_krw,reason_code,next_attempt_at,created_at)
                        VALUES (?,?,?,?,'RESERVED',5000,'UNUSED_TOP_UP',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """)) {
                    statement.setObject(1, refund); statement.setObject(2, order); statement.setObject(3, customer);
                    statement.setString(4, "b".repeat(64)); statement.executeUpdate();
                }
                var result = Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                        .target(MigrationVersion.fromVersion("202610131200")).load().migrate();
                assertThat(result.migrationsExecuted).isEqualTo(1);
                try (var statement = connection.prepareStatement("SELECT state,amount_krw,admin_case_id,reclaimed_paid,reclaimed_bonus FROM top_up_refunds WHERE id = ?")) {
                    statement.setObject(1, refund);
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("RESERVED");
                        assertThat(rows.getInt(2)).isEqualTo(5000);
                        assertThat(rows.getObject(3)).isNull(); assertThat(rows.getObject(4)).isNull(); assertThat(rows.getObject(5)).isNull();
                    }
                }
            } finally {
                // This schema is created by this test, never the application's public schema.
                assertThat(schema).matches("refund_upgrade_[a-f0-9]{32}");
                connection.createStatement().execute("SET search_path TO public");
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
