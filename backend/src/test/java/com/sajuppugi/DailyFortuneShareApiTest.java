package com.sajuppugi;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sajuppugi.fortune.daily.application.DailyFortuneService;
import com.sajuppugi.share.DailyFortuneShareService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyFortuneShareApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DailyFortuneShareService shares;
    @MockitoBean DailyFortuneService fortunes;
    UUID owner = UUID.randomUUID();
    UUID person = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    @BeforeEach
    void setup() {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");
        jdbc.update("INSERT INTO users(id,kakao_subject,status,created_at,updated_at) VALUES (?,?, 'ACTIVE',?,?)",
                owner, "share-owner-" + owner, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO users(id,kakao_subject,status,created_at,updated_at) VALUES (?,?, 'ACTIVE',?,?)",
                other, "share-other-" + other, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO people(id,owner_user_id,profile_type,display_name_enc,birth_date_enc,birth_time_enc,
                calendar_type,is_leap_month,birth_time_unknown,gender,birth_fingerprint,created_at,updated_at)
                VALUES (?,?,'SELF',?,?,?,'SOLAR',FALSE,FALSE,'UNSPECIFIED',?,?,?)
                """, person, owner, new byte[]{1}, new byte[]{2}, new byte[]{3}, "f".repeat(64),
                Timestamp.from(now), Timestamp.from(now));
        when(fortunes.today(owner, person)).thenReturn(fortune());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM daily_fortune_shares WHERE owner_user_id IN (?,?)", owner, other);
        jdbc.update("DELETE FROM people WHERE id = ?", person);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", owner, other);
    }

    @Test
    void createsReusesAndPubliclyReturnsOnlyAnonymousSnapshot() throws Exception {
        String first = create(owner, person);
        String second = create(owner, person);
        String token = JsonPath.read(first, "$.data.token");
        String shareId = JsonPath.read(first, "$.data.shareId");
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(second, "$.data.token")).isEqualTo(token);
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(second, "$.data.shareId")).isEqualTo(shareId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_fortune_shares WHERE person_id=?", Integer.class, person)).isOne();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT public_token_hash FROM daily_fortune_shares WHERE person_id=?", String.class, person))
                .matches("[a-f0-9]{64}").isNotEqualTo(token);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT public_token_enc FROM daily_fortune_shares WHERE person_id=?", byte[].class, person))
                .asString().doesNotContain(token);

        String body = mvc.perform(get("/api/v1/daily-fortune-shares/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-10-13"))
                .andExpect(jsonPath("$.data.scores.overall").value(71))
                .andExpect(jsonPath("$.data.content.oneLineFortune").value("오늘 문장"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("personId", "userId", "owner", "birth", "gender", "이름", "todayAction");
    }

    @Test
    void ownerCanRevokeButOthersCannotAndExpiredLinksAreGone() throws Exception {
        String created = create(owner, person);
        String token = JsonPath.read(created, "$.data.token");
        String shareId = JsonPath.read(created, "$.data.shareId");
        mvc.perform(delete("/api/v1/daily-fortune-shares/{id}", shareId).with(user(other.toString())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/daily-fortune-shares/{id}", shareId).with(user(owner.toString())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/daily-fortune-shares/{token}", token))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SHARE_REVOKED"));

        String renewed = create(owner, person);
        String renewedToken = JsonPath.read(renewed, "$.data.token");
        jdbc.update("UPDATE daily_fortune_shares SET expires_at=? WHERE id=?",
                Timestamp.from(Instant.EPOCH), UUID.fromString(shareId));
        mvc.perform(get("/api/v1/daily-fortune-shares/{token}", renewedToken))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SHARE_EXPIRED"));
    }

    @Test
    void mutationRequiresAuthenticationAndCsrfAndUnknownTokenIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/daily-fortunes/today/share").param("personId", person.toString()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/daily-fortunes/today/share").param("personId", person.toString()).with(user(owner.toString())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/daily-fortune-shares/not-a-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentCreationReturnsOneActiveLink() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return shares.create(owner, person); });
            var second = pool.submit(() -> { start.await(); return shares.create(owner, person); });
            start.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThat(a.shareId()).isEqualTo(b.shareId());
            org.assertj.core.api.Assertions.assertThat(a.token()).isEqualTo(b.token());
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_fortune_shares WHERE owner_user_id=? AND person_id=?",
                Integer.class, owner, person)).isOne();
    }

    private String create(UUID userId, UUID personId) throws Exception {
        return mvc.perform(post("/api/v1/daily-fortunes/today/share").param("personId", personId.toString())
                        .with(user(userId.toString())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
    }

    private DailyFortuneService.DailyFortune fortune() {
        return new DailyFortuneService.DailyFortune(LocalDate.of(2026, 10, 13), person,
                new DailyFortuneService.Scores(71, 62, 53, 44, 35),
                new DailyFortuneService.DailyContent("오늘 문장", "민트", "책갈피", "공원", "한 걸음"),
                "calc-v1", "daily-v2");
    }
}
