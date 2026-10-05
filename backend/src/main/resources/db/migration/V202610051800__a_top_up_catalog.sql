ALTER TABLE products ADD COLUMN paid_shell_amount INTEGER;
ALTER TABLE products ADD COLUMN bonus_shell_amount INTEGER;
ALTER TABLE products ADD CONSTRAINT products_top_up_grants CHECK (
    (category = 'TOP_UP' AND price_currency = 'KRW'
        AND paid_shell_amount IS NOT NULL AND paid_shell_amount > 0
        AND bonus_shell_amount IS NOT NULL AND bonus_shell_amount >= 0
        AND CAST(paid_shell_amount AS BIGINT) + bonus_shell_amount <= 2147483647)
    OR (category <> 'TOP_UP' AND price_currency = 'TURTLE_SHELL'
        AND paid_shell_amount IS NULL AND bonus_shell_amount IS NULL)
);

-- Product values are confirmed; activation waits for payment and fulfillment readiness.
INSERT INTO products (id, code, category, price_currency, price_amount, paid_shell_amount,
    bonus_shell_amount, active, catalog_version) VALUES
('a0000000-0000-0000-0000-000000000010', 'TURTLE_SHELL_10', 'TOP_UP', 'KRW', 1000, 10, 0, FALSE, '2026-10-04'),
('a0000000-0000-0000-0000-000000000030', 'TURTLE_SHELL_30', 'TOP_UP', 'KRW', 3000, 30, 3, FALSE, '2026-10-04'),
('a0000000-0000-0000-0000-000000000050', 'TURTLE_SHELL_50', 'TOP_UP', 'KRW', 5000, 50, 6, FALSE, '2026-10-04'),
('a0000000-0000-0000-0000-000000000100', 'TURTLE_SHELL_100', 'TOP_UP', 'KRW', 10000, 100, 14, FALSE, '2026-10-04'),
('a0000000-0000-0000-0000-000000000300', 'TURTLE_SHELL_300', 'TOP_UP', 'KRW', 30000, 300, 46, FALSE, '2026-10-04'),
('a0000000-0000-0000-0000-000000000500', 'TURTLE_SHELL_500', 'TOP_UP', 'KRW', 50000, 500, 80, FALSE, '2026-10-04');
