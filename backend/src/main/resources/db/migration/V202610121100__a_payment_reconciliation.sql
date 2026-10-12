CREATE TABLE payment_reconciliation_jobs (
    order_id UUID PRIMARY KEY REFERENCES top_up_orders(id),
    state VARCHAR(30) NOT NULL CHECK (state IN ('READY','IN_FLIGHT','RETRY','CHECKED','REVIEW_REQUIRED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE,
    finding_code VARCHAR(40)
);
CREATE INDEX payment_reconciliation_due ON payment_reconciliation_jobs(state, next_attempt_at);
