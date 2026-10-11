package com.sajuppugi.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kakao-oauth")
public record KakaoOAuthProperties(String restApiKey, String clientSecret, String redirectUri,
                                   String frontendBaseUrl, Duration sessionTtl, Duration stateTtl) {
    @Override public String toString() { return "KakaoOAuthProperties[credentials=REDACTED]"; }
}
