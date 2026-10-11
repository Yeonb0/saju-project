package com.sajuppugi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class KakaoOAuthClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final KakaoOAuthProperties properties;
    KakaoOAuthClient(RestClient.Builder builder, ObjectMapper mapper, KakaoOAuthProperties properties) {
        this.client = builder.build(); this.mapper = mapper; this.properties = properties;
    }
    String authorizeUrl(String state) {
        return "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=" + encode(properties.restApiKey())
                + "&redirect_uri=" + encode(properties.redirectUri()) + "&state=" + encode(state);
    }
    KakaoUser userForCode(String code) {
        try {
            JsonNode tokens = client.post().uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=authorization_code&client_id=" + encode(properties.restApiKey())
                            + "&redirect_uri=" + encode(properties.redirectUri()) + "&code=" + encode(code)
                            + "&client_secret=" + encode(properties.clientSecret()))
                    .retrieve().body(JsonNode.class);
            String access = tokens == null ? null : tokens.path("access_token").asText(null);
            if (access == null || access.isBlank()) throw new IllegalStateException();
            JsonNode me = client.get().uri(URI.create("https://kapi.kakao.com/v2/user/me"))
                    .header("Authorization", "Bearer " + access).retrieve().body(JsonNode.class);
            String id = me == null ? null : me.path("id").asText(null);
            if (id == null || id.isBlank()) throw new IllegalStateException();
            JsonNode account = me.path("kakao_account");
            String nickname = account.path("profile").path("nickname").asText(null);
            return new KakaoUser(id, nickname == null || nickname.isBlank() ? null : nickname.substring(0, Math.min(50, nickname.length())));
        } catch (RuntimeException exception) { throw new IllegalArgumentException("KAKAO_OAUTH_FAILED"); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    record KakaoUser(String subject, String nickname) { }
}
