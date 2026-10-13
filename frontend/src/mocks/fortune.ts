// 운세 구매 포트의 가짜 구현 — 개발 서버 · Vercel 미리보기 전용 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 서버 규칙을 흉내 낸다: 견적은 서버가 잔액 · 부족분 · 구매 후 잔액을 계산해 준다, 같은 Idempotency-Key 는 같은 결과,
// 같은 키에 다른 본문은 409 IDEMPOTENCY_KEY_REUSED, 잔액이 모자라면 409 INSUFFICIENT_BALANCE, 만료 견적은 409 QUOTE_EXPIRED.
// 구매 상태 조회(getPurchaseStatus)는 API_SPEC 8장 상태표(Q-38 답, PR #20)의 조합을 따르고, 기록이 없으면 null 이다 (PURCHASE-STATUS-WAIT 해제).
// 아래 상품 · 금액 · 이름은 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다 (P-03 값을 옮기지 않는다).
import { ApiError } from "@/lib/api/errors";
import type {
  FortunePort,
  FortuneProduct,
  FortuneQuote,
  FortuneSelection,
  FortuneType,
  ReadingPurchaseResult,
  ReadingPurchaseState,
} from "@/lib/ports/fortune";
import type { TopUpProduct } from "@/lib/ports/topUp";
import { FIXTURE_TOP_UP_PRODUCTS } from "./topUp";
import { createFakeWallet, type FakeWallet } from "./wallet";

export const FAKE_FORTUNE_SCENARIOS = [
  "fulfilled", // 구매 즉시 FULFILLED
  "generation_failed", // 생성 실패 — FAILED · 차감 되돌림 (READING_GENERATION_FAILED)
  "processing_409", // 첫 구매 요청은 409 IDEMPOTENCY_REQUEST_PROCESSING, 같은 키 재요청은 FULFILLED
  "quote_expired", // 첫 견적으로 구매하면 409 QUOTE_EXPIRED, 새 견적으로는 FULFILLED
  "generating", // 구매 응답은 GENERATING · WAIT (차감함), 그 견적으로 getPurchaseStatus 하면 그때 결과를 만들고 FULFILLED · VIEW_READING
  "review_required", // FAILED · REVIEW_REQUIRED · CONTACT_SUPPORT — 차감을 되돌리지 않는다
  "refunded", // REFUNDED · COMPLETED · RETRY_PURCHASE — 차감을 되돌린다
] as const;

export type FakeFortuneScenario = (typeof FAKE_FORTUNE_SCENARIOS)[number];

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 가격은 가짜 잔액(src/mocks/wallet.ts)보다 크게 둬서
// 처음에는 잔액 부족 → 충전 → 복귀 경로를 지나게 한다. 숫자를 실제와 다르게 둔 것은 화면 하드코딩을 테스트에서 드러내려는 것이다.
const fixture = (
  fortuneType: Exclude<FortuneType, "UNKNOWN">,
  option: FortuneProduct["option"],
  amount: number,
): FortuneProduct => ({
  code: `FIXTURE_${fortuneType}_${option}`,
  fortuneType,
  option,
  price: { currency: "TURTLE_SHELL", amount },
  active: true,
  saleEndsAt: null,
});

const FIXTURE_PRODUCTS: readonly FortuneProduct[] = [
  // 수능운은 부적 포함 단일 상품 (P-03) — 구성만 맞춘다
  fixture("SUNEUNG", "READING_WITH_TALISMAN", 13),
  ...(
    ["OVERALL", "LOVE", "WEALTH", "COMPATIBILITY", "SINSAL"] as const
  ).flatMap((type) => [
    fixture(type, "READING_ONLY", 9),
    fixture(type, "READING_WITH_TALISMAN", 12),
  ]),
];

const TRACE = "fixture-trace";
// 픽스처일 뿐이며 실제 규칙과 무관하다 — 견적 유효 시간 (Q-07 의 30분과 같게)
const QUOTE_TTL_MS = 30 * 60 * 1000;

type StoredQuote = {
  quote: FortuneQuote;
  selection: FortuneSelection;
  expired: boolean;
};
type StoredPurchase = {
  body: string;
  quoteId: string;
  // generating 시나리오: 구매 응답은 GENERATING, getPurchaseStatus 에서 완료로 바꾼다
  pending: { product: FortuneProduct; selection: FortuneSelection } | null;
  result: ReadingPurchaseResult;
};

export function createFakeFortunePort(
  options: {
    scenario?: FakeFortuneScenario;
    wallet?: FakeWallet;
    // 추천 충전 상품 계산에 쓰는 가짜 충전 상품 목록 (기본: src/mocks/topUp.ts 픽스처)
    topUpProducts?: readonly TopUpProduct[];
    now?: () => number;
    // 구매가 끝나면 결과(reading)를 만든다 — 가짜 결과 포트와 이어 줄 때 넘긴다
    onFulfilled?: (
      product: FortuneProduct,
      selection: FortuneSelection,
    ) => string;
  } = {},
): FortunePort {
  const scenario = options.scenario ?? "fulfilled";
  const wallet = options.wallet ?? createFakeWallet();
  const now = options.now ?? Date.now;
  const topUpProducts = options.topUpProducts ?? FIXTURE_TOP_UP_PRODUCTS;
  const quotes = new Map<string, StoredQuote>();
  const purchases = new Map<string, StoredPurchase>();
  const processingSent = new Set<string>();
  let firstQuoteUsed = false;

  const fail = (status: number, code: string) =>
    new ApiError({ status, code, traceId: TRACE });

  function product(code: string) {
    const found = FIXTURE_PRODUCTS.find((p) => p.code === code);
    if (!found) throw fail(404, "PRODUCT_NOT_FOUND");
    if (!found.active) throw fail(422, "PRODUCT_NOT_AVAILABLE");
    return found;
  }

  // 가짜 서버가 BE-A 규칙으로 계산한다 (P-06 · MOCK-PORT, FE_COMPATIBILITY.md · QuoteFundingService):
  // 부족할 때만, 활성 충전 상품 중 creditedAmount(보너스 포함) >= 부족분인 것 가운데
  // 가격이 가장 낮은 것(같으면 code 사전순 앞). 후보가 없으면 null — 부족한데 null 일 수 있다.
  function recommendTopUp(shortage: number): string | null {
    if (shortage === 0) return null;
    const candidates = topUpProducts
      .filter((t) => t.active && t.creditedAmount >= shortage)
      .sort(
        (a, b) => a.price.amount - b.price.amount || (a.code < b.code ? -1 : 1),
      );
    return candidates[0]?.code ?? null;
  }

  function makeQuote(selection: FortuneSelection): FortuneQuote {
    const p = product(selection.productCode);
    // 서버 규칙 흉내 — 상품과 다른 운세 종류는 잘못된 요청 (ADAPTER-HTTP 의 selection.fortuneType)
    if (selection.fortuneType !== p.fortuneType) {
      throw fail(422, "INVALID_REQUEST");
    }
    const balance = wallet.balance();
    const shortage = Math.max(0, p.price.amount - balance);
    return {
      quoteId: crypto.randomUUID(),
      productCode: p.code,
      // 픽스처 이름 — 실제 문구가 아니다
      productName: `FIXTURE ${p.code}`,
      charged: p.price,
      walletBalance: balance,
      // 가짜 서버가 계산해 준다 — 화면은 이 값만 표시한다 (P-09)
      balanceAfter: shortage === 0 ? balance - p.price.amount : null,
      shortage,
      recommendedTopUp: recommendTopUp(shortage),
      expiresAt: new Date(now() + QUOTE_TTL_MS).toISOString(),
    };
  }

  function findQuote(quoteId: string) {
    const stored = quotes.get(quoteId);
    // 근거: FE_COMPATIBILITY.md "missing/other-user quotes return RESOURCE_NOT_FOUND"
    if (!stored) throw fail(404, "RESOURCE_NOT_FOUND");
    if (stored.expired || Date.parse(stored.quote.expiresAt) <= now()) {
      stored.expired = true;
      throw fail(409, "QUOTE_EXPIRED");
    }
    return stored;
  }

  return {
    async listProducts(fortuneType) {
      return FIXTURE_PRODUCTS.filter((p) => p.fortuneType === fortuneType);
    },

    async createQuote(selection) {
      const quote = makeQuote(selection);
      quotes.set(quote.quoteId, { quote, selection, expired: false });
      return quote;
    },

    async getQuote(quoteId) {
      const stored = findQuote(quoteId);
      // 재확인 때 잔액이 바뀌었으면 서버처럼 새 값으로 다시 계산한다 (같은 quoteId 유지)
      const fresh = makeQuote(stored.selection);
      stored.quote = {
        ...fresh,
        quoteId: stored.quote.quoteId,
        expiresAt: stored.quote.expiresAt,
      };
      return stored.quote;
    },

    async purchase(input, idempotencyKey) {
      const body = JSON.stringify(input);
      const done = purchases.get(idempotencyKey);
      if (done) {
        if (done.body !== body) throw fail(409, "IDEMPOTENCY_KEY_REUSED");
        return done.result;
      }

      const stored = findQuote(input.quoteId);
      if (scenario === "quote_expired" && !firstQuoteUsed) {
        firstQuoteUsed = true;
        stored.expired = true;
        throw fail(409, "QUOTE_EXPIRED");
      }
      if (stored.quote.productCode !== input.selection.productCode) {
        throw fail(422, "INVALID_REQUEST");
      }
      const p = product(input.selection.productCode);
      if (
        scenario === "processing_409" &&
        !processingSent.has(idempotencyKey)
      ) {
        processingSent.add(idempotencyKey);
        throw fail(409, "IDEMPOTENCY_REQUEST_PROCESSING");
      }
      const taken = wallet.debit(p.price.amount);
      if (!taken) {
        throw fail(409, "INSUFFICIENT_BALANCE");
      }

      // API_SPEC 8장 상태표 조합. readingId 는 FULFILLED 에서만 준다
      const reverted =
        scenario === "generation_failed" || scenario === "refunded";
      // 되돌림은 잔액으로만 드러난다 — 구매 응답에 환급 필드는 없다 (Q-38)
      if (reverted) wallet.credit(taken.paid, taken.bonus);
      const base = {
        purchaseId: crypto.randomUUID(),
        balance: wallet.balance(),
      };
      const zero = { currency: p.price.currency, amount: 0 };
      let result: ReadingPurchaseResult;
      if (scenario === "generation_failed") {
        result = {
          ...base,
          readingId: null,
          status: "FAILED",
          compensationStatus: "NOT_REQUIRED",
          nextAction: "RETRY_PURCHASE",
          charged: zero,
        };
      } else if (scenario === "review_required") {
        result = {
          ...base,
          readingId: null,
          status: "FAILED",
          compensationStatus: "REVIEW_REQUIRED",
          nextAction: "CONTACT_SUPPORT",
          charged: p.price,
        };
      } else if (scenario === "refunded") {
        result = {
          ...base,
          readingId: null,
          status: "REFUNDED",
          compensationStatus: "COMPLETED",
          nextAction: "RETRY_PURCHASE",
          charged: zero,
        };
      } else if (scenario === "generating") {
        result = {
          ...base,
          readingId: null,
          status: "GENERATING",
          compensationStatus: "NOT_REQUIRED",
          nextAction: "WAIT",
          charged: p.price,
        };
      } else {
        result = {
          ...base,
          readingId:
            options.onFulfilled?.(p, input.selection) ?? crypto.randomUUID(),
          status: "FULFILLED",
          compensationStatus: "NOT_REQUIRED",
          nextAction: "VIEW_READING",
          charged: p.price,
        };
      }
      purchases.set(idempotencyKey, {
        body,
        quoteId: input.quoteId,
        pending:
          scenario === "generating"
            ? { product: p, selection: input.selection }
            : null,
        result,
      });
      return result;
    },

    // 그 견적으로 기록된 구매의 상태. 기록이 없으면 null (409 로 끝난 요청은 기록이 없다). 견적 만료와 무관하다 — findQuote 를 거치지 않는다
    async getPurchaseStatus(quoteId): Promise<ReadingPurchaseState | null> {
      for (const record of purchases.values()) {
        if (record.quoteId !== quoteId) continue;
        if (record.pending) {
          // generating: 조회 때 결과를 만든다. 한 번 완료되면 이후 조회도 같은 값
          const { product: p, selection } = record.pending;
          record.pending = null;
          record.result = {
            ...record.result,
            readingId:
              options.onFulfilled?.(p, selection) ?? crypto.randomUUID(),
            status: "FULFILLED",
            compensationStatus: "NOT_REQUIRED",
            nextAction: "VIEW_READING",
          };
        }
        const { purchaseId, readingId, status } = record.result;
        const { compensationStatus, nextAction } = record.result;
        return {
          purchaseId,
          readingId,
          status,
          compensationStatus,
          nextAction,
        };
      }
      return null;
    },
  };
}
