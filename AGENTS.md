# Sajuppugi development and operations

## Required reference

Before development, debugging, review, or deployment preparation, read the relevant sections of `docs/OPERATIONS_CHECKLIST.md`. It contains the user's required operational checks and beginner-friendly explanations. Read section 6 when implementation affects production behavior; use the specific subsections relevant to the change instead of repeating the entire checklist for every small edit.

The document's 2026-10-09 implementation assessment is historical. Check the current branch, implementation, API/OpenAPI, and team decisions before treating an item as implemented or missing. Operational recommendations do not override confirmed team policies. Update the maintained checklist when a material implementation or verification result changes its status.

## Apply during implementation

- Identify the affected modules and tables, authorization boundary, transaction boundaries, external calls, and failure/recovery states before changing financial or shared behavior. Explain these to the user in plain Korean.
- Keep PostgreSQL as the project's existing database. Validate risky persistence and concurrency behavior against PostgreSQL; H2 compatibility mode alone is not equivalent evidence.
- Wallet, payment, purchase, refund, and gift changes must cover duplicate requests, concurrent operations, timeouts, and process interruption where relevant. Confirm ledger allocations, balances, and order states agree.
- Check ownership on the server. Authentication success alone does not grant access to every person, order, reading, or private asset.
- Do not hold wallet locks or long DB transactions while awaiting external providers. Persist unfinished work and provide bounded retry, reconciliation, or compensation paths.
- Report payment/compensation completion only after the corresponding durable state is confirmed. Unknown provider outcomes need lookup/reconciliation, not a new purchase by default.
- Add new Flyway migrations instead of editing shared applied migrations. Check deployment compatibility and recovery when schema changes affect existing data.
- Keep mock implementations out of production. Keep secrets and raw personal/payment/link data out of client configuration, logs, analytics, and error reports.
- Use the repository's existing module boundaries and public application services/ports. Read additional instructions in the affected subtree, including `frontend/AGENTS.md`.
- Perform interruption, fault injection, load, and restore exercises in local or staging environments. For a production investigation, begin with read-only evidence and follow the user's authorized scope.

## Relevant section 6 checks

- 6-1: sessions, authorization, CSRF, return destinations.
- 6-2: PostgreSQL transactions, ledger consistency, duplicate/concurrent writes, connection pools.
- 6-3: provider timeouts, unknown outcomes, retry limits, duplicate payment grants.
- 6-4: outbox/worker recovery, leases, stale completions, bounded retries.
- 6-5: gift tokens, expiration, reissue, private assets and signed URLs.
- 6-6: environments, migrations, deployment compatibility and rollback limits.
- 6-7: database/files/key restoration and post-restore reconciliation.
- 6-8: request/business identifiers, alerts, administrator authorization and audit.
- 6-9: data minimization, encryption, retention and deletion.
- 6-10: peak load, latency, queues, database and provider capacity.

## Completion reporting

- During ongoing A-part development, make local feature-sized commits after verification, using explicit paths and preserving unrelated edits. The user requested intermediate commits; this does not authorize push, PR creation, or deployment. Keep credentials and generated build/test output out of commits.

Briefly state what changed, which checklist items applied, what was actually verified, and what remains unverified or blocked. Use measured evidence instead of inferring success from HTTP 200, green CI, or test counts. Do not claim a live deployment or production capacity was checked without observing it. Record important completed checks and remaining implementation gaps in the maintained document with date and code/environment context, without sensitive values.

These instructions do not add a new permission gate. Continue authorized work, scale verification to risk, and preserve existing changes.
