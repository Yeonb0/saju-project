package com.sajuppugi;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.application.WalletQueryService;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletTransaction;
import com.sajuppugi.wallet.port.WalletReadRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WalletReadPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletReadRepository repository;
    private WalletQueryService service;

    @BeforeEach
    void setUp() {
        service = new WalletQueryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        for (UUID user : new UUID[] {USER, OTHER}) {
            jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", user, Timestamp.from(NOW));
        }
    }

    @Test
    void balanceIncludesOnlyOwnedUsableLotsAndExpiresAtExactBoundary() {
        lot(USER, "PAID", 10, NOW, NOW.plusSeconds(10));
        lot(USER, "BONUS", 3, NOW, NOW.plusSeconds(10));
        lot(USER, "PAID", 100, NOW.minusSeconds(1), NOW);
        lot(USER, "PAID", 100, NOW.plusSeconds(1), NOW.plusSeconds(10));
        lot(OTHER, "PAID", 100, NOW, NOW.plusSeconds(10));
        assertThat(service.getBalance(USER)).isEqualTo(new WalletBalance(10, 3));
        var expired = new WalletQueryService(repository, Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
        assertThat(expired.getBalance(USER)).isEqualTo(new WalletBalance(0, 0));
    }

    @Test
    void emptyWalletAndUnknownWalletHaveNoInventedCreditsOrWrites() {
        assertThat(service.getBalance(USER)).isEqualTo(new WalletBalance(0, 0));
        assertThat(service.getBalance(UUID.randomUUID())).isEqualTo(new WalletBalance(0, 0));
        assertThat(service.getTransactions(USER, null, 20).items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets", Integer.class)).isEqualTo(2);
    }

    @Test
    void pagesOwnedHistoryWithoutDuplicatesAtEqualTimestamps() {
        UUID userLot = lot(USER, "PAID", 30, NOW.minusSeconds(10), NOW.plusSeconds(10));
        for (long id : new long[] {1, 2, 3}) {
            transaction(new UUID(0, id), USER, userLot, "TOP_UP", 10, NOW);
        }
        UUID otherLot = lot(OTHER, "PAID", 10, NOW.minusSeconds(10), NOW.plusSeconds(10));
        transaction(new UUID(0, 4), OTHER, otherLot, "TOP_UP", 10, NOW.plusSeconds(1));
        var first = service.getTransactions(USER, null, 2);
        assertThat(first.items()).extracting(WalletTransaction::id).containsExactly(new UUID(0, 3), new UUID(0, 2));
        assertThat(first.nextCursor()).isEqualTo(new UUID(0, 2).toString());
        var second = service.getTransactions(USER, first.nextCursor(), 2);
        assertThat(second.items()).extracting(WalletTransaction::id).containsExactly(new UUID(0, 1));
        assertThat(second.nextCursor()).isNull();
        assertThat(second.items().getFirst().allocations())
                .containsExactly(new WalletTransaction.Allocation(userLot, 10));
    }

    @Test
    void pagesByTimeBeforeIdAndFinishesWithEmptyPage() {
        UUID userLot = lot(USER, "PAID", 30, NOW.minusSeconds(10), NOW.plusSeconds(10));
        transaction(new UUID(0, 1), USER, userLot, "TOP_UP", 10, NOW);
        transaction(new UUID(0, 2), USER, userLot, "TOP_UP", 10, NOW.minusSeconds(1));
        assertThat(service.getTransactions(USER, null, 20).items()).extracting(WalletTransaction::id)
                .containsExactly(new UUID(0, 1), new UUID(0, 2));
        var end = service.getTransactions(USER, new UUID(0, 2).toString(), 20);
        assertThat(end.items()).isEmpty();
        assertThat(end.nextCursor()).isNull();
    }

    @Test
    void rejectsAnotherUsersAndUnknownCursors() {
        UUID otherLot = lot(OTHER, "PAID", 10, NOW, NOW.plusSeconds(10));
        UUID id = UUID.randomUUID();
        transaction(id, OTHER, otherLot, "TOP_UP", 10, NOW);
        assertCode(() -> service.getTransactions(USER, id.toString(), 20), ErrorCode.INVALID_CURSOR);
        assertCode(() -> service.getTransactions(USER, UUID.randomUUID().toString(), 20), ErrorCode.INVALID_CURSOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-cursor", "1-1-1-1-1"})
    void rejectsMalformedCursors(String cursor) {
        assertCode(() -> service.getTransactions(USER, cursor, 20), ErrorCode.INVALID_CURSOR);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, Integer.MAX_VALUE})
    void rejectsOutOfRangePageSizes(int limit) {
        assertCode(() -> service.getTransactions(USER, null, limit), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void requiresServerSuppliedUser() {
        assertCode(() -> service.getBalance(null), ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.getTransactions(null, null, 20), ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @CsvSource({"TOP_UP,-10", "PURCHASE,10", "EXPIRY,10", "ADJUSTMENT,0"})
    void databaseAlsoRejectsInvalidLedgerDirection(String type, int amount) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), USER, type, amount, UUID.randomUUID(), Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAllocationReferencingAnotherUsersLot() {
        UUID ownLot = lot(USER, "PAID", 10, NOW, NOW.plusSeconds(10));
        UUID otherLot = lot(OTHER, "PAID", 10, NOW, NOW.plusSeconds(10));
        UUID transaction = UUID.randomUUID();
        transaction(transaction, USER, ownLot, "TOP_UP", 10, NOW);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, ?)
                """, transaction, otherLot, USER, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID lot(UUID user, String type, int amount, Instant created, Instant expires) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, user, type, amount, amount, Timestamp.from(created), Timestamp.from(expires));
        return id;
    }

    private void transaction(UUID id, UUID user, UUID lot, String type, int amount, Instant created) {
        jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, user, type, amount, UUID.randomUUID(), Timestamp.from(created));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, ?)",
                id, lot, user, amount);
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
