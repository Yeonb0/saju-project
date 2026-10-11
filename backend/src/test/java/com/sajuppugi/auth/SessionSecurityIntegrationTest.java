package com.sajuppugi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionSecurityIntegrationTest.FixedClockConfiguration.class)
class SessionSecurityIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthService auth;

    @Test
    void sessionCsrfLogoutAndRevocationUseTheSameOpaqueCookie() throws Exception {
        String rawToken = "session-for-real-filter-test";
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, userId, "kakao-" + userId, "테스트", timestamp(NOW), timestamp(NOW));
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, SecretHash.sha256(rawToken), SecretHash.sha256("old-csrf"),
                timestamp(NOW.plusSeconds(3600)), timestamp(NOW));
        Cookie cookie = new Cookie(AuthService.COOKIE, rawToken);

        String sessionBody = mvc.perform(get("/api/v1/session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andReturn().getResponse().getContentAsString();
        String csrf = mapper.readTree(sessionBody).path("data").path("csrfToken").asText();
        assertThat(csrf).isNotBlank().isNotEqualTo("old-csrf");
        assertThat(jdbc.queryForObject("SELECT csrf_secret_hash FROM user_sessions WHERE token_hash = ?", String.class,
                SecretHash.sha256(rawToken))).isEqualTo(SecretHash.sha256(csrf));
        assertThat(auth.matchesCsrf(auth.session(rawToken), csrf)).isTrue();

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_FAILED"));
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).header("X-CSRF-Token", csrf))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0"));
        mvc.perform(get("/api/v1/me").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void expiredSessionCannotAuthenticateOrReceiveACsrfToken() throws Exception {
        String rawToken = "expired-session-filter-test";
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, userId, "kakao-" + userId, "만료", timestamp(NOW), timestamp(NOW));
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, SecretHash.sha256(rawToken), SecretHash.sha256("expired-csrf"),
                timestamp(NOW.minusSeconds(1)), timestamp(NOW));
        Cookie cookie = new Cookie(AuthService.COOKIE, rawToken);

        mvc.perform(get("/api/v1/session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.csrfToken").doesNotExist());
        mvc.perform(get("/api/v1/me").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
