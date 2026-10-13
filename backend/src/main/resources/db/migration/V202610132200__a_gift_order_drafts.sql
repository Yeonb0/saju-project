CREATE TABLE gift_order_drafts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    quote_id UUID NOT NULL UNIQUE REFERENCES purchase_quotes(id),
    request_hash VARCHAR(64) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    shell_amount INTEGER NOT NULL CHECK (shell_amount > 0),
    recipient_phone_masked VARCHAR(30) NOT NULL,
    private_payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status = 'CREATED'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (expires_at > created_at)
);
CREATE INDEX gift_order_drafts_owner_idx ON gift_order_drafts(user_id, created_at, id);
CREATE TABLE gift_order_commands (
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES gift_order_drafts(id),
    PRIMARY KEY (user_id, key_hash)
);
