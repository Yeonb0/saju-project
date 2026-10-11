# Backend A / FE compatibility review

## 2026-10-09 update

The Q-34 funded quote POST/GET and wallet balance HTTP adapters are now implemented,
and Q-35 adds owned personId basic-saju input and a reproducible OpenAPI export.
See `FE_REQUESTS_20261009.md` and `../../docs/openapi/api-v1.json` for the current contract.
Authentication/person adapters, real top-up crediting and wallet purchase writes remain unfinished.
The review below records the earlier PR #14 scope, not the current endpoint inventory.

Reviewed 2026-10-06 against `main` (47e2055) and FE work branch `boyeon` (39c750d).
This change provides internal services and persistence, not production HTTP endpoints.
It must not be treated as a completed FE API integration.

## Compatible behavior

- Existing `ApiResponse(data, traceId)` and `ApiError(code, message, traceId, fieldErrors)`
  match FE envelope validation. This PR does not change those envelopes.
- Top-up amounts match the current six products, including 500 paid + 80 bonus = 580 credits.
- Quote validity is 30 minutes. Owned expired quotes return `QUOTE_EXPIRED`;
  missing/other-user quotes return `RESOURCE_NOT_FOUND`.
- Quote funding returns current usable balance, shortage, recommended product code,
  and `balanceAfter` (null when funds are insufficient). It does not debit a wallet.
- Recommendations use only active, in-sale top-ups and include bonus credits.
  Registered top-ups remain inactive until payment and fulfillment are ready.
- Existing payment state names remain unchanged. No approval, crediting, or PG adapter
  is introduced; FE must still wait for CREDITED to show top-up completion.

## Required HTTP / FE adapter work (not implemented here)

| Internal value | FE port value / remaining work |
|---|---|
| `Product.Category.READING` | API category `FORTUNE`; do not serialize the internal enum directly |
| `TopUpProduct.product.code/price/active` | Flatten to FE top-up fields; paid/bonus/credited values are available |
| `WalletBalance.paidBalance/bonusBalance/balance()` | Explicitly supply `currency: TURTLE_SHELL` and total `balance` |
| `FundingView.quote.id/productSnapshot/expiresAt` | Flatten to `quoteId`, `productCode`, `price`, `expiresAt`; `productName` is still absent |
| `WalletTransaction.PURCHASE/EXPIRY` | API `SPEND/EXPIRATION`; retain internal ledger names |
| Internal quote context hash | Derive server-side from verified `personId/counterpartPersonId` selection; never accept a trusted client hash |

The catalog currently has no fortune type, product option, display name, included-content,
or list-price metadata. Those fields and approved fortune product data are needed before
implementing the FE FortunePort. A simple quote/product record is not a public DTO.

The HTTP controller must use the authenticated server user, authorize person selection,
map internal values to the agreed DTO, and wrap responses. B authentication/session/CSRF
integration is intentionally unchanged. The default security configuration still denies
business endpoints. No client-supplied user ID is introduced.

Gift batch quotes, personal-data input rules, claim, PG confirmation, and refunds are not
implemented or decided by this PR. Out-of-range single-product recommendations remain
null with a positive shortage; no multi-top-up policy is assumed.

## Merge scope and verification

- Changed code is under backend A's catalog/wallet/common areas. No frontend,
  authentication, person, fortune generation, or talisman implementation files change.
- User foreign keys are intentionally deferred until B provides the actual user schema.
  Wallet-line foreign keys already enforce same-owner transactions and lots.
- Three timestamped A migrations add catalog/quote/wallet tables and inactive top-up data.
  B must review future migrations referencing these tables; this PR does not modify B tables.
- A non-checkout merge-tree check against `origin/boyeon` completed without conflicts.
- Local tests use H2 PostgreSQL mode. GitHub Backend CI runs the same migrations and tests
  on PostgreSQL 17; PostgreSQL CI must pass before merging this PR.
- This review checks static FE models and backend tests, not end-to-end browser requests.
  Real FE adapters and public business endpoints do not exist yet.
