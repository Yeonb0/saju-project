-- First release: one CSAT recipient, paid in turtle shells. Sales remain disabled.
-- Unpublished draft moved after the current main migrations.
INSERT INTO products (id, code, category, price_currency, price_amount, active, catalog_version,
                      product_name, fortune_type)
VALUES ('b7000000-0000-0000-0000-000000000001', 'GIFT_SUNEUNG_TALISMAN_ONLY', 'GIFT',
        'TURTLE_SHELL', 10, FALSE, '2026-10-11-gift-v1', '수능 선물 부적', 'SUNEUNG'),
       ('b7000000-0000-0000-0000-000000000002', 'GIFT_SUNEUNG_READING_WITH_TALISMAN', 'GIFT',
        'TURTLE_SHELL', 15, FALSE, '2026-10-11-gift-v1', '수능 선물 사주 + 부적', 'SUNEUNG');
