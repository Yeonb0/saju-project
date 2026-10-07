CREATE TABLE wallets (
    user_id UUID PRIMARY KEY,
    paid_balance INTEGER NOT NULL DEFAULT 0 CHECK (paid_balance >= 0),
    bonus_balance INTEGER NOT NULL DEFAULT 0 CHECK (bonus_balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE wallet_lots (
    id UUID PRIMARY KEY,
    wallet_user_id UUID NOT NULL REFERENCES wallets(user_id),
    balance_type VARCHAR(10) NOT NULL CHECK (balance_type IN ('PAID', 'BONUS')),
    granted_amount INTEGER NOT NULL CHECK (granted_amount > 0),
    remaining_amount INTEGER NOT NULL CHECK (remaining_amount >= 0 AND remaining_amount <= granted_amount),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (id, wallet_user_id),
    CHECK (expires_at > created_at)
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    wallet_user_id UUID NOT NULL REFERENCES wallets(user_id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('TOP_UP', 'PURCHASE', 'REFUND', 'EXPIRY', 'ADJUSTMENT')),
    total_amount INTEGER NOT NULL CHECK (total_amount <> 0),
    reference_id UUID NOT NULL,
    reversal_of_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (id, wallet_user_id),
    FOREIGN KEY (reversal_of_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id),
    CHECK (reversal_of_id IS NULL OR reversal_of_id <> id),
    CHECK ((type = 'TOP_UP' AND total_amount > 0)
        OR (type IN ('PURCHASE', 'EXPIRY') AND total_amount < 0)
        OR type IN ('REFUND', 'ADJUSTMENT'))
);

CREATE TABLE wallet_transaction_lines (
    transaction_id UUID NOT NULL,
    lot_id UUID NOT NULL,
    wallet_user_id UUID NOT NULL,
    amount INTEGER NOT NULL CHECK (amount <> 0),
    PRIMARY KEY (transaction_id, lot_id),
    FOREIGN KEY (transaction_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id),
    FOREIGN KEY (lot_id, wallet_user_id) REFERENCES wallet_lots(id, wallet_user_id)
);

CREATE INDEX wallet_lots_owner_idx ON wallet_lots(wallet_user_id, expires_at);
CREATE INDEX wallet_transactions_page_idx ON wallet_transactions(wallet_user_id, created_at, id);
