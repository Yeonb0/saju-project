CREATE TABLE talismans (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    source_reading_id UUID NOT NULL UNIQUE,
    fortune_type VARCHAR(30) NOT NULL
        CHECK (fortune_type IN ('OVERALL', 'LOVE', 'WEALTH', 'COMPATIBILITY', 'SINSAL', 'SUNEUNG')),
    element VARCHAR(10) NOT NULL
        CHECK (element IN ('WOOD', 'FIRE', 'EARTH', 'METAL', 'WATER')),
    animal VARCHAR(10) NOT NULL
        CHECK (animal IN ('RAT', 'OX', 'TIGER', 'RABBIT', 'DRAGON', 'SNAKE',
            'HORSE', 'GOAT', 'MONKEY', 'ROOSTER', 'DOG', 'PIG')),
    phrase VARCHAR(200) NOT NULL,
    description VARCHAR(300) NOT NULL,
    content_version VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'READY', 'FAILED')),
    original_object_key VARCHAR(500),
    thumbnail_object_key VARCHAR(500),
    share_object_key VARCHAR(500),
    failure_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ready_at TIMESTAMP WITH TIME ZONE,
    CHECK (
        (status = 'PENDING' AND original_object_key IS NULL AND thumbnail_object_key IS NULL
            AND share_object_key IS NULL AND failure_code IS NULL AND ready_at IS NULL)
        OR (status = 'READY' AND original_object_key IS NOT NULL AND thumbnail_object_key IS NOT NULL
            AND share_object_key IS NOT NULL AND failure_code IS NULL AND ready_at IS NOT NULL)
        OR (status = 'FAILED' AND original_object_key IS NULL AND thumbnail_object_key IS NULL
            AND share_object_key IS NULL AND failure_code IS NOT NULL AND ready_at IS NULL)
    )
);

CREATE TABLE talisman_ownerships (
    talisman_id UUID NOT NULL REFERENCES talismans(id),
    owner_user_id UUID NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('PURCHASE', 'GIFT')),
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (talisman_id, owner_user_id)
);

CREATE INDEX talisman_ownerships_vault_idx
    ON talisman_ownerships(owner_user_id, acquired_at DESC, talisman_id DESC);
