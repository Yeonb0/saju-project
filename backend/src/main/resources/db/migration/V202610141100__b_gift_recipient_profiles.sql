CREATE TABLE gift_recipient_profiles (
    id UUID PRIMARY KEY,
    gift_id UUID NOT NULL UNIQUE REFERENCES gift_purchase_work(gift_id),
    profile_ciphertext TEXT,
    encryption_key_id VARCHAR(32) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    purged_at TIMESTAMP WITH TIME ZONE,
    purge_policy_version VARCHAR(40),
    CHECK ((profile_ciphertext IS NULL) = (purged_at IS NOT NULL)),
    CHECK (purged_at IS NULL OR purge_policy_version IS NOT NULL)
);

CREATE TABLE gift_fulfillment_jobs (
    id UUID PRIMARY KEY,
    gift_id UUID NOT NULL UNIQUE,
    profile_id UUID NOT NULL UNIQUE REFERENCES gift_recipient_profiles(id),
    product_code VARCHAR(100) NOT NULL,
    state VARCHAR(30) NOT NULL CHECK (state IN
        ('PENDING','GENERATING','WAITING_TALISMAN','READY','RETRY','FAILED','REVIEW_REQUIRED','CANCELED','PURGED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 10),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_failure_code VARCHAR(80),
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    fencing_version BIGINT NOT NULL DEFAULT 0 CHECK (fencing_version >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);

CREATE INDEX gift_fulfillment_jobs_due_idx
    ON gift_fulfillment_jobs(state, next_attempt_at, gift_id);
