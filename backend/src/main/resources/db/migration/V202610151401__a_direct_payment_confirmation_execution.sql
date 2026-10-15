-- A payment key is retained only as a digest.  The execution row is deliberately
-- separate from the batch so an interrupted provider call can be reconciled.
CREATE TABLE direct_payment_confirmation_requests (
    buyer_user_id UUID NOT NULL,
    key_hash CHAR(64) NOT NULL,
    batch_id UUID NOT NULL REFERENCES direct_payment_confirmation_intents(batch_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (buyer_user_id, key_hash)
);

CREATE TABLE direct_payment_executions (
    batch_id UUID PRIMARY KEY REFERENCES direct_payment_confirmation_intents(batch_id) ON DELETE CASCADE,
    state VARCHAR(30) NOT NULL CHECK (state IN ('READY', 'IN_FLIGHT', 'RETRY', 'COMPLETED', 'REVIEW_REQUIRED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    confirmation_started BOOLEAN NOT NULL DEFAULT FALSE,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failure_code VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((state = 'IN_FLIGHT' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state <> 'IN_FLIGHT' AND lease_token IS NULL AND lease_until IS NULL))
);

CREATE INDEX direct_payment_execution_due_idx ON direct_payment_executions(state, next_attempt_at);
