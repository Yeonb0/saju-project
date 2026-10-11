-- Correct current display names without changing prices, grants, activation, or quote snapshots.
UPDATE products
SET product_name = '등껍질 ' || CAST(paid_shell_amount AS VARCHAR) || '개 충전'
WHERE category = 'TOP_UP'
  AND code IN ('TURTLE_SHELL_10', 'TURTLE_SHELL_30', 'TURTLE_SHELL_50',
               'TURTLE_SHELL_100', 'TURTLE_SHELL_300', 'TURTLE_SHELL_500')
  AND product_name = '조개 ' || CAST(paid_shell_amount AS VARCHAR) || '개 충전';
