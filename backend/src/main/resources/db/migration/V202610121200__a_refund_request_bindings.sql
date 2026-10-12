CREATE TABLE top_up_refund_requests (
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES top_up_refunds(order_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, key_hash)
);
