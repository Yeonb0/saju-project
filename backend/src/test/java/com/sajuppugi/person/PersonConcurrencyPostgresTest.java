package com.sajuppugi.person;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.person.application.PersonService;
import com.sajuppugi.person.domain.Person;
import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL-only proof that the owner row lock serializes person creation limits. */
@EnabledIfSystemProperty(named = "person.concurrency.postgres", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/sajuppugi_test",
        "spring.datasource.username=sajuppugi_test",
        "spring.datasource.password=local-test-only"})
@ActiveProfiles("test")
class PersonConcurrencyPostgresTest {
    @Autowired PersonService people;
    @Autowired JdbcTemplate jdbc;

    private UUID owner;

    @Test
    void ownerLockAllowsOneSelfAndAtMostTenOthersAcrossConcurrentTransactions() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("sajuppugi_test");
        owner = UUID.randomUUID();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, owner, "person-concurrency-" + owner, "테스트", now, now);

        List<Object> selfResults = concurrently(2, () -> create(Person.ProfileType.SELF, "본인", null, false));
        assertThat(selfResults).filteredOn(Person.class::isInstance).hasSize(1);
        assertThat(selfResults).contains(ErrorCode.PRIMARY_PERSON_ALREADY_EXISTS);

        List<Object> otherResults = concurrently(12,
                () -> create(Person.ProfileType.OTHER, "친구", RelationType.FRIEND, true));
        assertThat(otherResults).filteredOn(Person.class::isInstance).hasSize(10);
        assertThat(otherResults).filteredOn(ErrorCode.PERSON_LIMIT_EXCEEDED::equals).hasSize(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM people
                WHERE owner_user_id = ? AND profile_type = 'OTHER' AND deleted_at IS NULL
                """, Integer.class, owner)).isEqualTo(10);
    }

    @AfterEach
    void cleanUpSyntheticRows() {
        if (owner == null) return;
        jdbc.update("DELETE FROM people WHERE owner_user_id = ?", owner);
        jdbc.update("DELETE FROM person_owner_locks WHERE owner_user_id = ?", owner);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
    }

    private List<Object> concurrently(int workers, Callable<Object> command) throws Exception {
        try (var executor = Executors.newFixedThreadPool(workers)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Object>> commands = java.util.stream.IntStream.range(0, workers)
                    .<Callable<Object>>mapToObj(ignored -> () -> {
                        start.await();
                        try {
                            return command.call();
                        } catch (ApiException exception) {
                            return exception.errorCode();
                        }
                    }).toList();
            List<java.util.concurrent.Future<Object>> results = commands.stream().map(executor::submit).toList();
            start.countDown();
            return results.stream().map(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();
        }
    }

    private Person create(Person.ProfileType type, String name, RelationType relation, boolean consent) {
        return people.create(owner, type, name, new BirthInput(LocalDate.of(2008, 3, 12), LocalTime.of(13, 25), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE), relation, consent);
    }
}
