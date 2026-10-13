CREATE TABLE admin_refund_cases (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES top_up_orders(id),
    customer_user_id UUID NOT NULL,
    operator_id UUID NOT NULL REFERENCES users(id),
    executed_by UUID REFERENCES users(id),
    support_reference VARCHAR(80) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    UNIQUE (operator_id, key_hash)
);
ALTER TABLE top_up_refunds ADD COLUMN admin_case_id UUID UNIQUE REFERENCES admin_refund_cases(id);
ALTER TABLE top_up_refunds ADD COLUMN reclaimed_paid INTEGER;
ALTER TABLE top_up_refunds ADD COLUMN reclaimed_bonus INTEGER;
ALTER TABLE top_up_refunds ADD CONSTRAINT remaining_refund_snapshot CHECK (
    (admin_case_id IS NULL AND reclaimed_paid IS NULL AND reclaimed_bonus IS NULL)
    OR (admin_case_id IS NOT NULL AND reclaimed_paid IS NOT NULL AND reclaimed_bonus IS NOT NULL
        AND reclaimed_paid > 0 AND reclaimed_bonus >= 0)
);
CREATE TABLE admin_refund_execution_keys (
    operator_id UUID NOT NULL REFERENCES users(id),
    key_hash VARCHAR(64) NOT NULL,
    case_id UUID NOT NULL REFERENCES admin_refund_cases(id),
    expected_amount INTEGER NOT NULL CHECK (expected_amount > 0),
    expected_paid INTEGER NOT NULL CHECK (expected_paid > 0),
    expected_bonus INTEGER NOT NULL CHECK (expected_bonus >= 0),
    PRIMARY KEY (operator_id, key_hash)
);
