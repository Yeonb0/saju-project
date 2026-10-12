package com.sajuppugi.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.kakao-oauth.rest-api-key=test-rest-api-key",
        "app.kakao-oauth.client-secret=test-client-secret",
        "app.kakao-oauth.redirect-uri=http://localhost:8080/api/v1/auth/kakao/callback"
})
class KakaoCallbackFailureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AuthRepository repository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean KakaoOAuthClient kakao;

    @AfterEach
    void removeSyntheticOAuthRows() {
        jdbc.update("DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE kakao_subject = ?)",
                "new-kakao-user");
        jdbc.update("DELETE FROM users WHERE kakao_subject = ?", "new-kakao-user");
        jdbc.update("DELETE FROM oauth_login_states WHERE state_hash IN (?, ?, ?)",
                SecretHash.sha256("cancelled-kakao-login-state"), SecretHash.sha256("failed-kakao-login-state"),
                SecretHash.sha256("successful-kakao-login-state"));
    }

    @Test
    void accessDeniedConsumesStateAndReturnsToLoginWithCancellationMessage() throws Exception {
        String state = "cancelled-kakao-login-state";
        repository.saveState(SecretHash.sha256(state), "/me", Instant.now().plusSeconds(60));

        mvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("state", state)
                        .param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login?returnTo=%2Fme&oauth=cancelled"));

        mvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("state", state)
                        .param("error", "access_denied"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerFailureConsumesStateAndReturnsToLoginWithFailureMessage() throws Exception {
        String state = "failed-kakao-login-state";
        repository.saveState(SecretHash.sha256(state), "/wallet", Instant.now().plusSeconds(60));

        mvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("state", state)
                        .param("error", "server_error"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login?returnTo=%2Fwallet&oauth=failed"));
    }

    @Test
    void successfulLoginRotatesAnExistingBrowserSessionOnlyAfterItsReplacementIsStored() throws Exception {
        String state = "successful-kakao-login-state";
        String previousRaw = "previous-browser-session";
        UUID previousUser = UUID.randomUUID();
        UUID previousSession = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, previousUser, "previous-" + previousUser, "이전 사용자", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, previousSession, previousUser, SecretHash.sha256(previousRaw), SecretHash.sha256("old-csrf"),
                Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now));
        repository.saveState(SecretHash.sha256(state), "/me", now.plusSeconds(60));
        given(kakao.userForCode("valid-code")).willReturn(new KakaoOAuthClient.KakaoUser("new-kakao-user", "새 사용자"));

        mvc.perform(get("/api/v1/auth/kakao/callback").cookie(new Cookie(AuthService.COOKIE, previousRaw))
                        .param("state", state).param("code", "valid-code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/me"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains(AuthService.COOKIE + "=").doesNotContain(previousRaw));
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM user_sessions WHERE id = ?", Boolean.class,
                previousSession)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE user_id <> ? AND revoked_at IS NULL",
                Integer.class, previousUser)).isEqualTo(1);
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", previousUser);
        jdbc.update("DELETE FROM users WHERE id = ?", previousUser);
    }

    @Test
    void retainsNewLoginAndOnlyFourMostRecentlyUsedOlderSessions() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, userId, "session-limit-" + userId, "세션 제한", Timestamp.from(now), Timestamp.from(now));
        UUID oldest = null;
        try {
            for (int i = 0; i < 6; i++) {
                UUID sessionId = UUID.randomUUID();
                if (i == 0) oldest = sessionId;
                repository.createSession(sessionId, userId, SecretHash.sha256("session-" + i),
                        SecretHash.sha256("csrf-" + i), now.plusSeconds(3600), now.plusSeconds(i), 5);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND revoked_at IS NULL",
                    Integer.class, userId)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM user_sessions WHERE id = ?", Boolean.class,
                    oldest)).isTrue();
        } finally {
            jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
