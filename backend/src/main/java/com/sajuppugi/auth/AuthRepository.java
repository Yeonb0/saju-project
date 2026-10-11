package com.sajuppugi.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository {
    void saveState(String stateHash, String returnTo, Instant expiresAt);
    Optional<String> consumeState(String stateHash, Instant now);
    UUID upsertActiveMember(String kakaoSubject, String nickname, Instant now);
    void createSession(UUID id, UUID userId, String tokenHash, String csrfHash, Instant expiresAt, Instant now);
    Optional<Session> findSession(String tokenHash, Instant now);
    boolean rotateCsrf(UUID sessionId, String csrfHash, Instant now);
    boolean matchesCsrf(UUID sessionId, String csrfHash, Instant now);
    boolean revokeSession(String tokenHash, Instant now);
    record Session(UUID id, UUID userId, String nickname, Instant expiresAt) { }
}
