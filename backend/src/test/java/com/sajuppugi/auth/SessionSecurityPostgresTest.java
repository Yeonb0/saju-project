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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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

/** PostgreSQL-only check for durable opaque session authentication and CSRF revocation. */
@EnabledIfSystemProperty(named = "session.security.postgres", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/sajuppugi_test",
        "spring.datasource.username=sajuppugi_test",
        "spring.datasource.password=local-test-only"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionSecurityPostgresTest.FixedClockConfiguration.class)
class SessionSecurityPostgresTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthService auth;

    private UUID userId;
    private UUID sessionId;

    @Test
    void durableOpaqueSessionCsrfAndLogoutWorkOnPostgres() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("sajuppugi_test");
        String rawToken = "postgres-session-" + UUID.randomUUID();
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, userId, "kakao-" + userId, "PostgreSQL 테스트", timestamp(NOW), timestamp(NOW));
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sessionId, userId, SecretHash.sha256(rawToken), SecretHash.sha256("old-csrf"),
                timestamp(NOW.plusSeconds(3600)), timestamp(NOW));
        Cookie cookie = new Cookie(AuthService.COOKIE, rawToken);

        String body = mvc.perform(get("/api/v1/session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andReturn().getResponse().getContentAsString();
        String csrf = mapper.readTree(body).path("data").path("csrfToken").asText();
        assertThat(csrf).isNotBlank();
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
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM user_sessions WHERE id = ?", Boolean.class,
                sessionId)).isTrue();
    }

    @AfterEach
    void cleanUpSyntheticRows() {
        if (sessionId != null) jdbc.update("DELETE FROM user_sessions WHERE id = ?", sessionId);
        if (userId != null) jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
