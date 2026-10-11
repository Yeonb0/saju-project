ALTER TABLE products ADD COLUMN product_name VARCHAR(200);
ALTER TABLE products ADD COLUMN regular_price_amount INTEGER;
ALTER TABLE products ADD COLUMN fortune_type VARCHAR(30);
ALTER TABLE products ADD COLUMN product_option VARCHAR(30);
ALTER TABLE purchase_quotes ADD COLUMN product_name VARCHAR(200);
ALTER TABLE purchase_quotes ADD COLUMN regular_price_amount INTEGER;
ALTER TABLE purchase_quotes ADD COLUMN fortune_type VARCHAR(30);
ALTER TABLE purchase_quotes ADD COLUMN product_option VARCHAR(30);

ALTER TABLE products ADD CONSTRAINT products_regular_price CHECK (
    regular_price_amount IS NULL OR regular_price_amount >= price_amount);
ALTER TABLE purchase_quotes ADD CONSTRAINT quotes_regular_price CHECK (
    regular_price_amount IS NULL OR regular_price_amount >= price_amount);
ALTER TABLE products ADD CONSTRAINT products_fortune_type CHECK (
    fortune_type IS NULL OR fortune_type IN ('OVERALL', 'LOVE', 'WEALTH', 'COMPATIBILITY', 'SINSAL', 'SUNEUNG'));
ALTER TABLE products ADD CONSTRAINT products_product_option CHECK (
    product_option IS NULL OR product_option IN ('READING_ONLY', 'READING_WITH_TALISMAN'));

-- Register the missing CSAT catalog entry without enabling purchases.
INSERT INTO products (id, code, category, price_currency, price_amount, active, catalog_version)
SELECT 'b6000000-0000-0000-0000-000000000002', 'SUNEUNG_READING_WITH_TALISMAN',
       'READING', 'TURTLE_SHELL', 15, FALSE, '2026-10-11'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = 'SUNEUNG_READING_WITH_TALISMAN');

UPDATE products SET fortune_type = 'OVERALL', product_name = '종합운'
WHERE code IN ('OVERALL_READING_ONLY', 'OVERALL_READING_WITH_TALISMAN');
UPDATE products SET fortune_type = 'LOVE', product_name = '애정운'
WHERE code IN ('LOVE_READING_ONLY', 'LOVE_READING_WITH_TALISMAN');
UPDATE products SET fortune_type = 'WEALTH', product_name = '재물운'
WHERE code IN ('WEALTH_READING_ONLY', 'WEALTH_READING_WITH_TALISMAN');
UPDATE products SET fortune_type = 'COMPATIBILITY', product_name = '궁합'
WHERE code IN ('COMPATIBILITY_READING_ONLY', 'COMPATIBILITY_READING_WITH_TALISMAN');
UPDATE products SET fortune_type = 'SINSAL', product_name = '신살'
WHERE code IN ('SINSAL_READING_ONLY', 'SINSAL_READING_WITH_TALISMAN');
UPDATE products SET fortune_type = 'SUNEUNG', product_name = '수능운'
WHERE code = 'SUNEUNG_READING_WITH_TALISMAN';
UPDATE products SET product_option = 'READING_ONLY'
WHERE code IN ('OVERALL_READING_ONLY', 'LOVE_READING_ONLY', 'WEALTH_READING_ONLY',
              'COMPATIBILITY_READING_ONLY', 'SINSAL_READING_ONLY');
UPDATE products SET product_option = 'READING_WITH_TALISMAN',
    product_name = product_name || ' + 부적', regular_price_amount = 20
WHERE code IN ('OVERALL_READING_WITH_TALISMAN', 'LOVE_READING_WITH_TALISMAN',
              'WEALTH_READING_WITH_TALISMAN', 'COMPATIBILITY_READING_WITH_TALISMAN',
              'SINSAL_READING_WITH_TALISMAN', 'SUNEUNG_READING_WITH_TALISMAN');
UPDATE products SET product_name = '조개 ' || CAST(paid_shell_amount AS VARCHAR) || '개 충전'
WHERE code IN ('TURTLE_SHELL_10', 'TURTLE_SHELL_30', 'TURTLE_SHELL_50',
              'TURTLE_SHELL_100', 'TURTLE_SHELL_300', 'TURTLE_SHELL_500');

-- Historical quotes have no historical display evidence: keep their nullable metadata empty.
