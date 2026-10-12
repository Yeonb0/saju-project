package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sajuppugi.wallet.application.WalletOperationsService;
import com.sajuppugi.wallet.application.WalletTopUpPort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WalletOperationsTest {
    @Autowired WalletOperationsService operations;
    @Autowired WalletTopUpPort topUps;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

    @Test void expiresBonusThenPaidWithExactlyOneLedgerPerRunAndKeepsOriginalGrant() {
        UUID user = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        credit(user, UUID.randomUUID(), 50, 6);
        when(clock.instant()).thenReturn(NOW.plusSeconds(366L * 86400));
        assertThat(operations.inspectAndExpire(user)).isTrue();
        assertThat(operations.inspectAndExpire(user)).isTrue();
        assertThat(value(user, "SELECT SUM(total_amount) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'EXPIRY'")).isEqualTo(-6);
        assertThat(value(user, "SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?")).isEqualTo(50);
        when(clock.instant()).thenReturn(NOW.plusSeconds(6L * 366 * 86400));
        operations.inspectAndExpire(user);
        operations.inspectAndExpire(user);
        assertThat(value(user, "SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'EXPIRY'")).isEqualTo(2);
        assertThat(value(user, "SELECT SUM(granted_amount) FROM wallet_lots WHERE wallet_user_id = ?")).isEqualTo(56);
        assertThat(value(user, "SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?")).isZero();
    }

    @Test void recordsCorruptionWithoutRepairingOrExpiringAndResolvesAfterRepair() {
        UUID user = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        credit(user, UUID.randomUUID(), 50, 6);
        jdbc.update("UPDATE wallets SET paid_balance = 99 WHERE user_id = ?", user);
        assertThat(operations.inspectAndExpire(user)).isFalse();
        assertThat(value(user, "SELECT paid_balance FROM wallets WHERE user_id = ?")).isEqualTo(99);
        assertThat(value(user, "SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND resolved_at IS NULL")).isEqualTo(1);
        jdbc.update("UPDATE wallets SET paid_balance = 50 WHERE user_id = ?", user);
        assertThat(operations.inspectAndExpire(user)).isTrue();
        assertThat(value(user, "SELECT COUNT(*) FROM wallet_integrity_findings WHERE wallet_user_id = ? AND resolved_at IS NULL")).isZero();
    }

    @Test void reclaimsOnlyTheOrdersUnusedPaidAndBonusLotsAndCannotReclaimTwice() {
        UUID user = UUID.randomUUID(), order = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        credit(user, order, 50, 6);
        credit(user, UUID.randomUUID(), 10, 0);
        assertThat(operations.reclaimUnusedTopUp(user, order, UUID.randomUUID(), 50, 6)).isTrue();
        assertThat(operations.reclaimUnusedTopUp(user, order, UUID.randomUUID(), 50, 6)).isFalse();
        assertThat(value(user, "SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?")).isEqualTo(10);
        assertThat(operations.inspectAndExpire(user)).isTrue();
    }

    @Test void wrongGrantAmountAndExpiredGrantDoNotReclaim() {
        UUID user = UUID.randomUUID(), order = UUID.randomUUID();
        when(clock.instant()).thenReturn(NOW);
        credit(user, order, 50, 6);
        assertThat(operations.reclaimUnusedTopUp(user, order, UUID.randomUUID(), 51, 6)).isFalse();
        when(clock.instant()).thenReturn(NOW.plusSeconds(366L * 86400));
        assertThat(operations.reclaimUnusedTopUp(user, order, UUID.randomUUID(), 50, 6)).isFalse();
    }

    private void credit(UUID user, UUID order, int paid, int bonus) {
        jdbc.update("""
                INSERT INTO top_up_orders(id, user_id, key_hash, product_code, amount_krw, paid_shells, bonus_shells, status, created_at)
                VALUES (?, ?, ?, 'TEST', 1000, ?, ?, 'CREDITED', ?)
                """, order, user, order.toString(), paid, bonus, java.sql.Timestamp.from(NOW));
        topUps.credit(user, order, paid, bonus);
    }
    private int value(UUID user, String sql) { return jdbc.queryForObject(sql, Integer.class, user); }
}
