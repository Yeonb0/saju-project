CREATE TABLE admin_recovery_commands (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL REFERENCES users(id),
    order_id UUID NOT NULL REFERENCES top_up_orders(id),
    key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('READY','IN_FLIGHT','RETRY','COMPLETED','REVIEW_REQUIRED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    result_code VARCHAR(40),
    UNIQUE (operator_id, key_hash)
);
CREATE INDEX admin_recovery_due ON admin_recovery_commands(state, next_attempt_at);
