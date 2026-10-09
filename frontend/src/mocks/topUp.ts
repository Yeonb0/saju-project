// 충전 포트의 가짜 구현 — 개발 서버 · Vercel 미리보기 전용 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 서버 규칙을 흉내 낸다: 같은 Idempotency-Key 는 같은 주문, 승인은 주문 금액과 PG 복귀 금액 비교,
// 같은 주문을 다시 승인하면 처음 결과, PAID → CREDITED 지연, 오류 시나리오.
// 아래 상품 · 금액 · 잔액은 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다 (P-02 · P-03 값을 옮기지 않는다).
import { ApiError, ApiNetworkError } from "@/lib/api/errors";
import type {
  PaymentReturn,
  TopUpOrder,
  TopUpOrderState,
  TopUpOrderStatus,
  TopUpPort,
  TopUpProduct,
} from "@/lib/ports/topUp";
import { createFakeWallet, type FakeWallet } from "./wallet";

export const FAKE_TOP_UP_SCENARIOS = [
  "credited", // 승인 응답에서 바로 CREDITED
  "paid_then_credited", // 승인은 PAID(처리 중), 몇 번 조회 뒤 CREDITED
  "stuck_paid", // 계속 PAID — 30초 조회 초과 경로
  "confirm_lost", // 서버는 지급했지만 승인 응답이 끊김 (네트워크 오류)
  "processing_409", // 승인이 409 IDEMPOTENCY_REQUEST_PROCESSING, 조회 뒤 CREDITED
  "rejected", // 422 PAYMENT_REJECTED, 주문 FAILED
] as const;

export type FakeTopUpScenario = (typeof FAKE_TOP_UP_SCENARIOS)[number];

// 구성만 실제 상품 목록(P-02)과 맞춘다: 판매 상품 6개, 첫 상품은 보너스 0, 마지막에 비활성 1개.
// 금액 · 수량은 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다 (P-02 · P-03 값을 옮기지 않는다).
// 숫자를 일부러 실제와 다르게 둔 것은, 화면이 값을 하드코딩하면 테스트에서 바로 드러나게 하려는 것이다.
// 비활성 상품은 화면이 고를 수 없게 막는지 확인하는 용도다.
// creditedAmount 는 가짜 서버 데이터라 유료 + 보너스를 미리 계산해 적는다.
export const FIXTURE_TOP_UP_PRODUCTS: readonly TopUpProduct[] = [
  {
    code: "FIXTURE_TOP_UP_A",
    price: { currency: "KRW", amount: 1111 },
    paidAmount: 11,
    bonusAmount: 0,
    creditedAmount: 11,
    active: true,
  },
  {
    code: "FIXTURE_TOP_UP_B",
    price: { currency: "KRW", amount: 2222 },
    paidAmount: 22,
    bonusAmount: 2,
    creditedAmount: 24,
    active: true,
  },
  {
    code: "FIXTURE_TOP_UP_C",
    price: { currency: "KRW", amount: 3333 },
    paidAmount: 33,
    bonusAmount: 3,
    creditedAmount: 36,
    active: true,
  },
  {
    code: "FIXTURE_TOP_UP_D",
    price: { currency: "KRW", amount: 4444 },
    paidAmount: 44,
    bonusAmount: 4,
    creditedAmount: 48,
    active: true,
  },
  {
    code: "FIXTURE_TOP_UP_E",
    price: { currency: "KRW", amount: 5555 },
    paidAmount: 55,
    bonusAmount: 5,
    creditedAmount: 60,
    active: true,
  },
  {
    code: "FIXTURE_TOP_UP_F",
    price: { currency: "KRW", amount: 6666 },
    paidAmount: 66,
    bonusAmount: 6,
    creditedAmount: 72,
    active: true,
  },
  {
    code: "FIXTURE_TOP_UP_INACTIVE",
    price: { currency: "KRW", amount: 7777 },
    paidAmount: 77,
    bonusAmount: 7,
    creditedAmount: 84,
    active: false,
  },
];

const TRACE = "fixture-trace";

type StoredOrder = {
  order: TopUpOrder;
  product: TopUpProduct;
  status: TopUpOrderStatus;
  polls: number;
};

export function createFakeTopUpPort(
  options: {
    scenario?: FakeTopUpScenario;
    creditAfterPolls?: number;
    // 가짜 운세 구매와 잔액을 나눠 쓸 때 넘긴다 (src/lib/ports/index.ts)
    wallet?: FakeWallet;
  } = {},
): TopUpPort {
  const scenario = options.scenario ?? "credited";
  const creditAfterPolls = options.creditAfterPolls ?? 2;
  const orders = new Map<string, StoredOrder>();
  const orderIdByKey = new Map<string, string>();
  const wallet = options.wallet ?? createFakeWallet();

  const fail = (status: number, code: string) =>
    new ApiError({ status, code, traceId: TRACE });

  function credit(stored: StoredOrder) {
    if (stored.status === "CREDITED") return;
    stored.status = "CREDITED";
    wallet.credit(stored.product.creditedAmount);
  }

  function stateOf(stored: StoredOrder): TopUpOrderState {
    return {
      orderId: stored.order.orderId,
      status: stored.status,
      processing: stored.status === "PAID",
      walletBalance: stored.status === "CREDITED" ? wallet.balance() : null,
    };
  }

  function find(orderId: string) {
    const stored = orders.get(orderId);
    if (!stored) throw fail(404, "PAYMENT_ORDER_NOT_FOUND");
    return stored;
  }

  return {
    async getWallet() {
      return { currency: "TURTLE_SHELL", balance: wallet.balance() };
    },

    async listTopUpProducts() {
      return FIXTURE_TOP_UP_PRODUCTS;
    },

    async createOrder(input, idempotencyKey) {
      const existing = orderIdByKey.get(idempotencyKey);
      if (existing) {
        const stored = find(existing);
        if (stored.product.code !== input.productCode) {
          throw fail(409, "IDEMPOTENCY_KEY_REUSED");
        }
        return stored.order;
      }
      const product = FIXTURE_TOP_UP_PRODUCTS.find(
        (p) => p.code === input.productCode,
      );
      if (!product) throw fail(404, "PRODUCT_NOT_FOUND");
      if (!product.active) throw fail(422, "PRODUCT_NOT_AVAILABLE");
      const orderId = crypto.randomUUID();
      const order: TopUpOrder = {
        orderId,
        // 픽스처 주문 이름 — 실제 문구가 아니다
        orderName: `FIXTURE ${product.code}`,
        amount: product.price,
        status: "PAYMENT_PENDING",
      };
      orders.set(orderId, {
        order,
        product,
        status: "PAYMENT_PENDING",
        polls: 0,
      });
      orderIdByKey.set(idempotencyKey, orderId);
      return order;
    },

    async confirm(ret: PaymentReturn) {
      const stored = find(ret.orderId);
      if (ret.paymentKey === "") throw fail(400, "INVALID_REQUEST");
      if (String(stored.order.amount.amount) !== ret.amount) {
        throw fail(400, "PAYMENT_AMOUNT_MISMATCH");
      }
      // 이미 처리한 주문은 처음 결과를 다시 준다 (같은 키 재요청)
      if (stored.status !== "PAYMENT_PENDING") return stateOf(stored);

      switch (scenario) {
        case "credited":
          credit(stored);
          return stateOf(stored);
        case "paid_then_credited":
        case "stuck_paid":
          stored.status = "PAID";
          return stateOf(stored);
        case "confirm_lost":
          credit(stored);
          throw new ApiNetworkError();
        case "processing_409":
          stored.status = "PAID";
          throw fail(409, "IDEMPOTENCY_REQUEST_PROCESSING");
        case "rejected":
          stored.status = "FAILED";
          throw fail(422, "PAYMENT_REJECTED");
      }
    },

    async getOrder(orderId) {
      const stored = find(orderId);
      if (stored.status === "PAID" && scenario !== "stuck_paid") {
        stored.polls += 1;
        if (stored.polls >= creditAfterPolls) credit(stored);
      }
      return stateOf(stored);
    },
  };
}
