package com.sajuppugi.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.member.application.MemberService;
import com.sajuppugi.person.application.PersonService;
import com.sajuppugi.person.domain.Person;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MemberLifecycleTest.FixedClockConfiguration.class)
@Transactional
class MemberLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberService members;
    @Autowired PersonService people;

    @Test
    void returnsMemberAndWithdrawalImmediatelyRevokesEverySession() throws Exception {
        UUID userId = insertUser("ACTIVE", NOW.minusSeconds(3600), null);
        insertSession(userId, "a".repeat(64));
        insertSession(userId, "b".repeat(64));

        mvc.perform(get("/api/v1/me").with(user(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mvc.perform(delete("/api/v1/me").with(user(userId.toString())).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWAL_PENDING"))
                .andExpect(jsonPath("$.data.scheduledDeletionAt").value("2026-11-10T03:00:00Z"));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, userId)).isZero();
    }

    @Test
    void purgesDueMemberPersonalDataButLeavesRecentWithdrawalPending() {
        UUID due = insertUser("WITHDRAWAL_PENDING", NOW.minusSeconds(90L * 86400),
                NOW.minusSeconds(31L * 86400));
        UUID recent = insertUser("WITHDRAWAL_PENDING", NOW.minusSeconds(40L * 86400),
                NOW.minusSeconds(29L * 86400));
        people.create(due, Person.ProfileType.SELF, "삭제할 이름", birth(), null, false);
        people.create(recent, Person.ProfileType.SELF, "유지할 이름", birth(), null, false);

        assertThat(members.purgeDueWithdrawals()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, due))
                .isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, due)).isNull();
        assertThat(jdbc.queryForObject("SELECT kakao_subject FROM users WHERE id = ?", String.class, due))
                .isEqualTo("withdrawn:" + due);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM people WHERE owner_user_id = ?", Integer.class, due))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, recent))
                .isEqualTo("WITHDRAWAL_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM people WHERE owner_user_id = ?", Integer.class, recent))
                .isEqualTo(1);
    }

    @Test
    void rejectsWithdrawalWhileRefundablePaidBalanceRemains() throws Exception {
        UUID userId = insertUser("ACTIVE", NOW.minusSeconds(3600), null);
        jdbc.update("INSERT INTO wallets (user_id, paid_balance, bonus_balance, version, updated_at) VALUES (?, 5, 0, 0, ?)",
                userId, timestamp(NOW));
        jdbc.update("""
                INSERT INTO wallet_lots (id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                    expires_at, created_at) VALUES (?, ?, 'PAID', 5, 5, ?, ?)
                """, UUID.randomUUID(), userId, timestamp(NOW.plusSeconds(86400)), timestamp(NOW.minusSeconds(60)));

        mvc.perform(delete("/api/v1/me").with(user(userId.toString())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAID_BALANCE_REMAINS"));

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
    }

    private UUID insertUser(String status, Instant createdAt, Instant withdrawalAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at,
                    withdrawal_requested_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, "kakao-" + id, "테스트 회원", status, timestamp(createdAt), timestamp(createdAt), timestamp(withdrawalAt));
        return id;
    }

    private void insertSession(UUID userId, String tokenHash) {
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, tokenHash, "c".repeat(64), timestamp(NOW.plusSeconds(86400)), timestamp(NOW));
    }

    private BirthInput birth() {
        return new BirthInput(LocalDate.of(2000, 1, 2), LocalTime.of(12, 30), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE);
    }

    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
