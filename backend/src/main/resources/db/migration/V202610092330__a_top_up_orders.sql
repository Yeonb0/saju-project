CREATE TABLE top_up_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    amount_krw INTEGER NOT NULL CHECK (amount_krw > 0),
    paid_shells INTEGER NOT NULL CHECK (paid_shells > 0),
    bonus_shells INTEGER NOT NULL CHECK (bonus_shells >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN
        ('CREATED', 'PAYMENT_PENDING', 'PAID', 'CREDITED', 'FAILED', 'CANCELED', 'REFUNDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, key_hash)
);
