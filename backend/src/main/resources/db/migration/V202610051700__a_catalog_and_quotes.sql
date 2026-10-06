CREATE TABLE products (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(30) NOT NULL CHECK (category IN ('TOP_UP', 'READING', 'GIFT', 'TALISMAN_ADDON')),
    price_currency VARCHAR(20) NOT NULL CHECK (price_currency IN ('KRW', 'TURTLE_SHELL')),
    price_amount INTEGER NOT NULL CHECK (price_amount > 0),
    active BOOLEAN NOT NULL,
    sale_start_at TIMESTAMP WITH TIME ZONE,
    sale_end_at TIMESTAMP WITH TIME ZONE,
    catalog_version VARCHAR(100) NOT NULL,
    CHECK (sale_start_at IS NULL OR sale_end_at IS NULL OR sale_end_at > sale_start_at)
);

CREATE TABLE purchase_quotes (
    id UUID PRIMARY KEY,
    requester_user_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    product_code VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('TOP_UP', 'READING', 'GIFT', 'TALISMAN_ADDON')),
    price_currency VARCHAR(20) NOT NULL CHECK (price_currency IN ('KRW', 'TURTLE_SHELL')),
    price_amount INTEGER NOT NULL CHECK (price_amount > 0),
    active BOOLEAN NOT NULL,
    sale_start_at TIMESTAMP WITH TIME ZONE,
    sale_end_at TIMESTAMP WITH TIME ZONE,
    catalog_version VARCHAR(100) NOT NULL,
    context_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    purchase_id UUID UNIQUE,
    CHECK (expires_at = created_at + INTERVAL '30' MINUTE)
);

CREATE INDEX purchase_quotes_owner_idx ON purchase_quotes(requester_user_id, created_at);
