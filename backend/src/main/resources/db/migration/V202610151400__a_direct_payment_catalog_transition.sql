-- New sales are settled in KRW at the PG.  Historical wallet/top-up rows remain
-- untouched for audit and any already-open refund case; no new top-up product is sold.
UPDATE products
SET active = FALSE,
    catalog_version = '2026-10-15-direct-payment-retired'
WHERE category = 'TOP_UP';

UPDATE products
SET price_currency = 'KRW',
    price_amount = CASE product_option
        WHEN 'READING_ONLY' THEN 1800
        WHEN 'READING_WITH_TALISMAN' THEN 2500
        ELSE price_amount
    END,
    regular_price_amount = NULL,
    catalog_version = '2026-10-15-direct-payment-v1'
WHERE category = 'READING'
  AND fortune_type <> 'SUNEUNG';

UPDATE products
SET price_currency = 'KRW',
    price_amount = 3000,
    regular_price_amount = NULL,
    catalog_version = '2026-10-15-direct-payment-v1'
WHERE code = 'SUNEUNG_READING_WITH_TALISMAN';

UPDATE products
SET price_currency = 'KRW',
    price_amount = 1000,
    regular_price_amount = NULL,
    catalog_version = '2026-10-15-direct-payment-v1'
WHERE category = 'TALISMAN_ADDON';

UPDATE products
SET price_currency = 'KRW',
    price_amount = CASE code
        WHEN 'GIFT_SUNEUNG_TALISMAN_ONLY' THEN 1000
        WHEN 'GIFT_SUNEUNG_READING_WITH_TALISMAN' THEN 3000
        ELSE price_amount
    END,
    regular_price_amount = NULL,
    catalog_version = '2026-10-15-direct-payment-v1'
WHERE category = 'GIFT';

CREATE TABLE direct_payment_batches (
    id UUID PRIMARY KEY,
    buyer_user_id UUID NOT NULL,
    quote_id UUID NOT NULL UNIQUE REFERENCES purchase_quotes(id),
    product_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_amount_krw INTEGER NOT NULL CHECK (unit_amount_krw > 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    total_amount_krw INTEGER NOT NULL CHECK (total_amount_krw > 0 AND total_amount_krw < 50000),
    key_hash CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELED', 'REVIEW_REQUIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (buyer_user_id, key_hash),
    CHECK (total_amount_krw = unit_amount_krw * quantity)
);

CREATE TABLE direct_payment_confirmation_intents (
    batch_id UUID PRIMARY KEY REFERENCES direct_payment_batches(id) ON DELETE CASCADE,
    payment_key_hash CHAR(64) NOT NULL,
    amount_krw INTEGER NOT NULL CHECK (amount_krw > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE direct_payment_consumptions (
    batch_id UUID PRIMARY KEY REFERENCES direct_payment_batches(id),
    consumer_type VARCHAR(30) NOT NULL CHECK (consumer_type IN ('READING', 'GIFT_BATCH', 'TALISMAN_ADDON')),
    consumer_id UUID NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (consumer_type, consumer_id)
);
