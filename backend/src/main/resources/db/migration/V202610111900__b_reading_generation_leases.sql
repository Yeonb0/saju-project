ALTER TABLE reading_purchases ADD COLUMN generation_lease_token UUID;
ALTER TABLE reading_purchases ADD COLUMN generation_lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE reading_purchases ADD COLUMN generation_fence BIGINT NOT NULL DEFAULT 0 CHECK (generation_fence >= 0);
ALTER TABLE reading_purchases ADD COLUMN generation_attempts INTEGER NOT NULL DEFAULT 0 CHECK (generation_attempts >= 0);

CREATE INDEX reading_purchases_generation_lease_idx
    ON reading_purchases (status, generation_lease_until, created_at);
