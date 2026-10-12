CREATE TABLE top_up_refunds (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES top_up_orders(id),
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    state VARCHAR(30) NOT NULL CHECK (state IN ('RESERVED','IN_FLIGHT','RETRY','REFUNDED','REVIEW_REQUIRED')),
    amount_krw INTEGER NOT NULL CHECK (amount_krw > 0),
    reason_code VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (user_id, key_hash)
);
CREATE INDEX top_up_refunds_due ON top_up_refunds(state, next_attempt_at);
