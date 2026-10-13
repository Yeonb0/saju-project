CREATE TABLE gift_payload_reencryption_work (
    order_id UUID PRIMARY KEY REFERENCES gift_order_drafts(id) ON DELETE CASCADE,
    active_key_id VARCHAR(32) NOT NULL,
    failures INTEGER NOT NULL CHECK (failures BETWEEN 1 AND 5),
    state VARCHAR(30) NOT NULL CHECK (state IN ('RETRY','REVIEW_REQUIRED')),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX gift_payload_reencryption_due_idx ON gift_payload_reencryption_work(state,next_attempt_at,order_id);
