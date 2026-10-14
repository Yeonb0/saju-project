ALTER TABLE gift_order_drafts ALTER COLUMN recipient_phone_masked DROP NOT NULL;

ALTER TABLE gift_purchase_tokens ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE gift_purchase_tokens ADD COLUMN token_ciphertext TEXT;
ALTER TABLE gift_purchase_tokens ADD COLUMN issued_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE gift_purchase_tokens ADD COLUMN reveal_until TIMESTAMP WITH TIME ZONE;

CREATE TABLE gift_link_commands (
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES gift_purchase_work(order_id),
    token_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, key_hash)
);

CREATE INDEX gift_link_commands_order_idx ON gift_link_commands(order_id, created_at);

COMMENT ON COLUMN gift_purchase_tokens.token_ciphertext IS
    'Short-lived encrypted token for buyer response recovery; token hash is the durable credential.';
