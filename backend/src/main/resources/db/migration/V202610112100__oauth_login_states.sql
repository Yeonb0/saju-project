CREATE TABLE oauth_login_states (
    state_hash CHAR(64) PRIMARY KEY,
    return_to VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX oauth_login_states_expiry_idx ON oauth_login_states(expires_at);
