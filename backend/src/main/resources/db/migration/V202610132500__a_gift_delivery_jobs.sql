ALTER TABLE gift_delivery_outbox ADD COLUMN current_job_id UUID;
ALTER TABLE gift_order_drafts ADD COLUMN private_payload_purged_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE gift_delivery_jobs (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES gift_purchase_work(order_id) ON DELETE CASCADE,
    state VARCHAR(30) NOT NULL CHECK (state IN ('PENDING','IN_FLIGHT','RETRY','UNKNOWN','SENT','FAILED','REVIEW_REQUIRED','CANCELED')),
    sends INTEGER NOT NULL DEFAULT 0 CHECK (sends BETWEEN 0 AND 4),
    lookups INTEGER NOT NULL DEFAULT 0 CHECK (lookups BETWEEN 0 AND 5),
    submission_started BOOLEAN NOT NULL DEFAULT FALSE,
    provider_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);
CREATE INDEX gift_delivery_jobs_due_idx ON gift_delivery_jobs(state,next_attempt_at,id);
CREATE INDEX gift_delivery_jobs_order_idx ON gift_delivery_jobs(order_id,created_at,id);
CREATE TABLE gift_delivery_commands (
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES gift_purchase_work(order_id) ON DELETE CASCADE,
    action VARCHAR(20) NOT NULL CHECK (action IN ('RESEND','REISSUE')),
    job_id UUID NOT NULL REFERENCES gift_delivery_jobs(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(user_id,key_hash)
);
CREATE TABLE gift_delivery_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES gift_purchase_work(order_id) ON DELETE CASCADE,
    actor_id UUID,
    job_id UUID,
    action VARCHAR(60) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX gift_delivery_events_order_idx ON gift_delivery_events(order_id,created_at,id);

INSERT INTO gift_delivery_jobs(id,order_id,state,next_attempt_at,created_at)
    SELECT id,order_id,status,created_at,created_at FROM gift_delivery_outbox;
UPDATE gift_delivery_outbox SET current_job_id = id;
