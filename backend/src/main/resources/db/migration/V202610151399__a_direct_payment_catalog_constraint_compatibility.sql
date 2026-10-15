-- Must precede the KRW direct-payment catalog transition.  The earlier
-- constraint assumed every non-top-up product was paid in shells.
ALTER TABLE products DROP CONSTRAINT products_top_up_grants;
ALTER TABLE products ADD CONSTRAINT products_top_up_grants CHECK (
    (category = 'TOP_UP' AND price_currency = 'KRW'
        AND paid_shell_amount IS NOT NULL AND paid_shell_amount > 0
        AND bonus_shell_amount IS NOT NULL AND bonus_shell_amount >= 0
        AND CAST(paid_shell_amount AS BIGINT) + bonus_shell_amount <= 2147483647)
    OR (category <> 'TOP_UP' AND price_currency IN ('TURTLE_SHELL', 'KRW')
        AND paid_shell_amount IS NULL AND bonus_shell_amount IS NULL)
);
