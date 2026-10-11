CREATE TABLE users (
    id UUID PRIMARY KEY,
    kakao_subject VARCHAR(100) NOT NULL UNIQUE,
    nickname VARCHAR(50),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'WITHDRAWN', 'BLOCKED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    withdrawal_requested_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX users_withdrawal_cleanup_idx ON users(status, withdrawal_requested_at);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    csrf_secret_hash CHAR(64) NOT NULL,
    device_label VARCHAR(100),
    ip_prefix VARCHAR(100),
    user_agent_summary VARCHAR(200),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX user_sessions_user_active_idx ON user_sessions(user_id, revoked_at, expires_at);
