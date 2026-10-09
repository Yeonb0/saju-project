# A-part wallet purchase implementation

## Delivery order

1. Internal wallet debit, immutable ledger, durable replay and full purchase compensation.
2. Purchase recovery: interruption after debit, stale generation, failed compensation, reconciliation and alerts.
3. Top-up order, Toss test approval/lookup, exactly-once grant and uncertain outcome recovery.
4. Gift purchase/delivery, administrative refunds and ledger reconciliation.
5. Authenticated FE integration, staging fault/load/restore exercises and release review.

Step 1 is implemented. Step 2 now includes bounded recovery of confirmed FAILED purchases,
with a separately gated scheduler. It does not finish the whole purchase workflow.

## Activation boundary

`WalletPurchaseService` implements the existing BE-B `WalletPurchasePort`. There is no standalone
HTTP debit/refund endpoint. It is disabled by default (`WALLET_PURCHASES_ENABLED=false`).
The new integration tests explicitly enable it. Do not enable production writes until recovery,
real subject/session integration and A/B review are complete.

Existing BE-B services derive an internal key from purchase ID, not the original client key.
Wallet command replay therefore does not implement the HTTP API's complete key/body contract.
That request contract still needs a durable application-level implementation.

## Transaction boundary and locking

Each command starts a short independent transaction (REQUIRES_NEW, 10-second transaction timeout).
Lock order is wallet row, then that owner's lots ordered by ID. No Liner/PG/network call is made
inside these methods. Future grants/expiry/admin commands must use the same wallet-lock protocol.
The JDBC transaction timeout does not replace operational deadlock/lock-wait monitoring.

Debit changes lots, appends PURCHASE and allocation lines, updates wallet projection,
stores one receipt per quote and stores a hashed-key command receipt in one commit.
Constraints retain one debit per quote and one compensation per original transaction.
Failed commands roll back; successful receipts have no automatic TTL in this implementation.
Retention/erasure and deployment migration review remain required before release.

The current price is not substituted for the quoted price. A new debit checks quote ownership,
expiry and current product availability, then charges the quote snapshot. Catalog availability is
read at validation time; concurrent administrator sale changes are not globally serialized.

## Replay and compensation semantics

- Same wallet/key and same request fingerprint: original transaction and original balance snapshot.
- Same wallet/key and a different quote or compensation reason: IDEMPOTENCY_KEY_REUSED.
- Same quote with a different key: original debit, no second allocation/ledger append.
- Replay of a successful debit is allowed after quote expiry. It is a receipt, not permission to generate new content.
- Compensation restores the exact original allocation with one positive REFUND linked by reversal_of_id.
- Different compensation keys still reuse the original reversal.
- Original lots/types/grant dates/expiry are preserved. Expired lots remain unspendable after restoration.
  Any extension or replacement policy at the expiry boundary needs explicit product approval.
- Returned balances are committed receipt snapshots, not necessarily current balances.
  Query WalletQueryUseCase for the current spendable balance.
- Compensation is a trusted internal command, not cash cancellation or a user-authorized refund API.
  The caller must establish that content was not fulfilled before invoking it.

## Durability versus recovery

The ledger and command receipts survive service instance replacement. If commit succeeded but its
response was lost, a caller can safely replay the same debit or compensation.
However, BE-B's purchase state and wallet debit are separate commits. No worker automatically
recovers CREATED purchases with a committed debit or DEBITED/GENERATING purchases yet.
FAILED-only compensation is now implemented: it locks
the failed purchase, verifies the original debit's owner/quote, reuses a committed reversal and updates
REFUNDED. Retry attempts/time/error are durable, with bounded exponential backoff and an exhausted
FAILED state for operator review. The scheduler requires both WALLET_PURCHASES_ENABLED and
WALLET_RECOVERY_ENABLED; both are false by default. Wallet writes must remain disabled until the
remaining gaps are closed. A killed process, stale worker completion, real generation failure and operational
refund completion are not proven merely by the wallet transaction tests.

Recovery lock order is failed purchase -> wallet -> lots. Wallet compensation uses an independent
transaction, so an active recovery needs a second DB connection. Pool size and worker concurrency
must account for that before activation. It never waits for Liner/PG under these locks.
If wallet commit succeeds but the outer purchase-state transaction rolls back, the next attempt reuses
the existing reversal. If the DB cannot persist the retry metadata, FAILED itself remains recoverable.
Failures that exhaust retries are logged by purchase ID/error code; an alert integration and authorized
operator retry API are not implemented. Do not resolve these by directly editing ledger rows.

## Verification

`WalletPurchasePersistenceTest` uses real commits, a synthetic owner with opening ledger entries,
and two executor threads/independent DB transactions. Cases cover debit order, snapshot price,
ownership/expiry/sale availability, unusable lots, same-key mismatch, different-key duplicate quote,
exact allocation reversal, expired-lot preservation, late-write rollback, retry after failed reversal,
new service instance replay and concurrent debit/compensation.

Run against the isolated PostgreSQL database using backend README instructions. Never run these
tests against development/production DBs. Fault injection throws before transaction commit; it is
not an operating-system kill or a full purchase recovery exercise. Final observed results are
recorded in `docs/OPERATIONS_CHECKLIST.md`.
