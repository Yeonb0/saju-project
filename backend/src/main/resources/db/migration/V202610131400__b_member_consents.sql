CREATE TABLE member_consents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    terms_version VARCHAR(40) NOT NULL,
    privacy_version VARCHAR(40) NOT NULL,
    age_over_14_confirmed BOOLEAN NOT NULL CHECK (age_over_14_confirmed = TRUE),
    terms_agreed BOOLEAN NOT NULL CHECK (terms_agreed = TRUE),
    privacy_agreed BOOLEAN NOT NULL CHECK (privacy_agreed = TRUE),
    marketing_agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, terms_version, privacy_version)
);

CREATE INDEX member_consents_user_agreed_idx
    ON member_consents(user_id, agreed_at DESC);
