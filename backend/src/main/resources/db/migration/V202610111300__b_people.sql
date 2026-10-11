CREATE TABLE people (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    profile_type VARCHAR(20) NOT NULL CHECK (profile_type IN ('SELF', 'OTHER')),
    display_name_enc BYTEA NOT NULL,
    birth_date_enc BYTEA NOT NULL,
    birth_time_enc BYTEA,
    calendar_type VARCHAR(10) NOT NULL CHECK (calendar_type IN ('SOLAR', 'LUNAR')),
    is_leap_month BOOLEAN NOT NULL,
    birth_time_unknown BOOLEAN NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'UNSPECIFIED')),
    relation_type VARCHAR(30),
    birth_fingerprint CHAR(64) NOT NULL,
    third_party_confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT people_birth_time_check CHECK (
        (birth_time_unknown = TRUE AND birth_time_enc IS NULL)
        OR (birth_time_unknown = FALSE AND birth_time_enc IS NOT NULL)
    ),
    CONSTRAINT people_calendar_check CHECK (calendar_type = 'LUNAR' OR is_leap_month = FALSE),
    CONSTRAINT people_relation_check CHECK (
        (profile_type = 'SELF' AND relation_type IS NULL AND third_party_confirmed_at IS NULL)
        OR (profile_type = 'OTHER' AND relation_type IS NOT NULL AND third_party_confirmed_at IS NOT NULL)
    )
);

CREATE INDEX people_owner_active_idx ON people(owner_user_id, deleted_at, created_at);
CREATE INDEX people_owner_fingerprint_idx ON people(owner_user_id, birth_fingerprint);
