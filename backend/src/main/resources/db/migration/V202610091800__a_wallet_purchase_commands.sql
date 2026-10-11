CREATE TABLE wallet_purchase_debits (
    quote_id UUID PRIMARY KEY REFERENCES purchase_quotes(id),
    wallet_user_id UUID NOT NULL,
    transaction_id UUID NOT NULL UNIQUE,
    paid_balance INTEGER NOT NULL CHECK (paid_balance >= 0),
    bonus_balance INTEGER NOT NULL CHECK (bonus_balance >= 0),
    FOREIGN KEY (transaction_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id)
);

CREATE TABLE wallet_purchase_compensations (
    original_transaction_id UUID PRIMARY KEY,
    wallet_user_id UUID NOT NULL,
    transaction_id UUID NOT NULL UNIQUE,
    paid_balance INTEGER NOT NULL CHECK (paid_balance >= 0),
    bonus_balance INTEGER NOT NULL CHECK (bonus_balance >= 0),
    FOREIGN KEY (original_transaction_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id),
    FOREIGN KEY (transaction_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id),
    CHECK (original_transaction_id <> transaction_id)
);

CREATE TABLE wallet_purchase_commands (
    wallet_user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    transaction_id UUID NOT NULL,
    paid_balance INTEGER NOT NULL CHECK (paid_balance >= 0),
    bonus_balance INTEGER NOT NULL CHECK (bonus_balance >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (wallet_user_id, key_hash),
    FOREIGN KEY (transaction_id, wallet_user_id) REFERENCES wallet_transactions(id, wallet_user_id)
);
