-- Enable the KRW Suneung reading product for catalog display and quote issuance.
UPDATE products
SET active = TRUE
WHERE code = 'SUNEUNG_READING_WITH_TALISMAN'
  AND category = 'READING'
  AND price_currency = 'KRW';
