// 직접결제 포트의 가짜 구현 — 개발 서버 · Vercel 미리보기 전용 (DIRECT-PAY, MOCK-PORT, docs/FRONTEND.md 1-2).
// 초안 — PR #49 병합 · OPENAPI-FE-SYNC 후 생성 타입과 대조. 서버 규칙을 흉내 낸다: 주문 금액은 견적 charged.amount 를 가짜 서버가 옮겨 담고(P-09),
// 승인 금액이 다르면 409, 같은 키는 같은 결과, 같은 키에 다른 본문은 409 IDEMPOTENCY_KEY_REUSED, PAID 가 아니면 생성하지 않는다.
// 아래 금액 · 상품명 · ID 는 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다 (실제 가격을 옮기지 않는다).
// 등껍질 지갑을 건드리지 않는다 — FakeWallet 의 debit · credit 을 부르지 않는다.
import { ApiError, ApiNetworkError } from "@/lib/api/errors";
import type {
  DirectPaymentOrder,
  DirectPaymentPort,
  DirectReadingFulfillment,
} from "@/lib/ports/directPayment";
import { type FakeFortunePort, sameSelection } from "./fortune";

export const FAKE_DIRECT_PAYMENT_SCENARIOS = [
  "paid", // 승인 즉시 PAID
  "pending_then_paid", // 승인 응답은 PAYMENT_PENDING, 조회 paidAfterPolls 번째에 PAID
  "stuck_pending", // 계속 PAYMENT_PENDING
  "confirm_lost", // 서버는 PAID 로 바꿨는데 승인 응답이 끊김 (ApiNetworkError) — 조회하면 PAID (I-05)
  "review_required", // REVIEW_REQUIRED — 새 주문으로 덮지 않는다 (BE 전달본 10/16)
  "rejected", // 승인 거절 — FAILED
] as const;

export type FakeDirectPaymentScenario =
  (typeof FAKE_DIRECT_PAYMENT_SCENARIOS)[number];

const TRACE = "fixture-trace";

type StoredOrder = {
  order: DirectPaymentOrder;
  quoteId: string;
  paymentKey: string | null;
  // pending_then_paid: 승인 뒤 조회 횟수
  polls: number;
  // 생성에 쓴 멱등 키 — 다른 키의 생성은 409 PAYMENT_CONFIRMATION_CONFLICT
  fulfilledBy: string | null;
};

type StoredFulfillment = {
  body: string;
  result: DirectReadingFulfillment;
};

export function createFakeDirectPaymentPort(options: {
  fortune: FakeFortunePort;
  scenario?: FakeDirectPaymentScenario;
  paidAfterPolls?: number;
  // 서버 규칙 흉내 — 이 금액 이상이면 400 PAYMENT_BATCH_LIMIT_EXCEEDED (전달본 "5만 원 미만"). 실제 한도 상수가 아니다
  batchLimitKrw?: number;
}): DirectPaymentPort {
  const { fortune } = options;
  const scenario = options.scenario ?? "paid";
  const paidAfterPolls = options.paidAfterPolls ?? 2;
  const batchLimitKrw = options.batchLimitKrw ?? 50_000;
  const orders = new Map<string, StoredOrder>();
  const orderKeys = new Map<string, { quoteId: string; orderId: string }>();
  const fulfillments = new Map<string, StoredFulfillment>();

  const fail = (status: number, code: string) =>
    new ApiError({ status, code, traceId: TRACE });

  function find(orderId: string) {
    const stored = orders.get(orderId);
    if (!stored) throw fail(404, "RESOURCE_NOT_FOUND");
    return stored;
  }

  function setStatus(
    stored: StoredOrder,
    status: DirectPaymentOrder["status"],
  ) {
    stored.order = { ...stored.order, status };
  }

  return {
    async createOrder(input, idempotencyKey) {
      const done = orderKeys.get(idempotencyKey);
      if (done) {
        if (done.quoteId !== input.quoteId) {
          throw fail(409, "IDEMPOTENCY_KEY_REUSED");
        }
        return find(done.orderId).order;
      }
      const { quote } = fortune.direct.activeQuote(input.quoteId);
      if (quote.charged.currency !== "KRW") {
        throw fail(422, "PRODUCT_NOT_AVAILABLE");
      }
      // 금액은 서버(가짜)가 견적에서 옮겨 담는다 — 수량 1 (P-09)
      const amount = quote.charged.amount * 1;
      if (amount >= batchLimitKrw) {
        throw fail(400, "PAYMENT_BATCH_LIMIT_EXCEEDED");
      }
      const order: DirectPaymentOrder = {
        orderId: crypto.randomUUID(),
        productCode: quote.productCode,
        // 픽스처 이름 — 실제 문구가 아니다
        orderName: quote.productName ?? `FIXTURE ${quote.productCode}`,
        amount: { currency: "KRW", amount },
        status: "PAYMENT_PENDING",
      };
      orders.set(order.orderId, {
        order,
        quoteId: input.quoteId,
        paymentKey: null,
        polls: 0,
        fulfilledBy: null,
      });
      orderKeys.set(idempotencyKey, {
        quoteId: input.quoteId,
        orderId: order.orderId,
      });
      return order;
    },

    async confirm(ret) {
      const stored = find(ret.orderId);
      if (!/^\d+$/.test(ret.amount)) throw fail(422, "INVALID_REQUEST");
      // 승인 금액은 서버 주문 금액과 대조한다 (P-08). 다르면 주문은 PAYMENT_PENDING 그대로
      if (Number(ret.amount) !== stored.order.amount.amount) {
        throw fail(409, "PAYMENT_AMOUNT_MISMATCH");
      }
      if (stored.paymentKey !== null) {
        if (stored.paymentKey !== ret.paymentKey) {
          throw fail(409, "PAYMENT_CONFIRMATION_CONFLICT");
        }
        // 승인 재전송(같은 키, CONFIRM-KEY) — 지금 상태를 그대로 돌려준다
        return stored.order;
      }
      stored.paymentKey = ret.paymentKey;
      if (scenario === "paid") setStatus(stored, "PAID");
      else if (scenario === "review_required")
        setStatus(stored, "REVIEW_REQUIRED");
      else if (scenario === "rejected") setStatus(stored, "FAILED");
      else if (scenario === "confirm_lost") {
        setStatus(stored, "PAID");
        throw new ApiNetworkError();
      }
      // pending_then_paid · stuck_pending 은 PAYMENT_PENDING 그대로
      return stored.order;
    },

    async getOrder(orderId) {
      const stored = find(orderId);
      if (
        scenario === "pending_then_paid" &&
        stored.paymentKey !== null &&
        stored.order.status === "PAYMENT_PENDING"
      ) {
        stored.polls += 1;
        if (stored.polls >= paidAfterPolls) setStatus(stored, "PAID");
      }
      return stored.order;
    },

    async fulfillReading(input, idempotencyKey) {
      const body = JSON.stringify(input);
      const done = fulfillments.get(idempotencyKey);
      if (done) {
        if (done.body !== body) throw fail(409, "IDEMPOTENCY_KEY_REUSED");
        return done.result;
      }
      const stored = find(input.orderId);
      // PAID 일 때만 생성한다 — 승인 전 · 거절 · 검토 중 주문은 생성하지 않는다
      if (stored.order.status !== "PAID") {
        throw fail(409, "PAYMENT_ORDER_NOT_CONFIRMABLE");
      }
      if (input.quoteId !== stored.quoteId) throw fail(422, "INVALID_REQUEST");
      const snapshot = fortune.direct.quoteSnapshot(input.quoteId);
      if (!sameSelection(snapshot.selection, input.selection)) {
        throw fail(422, "INVALID_REQUEST");
      }
      if (stored.fulfilledBy !== null) {
        throw fail(409, "PAYMENT_CONFIRMATION_CONFLICT");
      }
      const made = fortune.direct.fulfill(
        input.quoteId,
        snapshot.selection,
        idempotencyKey,
      );
      stored.fulfilledBy = idempotencyKey;
      const result: DirectReadingFulfillment = {
        purchaseId: made.purchaseId,
        readingId: made.readingId,
        status: made.status,
      };
      fulfillments.set(idempotencyKey, { body, result });
      return result;
    },
  };
}
