CREATE TABLE daily_fortune_shares (
    id UUID PRIMARY KEY,
    public_token_hash CHAR(64) NOT NULL UNIQUE,
    public_token_enc BYTEA NOT NULL,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    fortune_date DATE NOT NULL,
    overall_score INTEGER NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    love_score INTEGER NOT NULL CHECK (love_score BETWEEN 0 AND 100),
    wealth_score INTEGER NOT NULL CHECK (wealth_score BETWEEN 0 AND 100),
    study_work_score INTEGER NOT NULL CHECK (study_work_score BETWEEN 0 AND 100),
    condition_score INTEGER NOT NULL CHECK (condition_score BETWEEN 0 AND 100),
    one_line_fortune VARCHAR(500) NOT NULL,
    lucky_color VARCHAR(100) NOT NULL,
    lucky_item VARCHAR(100) NOT NULL,
    lucky_place VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (owner_user_id, person_id, fortune_date)
);

CREATE INDEX daily_fortune_shares_expiry_idx ON daily_fortune_shares(expires_at);
