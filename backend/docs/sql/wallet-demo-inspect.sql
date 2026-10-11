\pset pager off
BEGIN READ ONLY;
SELECT current_database(), current_user;

SELECT user_id, paid_balance, bonus_balance, version
FROM wallets WHERE user_id = :'demo_user'::uuid;

SELECT id, balance_type, granted_amount, remaining_amount, expires_at
FROM wallet_lots WHERE wallet_user_id = :'demo_user'::uuid;

SELECT COALESCE(SUM(remaining_amount), 0) AS currently_spendable
FROM wallet_lots WHERE wallet_user_id = :'demo_user'::uuid
AND created_at <= CURRENT_TIMESTAMP AND expires_at > CURRENT_TIMESTAMP;

SELECT id, type, total_amount, reference_id, reversal_of_id
FROM wallet_transactions WHERE wallet_user_id = :'demo_user'::uuid
ORDER BY created_at, id;

SELECT t.type, t.total_amount, SUM(l.amount) AS allocated
FROM wallet_transactions t JOIN wallet_transaction_lines l ON l.transaction_id = t.id
WHERE t.wallet_user_id = :'demo_user'::uuid
GROUP BY t.id, t.type, t.total_amount ORDER BY t.type;

SELECT id, status, wallet_transaction_id, recovery_attempts, recovery_last_error_code
FROM reading_purchases WHERE buyer_user_id = :'demo_user'::uuid;
COMMIT;
