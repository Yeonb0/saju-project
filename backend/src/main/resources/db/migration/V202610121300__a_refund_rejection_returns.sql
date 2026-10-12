ALTER TABLE top_up_refunds ADD COLUMN resolution VARCHAR(30)
    CHECK (resolution IS NULL OR resolution = 'CANCEL_REJECTED');
CREATE TABLE wallet_cash_refund_returns (
    refund_id UUID PRIMARY KEY REFERENCES top_up_refunds(id),
    original_transaction_id UUID NOT NULL UNIQUE REFERENCES wallet_transactions(id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES wallet_transactions(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
