-- Legacy wallet-flow tests exercise historical shell-funded purchases.  Keep
-- their fixture catalog separate from the production KRW direct-payment sale
-- catalog that Flyway installs before every test context.
UPDATE products
SET price_currency = 'TURTLE_SHELL',
    price_amount = CASE product_option
        WHEN 'READING_ONLY' THEN 10
        WHEN 'READING_WITH_TALISMAN' THEN 15
        ELSE price_amount
    END,
    regular_price_amount = CASE
        WHEN product_option = 'READING_WITH_TALISMAN' THEN 20
        ELSE NULL
    END
WHERE category = 'READING';

UPDATE products
SET price_currency = 'TURTLE_SHELL',
    price_amount = CASE code
        WHEN 'GIFT_SUNEUNG_TALISMAN_ONLY' THEN 10
        WHEN 'GIFT_SUNEUNG_READING_WITH_TALISMAN' THEN 15
        ELSE price_amount
    END
WHERE category = 'GIFT';

UPDATE products
SET price_currency = 'TURTLE_SHELL', price_amount = 5
WHERE category = 'TALISMAN_ADDON';
