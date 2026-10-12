package com.sajuppugi.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kakao-oauth")
public record KakaoOAuthProperties(String restApiKey, String clientSecret, String redirectUri,
                                   String frontendBaseUrl, Duration sessionTtl, Duration stateTtl,
                                   int maxActiveSessions) {
    public KakaoOAuthProperties {
        if (maxActiveSessions < 1 || maxActiveSessions > 20) {
            throw new IllegalArgumentException("KAKAO_MAX_ACTIVE_SESSIONS must be between 1 and 20");
        }
    }
    @Override public String toString() { return "KakaoOAuthProperties[credentials=REDACTED]"; }
}
