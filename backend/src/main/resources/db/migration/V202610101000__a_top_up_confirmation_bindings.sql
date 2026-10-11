CREATE TABLE top_up_confirmation_intents (
    order_id UUID PRIMARY KEY REFERENCES top_up_orders(id),
    payment_key_hash VARCHAR(64) NOT NULL UNIQUE,
    amount_krw INTEGER NOT NULL CHECK (amount_krw > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE top_up_confirmation_requests (
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES top_up_confirmation_intents(order_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, key_hash)
);

CREATE INDEX top_up_confirmation_requests_order_idx ON top_up_confirmation_requests(order_id);
