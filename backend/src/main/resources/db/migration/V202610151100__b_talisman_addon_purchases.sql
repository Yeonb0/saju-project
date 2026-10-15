CREATE TABLE talisman_addon_purchases (
    id UUID PRIMARY KEY,
    reading_id UUID NOT NULL UNIQUE REFERENCES readings(id),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    reading_only_product_code VARCHAR(100) NOT NULL,
    bundled_product_code VARCHAR(100) NOT NULL,
    quoted_amount INTEGER NOT NULL CHECK (quoted_amount > 0),
    quote_id UUID UNIQUE REFERENCES purchase_quotes(id),
    wallet_transaction_id UUID UNIQUE REFERENCES wallet_transactions(id),
    state VARCHAR(30) NOT NULL CHECK (state IN ('QUOTED','DEBITING','DEBITED','PENDING_TALISMAN','READY','FAILED','REVIEW_REQUIRED')),
    talisman_id UUID UNIQUE REFERENCES talismans(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX talisman_addon_purchases_owner_created_idx
    ON talisman_addon_purchases(owner_user_id, created_at DESC, id DESC);
