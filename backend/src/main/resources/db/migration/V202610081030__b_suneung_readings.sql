CREATE TABLE reading_purchases (
    id UUID PRIMARY KEY,
    buyer_user_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    quote_id UUID NOT NULL UNIQUE REFERENCES purchase_quotes(id),
    subject_person_id UUID NOT NULL,
    wallet_transaction_id UUID UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'DEBITED', 'GENERATING', 'FULFILLED', 'FAILED', 'REFUNDED')),
    reading_id UUID UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fulfilled_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX reading_purchases_owner_created_idx ON reading_purchases(buyer_user_id, created_at DESC);

CREATE TABLE readings (
    id UUID PRIMARY KEY,
    reading_result_id UUID NOT NULL REFERENCES reading_results(id),
    owner_user_id UUID NOT NULL,
    purchase_id UUID NOT NULL UNIQUE REFERENCES reading_purchases(id),
    fortune_type VARCHAR(30) NOT NULL,
    product_option VARCHAR(30) NOT NULL,
    subject_display_name VARCHAR(100) NOT NULL,
    event_date DATE,
    public_snapshot TEXT NOT NULL,
    calculation_version VARCHAR(100) NOT NULL,
    generation_version VARCHAR(100) NOT NULL,
    content_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX readings_owner_created_idx ON readings(owner_user_id, created_at DESC);
