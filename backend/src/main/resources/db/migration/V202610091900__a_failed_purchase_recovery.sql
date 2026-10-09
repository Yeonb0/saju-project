ALTER TABLE reading_purchases ADD COLUMN recovery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (recovery_attempts >= 0);
ALTER TABLE reading_purchases ADD COLUMN recovery_next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reading_purchases ADD COLUMN recovery_last_error_code VARCHAR(100);

CREATE INDEX reading_purchases_recovery_idx ON reading_purchases(status, recovery_next_attempt_at, created_at);
