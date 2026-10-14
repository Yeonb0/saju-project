ALTER TABLE gift_purchase_work DROP CONSTRAINT gift_purchase_work_status_check;
ALTER TABLE gift_purchase_work ADD CONSTRAINT gift_purchase_work_status_check
    CHECK (status IN ('PENDING','ACTIVE','REFUNDED','FAILED','REVIEW_REQUIRED','CANCEL_PENDING','CANCELED'));
ALTER TABLE gift_purchase_work ADD COLUMN first_provided_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE gift_action_commands (
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES gift_order_drafts(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('PURCHASE','CANCEL')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id,key_hash)
);
CREATE TABLE gift_cancellations (
    order_id UUID PRIMARY KEY REFERENCES gift_purchase_work(order_id),
    state VARCHAR(30) NOT NULL CHECK (state IN ('PENDING','COMPLETED','REVIEW_REQUIRED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    return_transaction_id UUID UNIQUE,
    CHECK (state <> 'COMPLETED' OR (completed_at IS NOT NULL AND return_transaction_id IS NOT NULL))
);
CREATE INDEX gift_cancellations_due_idx ON gift_cancellations(state,next_attempt_at,order_id);
