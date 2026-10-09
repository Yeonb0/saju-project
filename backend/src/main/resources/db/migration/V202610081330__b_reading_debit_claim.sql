ALTER TABLE reading_purchases ADD COLUMN debit_claim_token UUID;
ALTER TABLE reading_purchases ADD COLUMN debit_claimed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reading_purchases ADD CONSTRAINT reading_purchase_debit_claim_ck CHECK (
    (debit_claim_token IS NULL AND debit_claimed_at IS NULL)
    OR (debit_claim_token IS NOT NULL AND debit_claimed_at IS NOT NULL)
);

CREATE INDEX reading_purchases_debit_claim_idx
    ON reading_purchases(status, debit_claimed_at);
