CREATE TABLE top_up_executions (
    order_id UUID PRIMARY KEY REFERENCES top_up_confirmation_intents(order_id) ON DELETE CASCADE,
    state VARCHAR(32) NOT NULL CHECK (state IN ('READY', 'IN_FLIGHT', 'RETRY', 'COMPLETED', 'REVIEW_REQUIRED')),
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
CREATE INDEX top_up_execution_due_idx ON top_up_executions(state, next_attempt_at);
INSERT INTO top_up_executions(order_id, state, next_attempt_at, updated_at)
SELECT order_id, 'READY', created_at, created_at FROM top_up_confirmation_intents;

CREATE TABLE wallet_top_up_receipts (
    order_id UUID PRIMARY KEY REFERENCES top_up_orders(id) ON DELETE CASCADE,
    wallet_user_id UUID NOT NULL REFERENCES wallets(user_id),
    transaction_id UUID NOT NULL UNIQUE,
    paid_balance INTEGER NOT NULL CHECK (paid_balance >= 0),
    bonus_balance INTEGER NOT NULL CHECK (bonus_balance >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (transaction_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id)
);
