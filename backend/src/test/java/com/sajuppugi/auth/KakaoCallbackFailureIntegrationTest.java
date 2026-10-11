package com.sajuppugi.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
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
}
