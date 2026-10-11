package com.sajuppugi.auth;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    static final String COOKIE = "SAJUPPUGI_SESSION";
    private final AuthRepository repository; private final KakaoOAuthClient kakao; private final KakaoOAuthProperties props; private final Clock clock;
    AuthService(AuthRepository repository, KakaoOAuthClient kakao, KakaoOAuthProperties props, Clock clock) { this.repository=repository; this.kakao=kakao; this.props=props; this.clock=clock; }
    String start(String returnTo) {
        configured(); String state = SecretHash.randomToken(); Instant now=clock.instant();
        repository.saveState(SecretHash.sha256(state), safeReturnTo(returnTo), now.plus(props.stateTtl()));
        return kakao.authorizeUrl(state);
    }
    Login complete(String state, String code, String previousRawSession) {
        configured(); Instant now=clock.instant(); String returnTo = consumeReturnTo(state, now);
        KakaoOAuthClient.KakaoUser user = kakao.userForCode(required(code)); UUID userId=repository.upsertActiveMember(user.subject(), user.nickname(), now);
        String token=SecretHash.randomToken(); UUID sessionId=UUID.randomUUID(); Instant expires=now.plus(props.sessionTtl());
        repository.createSession(sessionId,userId,SecretHash.sha256(token),SecretHash.sha256(SecretHash.randomToken()),expires,now);
        // The provider exchange occurs before database writes. Only revoke an existing browser session after its
        // replacement was stored, so a provider or storage failure does not unnecessarily sign the user out.
        if (previousRawSession != null) repository.revokeSession(SecretHash.sha256(previousRawSession), now);
        return new Login(token, returnTo);
    }
    String abort(String state) { configured(); return consumeReturnTo(state, clock.instant()); }
    AuthRepository.Session session(String raw) { return raw == null ? null : repository.findSession(SecretHash.sha256(raw), clock.instant()).orElse(null); }
    String csrf(AuthRepository.Session session) { String token=SecretHash.randomToken(); if (!repository.rotateCsrf(session.id(), SecretHash.sha256(token),clock.instant())) throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); return token; }
    boolean matchesCsrf(AuthRepository.Session session, String token) { return token != null && session != null && repository.matchesCsrf(session.id(), SecretHash.sha256(token), clock.instant()); }
    void logout(String raw) { if(raw != null) repository.revokeSession(SecretHash.sha256(raw), clock.instant()); }
    boolean isSecure() { return props.redirectUri() != null && props.redirectUri().startsWith("https://"); }
    String frontendUrl(String returnTo) { return URI.create(props.frontendBaseUrl()).resolve(safeReturnTo(returnTo)).toString(); }
    String loginFailureUrl(String returnTo, boolean cancelled) {
        return URI.create(props.frontendBaseUrl()).resolve("/login?returnTo="
                + java.net.URLEncoder.encode(safeReturnTo(returnTo), java.nio.charset.StandardCharsets.UTF_8)
                + "&oauth=" + (cancelled ? "cancelled" : "failed")).toString();
    }
    private String consumeReturnTo(String state, Instant now) {
        return repository.consumeState(SecretHash.sha256(required(state)), now)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST));
    }
    private void configured() { if (blank(props.restApiKey()) || blank(props.clientSecret()) || blank(props.redirectUri()) || blank(props.frontendBaseUrl())) throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR); }
    private static String required(String value) { if (blank(value) || value.length()>2000) throw new ApiException(ErrorCode.INVALID_REQUEST); return value; }
    static String safeReturnTo(String value) { if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("\\\\") || value.startsWith("/login") || value.startsWith("/onboarding")) return "/"; return value; }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    record Login(String rawToken, String returnTo) { }
}
