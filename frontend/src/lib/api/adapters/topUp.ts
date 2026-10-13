// 충전 진짜 adapter — GET /api/v1/wallet · GET /api/v1/products?category=TOP_UP · POST /api/v1/top-up-orders ·
// POST /api/v1/top-up-orders/{orderId}/confirm · GET /api/v1/top-up-orders/{orderId}. 근거: PG-3, Q-34 4 (OpenAPI WalletResponse · OrderResponse),
// Q-17 해결 · Q-28 해결 (PR #19: 승인 API, 주문 응답 processing · walletBalance · grantedShellAmount · nextAction · failureCode, 상품 목록 API),
// ADAPTER-HTTP (createApiClient 로 요청하고 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다. 생성 타입 enum 을 우리가 모르는 값도 받는 필드는
// satisfies 대조에서 Omit/Pick 으로 뺀다 — 키 존재 · 모양은 zod 가 검사한다), I-05 (주문 생성 키 = 구매 의도당 하나),
// CONFIRM-KEY (승인 키 = 주문 ID, createOrderBoundCommand), TOPUP-DONE (완료는 status CREDITED 만, 예정 지급량은 포트에 넣지 않는다 — 지급 수량은 CREDITED 뒤 서버 값만),
// Q-22 (정가 regularPrice 는 표시 방식 미정 — 검사만 하고 포트에 넣지 않는다), Q-33 (실패 사유 failureCode 문구 미정 — 포트에 넣지 않는다).
// TODO(PG-2 세션 adapter): ports/index.ts 연결은 CSRF 출처인 세션 adapter 가 생긴 뒤에 한다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import {
  createKeyedCommand,
  createOrderBoundCommand,
} from "@/lib/api/idempotency";
import {
  type CashRefund,
  type TopUpOrder,
  type TopUpOrderState,
  type TopUpPort,
  type TopUpProduct,
  toCashRefundNextAction,
  toCashRefundState,
  toTopUpNextAction,
  toTopUpOrderStatus,
  type Wallet,
} from "@/lib/ports/topUp";
import type { components, paths } from "@/types/api";

const WALLET_PATH = "/api/v1/wallet" satisfies keyof paths;
const ORDERS_PATH = "/api/v1/top-up-orders" satisfies keyof paths;
const ORDER_BY_ID_PATH =
  "/api/v1/top-up-orders/{orderId}" satisfies keyof paths;
const CONFIRM_PATH =
  "/api/v1/top-up-orders/{orderId}/confirm" satisfies keyof paths;
const PRODUCTS_PATH = "/api/v1/products" satisfies keyof paths;
const REFUND_PATH =
  "/api/v1/top-up-orders/{orderId}/refund" satisfies keyof paths;

const count = z.number().int().min(0);
const text = z.string().min(1);

// 포트에 넣지 않는 값도 계약 위반은 막는다
const walletSchema = z.object({
  currency: text,
  balance: count,
  paidBalance: count,
  bonusBalance: count,
});

const orderSchema = z
  .object({
    orderId: z.uuid(),
    productCode: text,
    orderName: text,
    amount: count,
    currency: text,
    // 모르는 상태는 UNKNOWN 으로 받는다 — 계약 위반 아님
    status: z.string(),
    paidShellAmount: count,
    bonusShellAmount: count,
    creditedShellAmount: count,
    // Q-17 해결 (PR #19): 키는 항상 있어야 한다. null 허용 값도 키가 없으면 계약 위반
    processing: z.boolean(),
    walletBalance: count.nullable(),
    grantedShellAmount: count.nullable(),
    // 모르는 값은 UNKNOWN 으로 받는다 — 계약 위반 아님
    nextAction: z.string(),
    failureCode: text.nullable(),
  })
  // TOPUP-DONE: CREDITED 인데 지급량이나 지급 당시 잔액이 없으면 완료를 믿을 수 없다
  .refine(
    (order) =>
      order.status !== "CREDITED" ||
      (order.grantedShellAmount !== null && order.walletBalance !== null),
  );

type OrderData = z.infer<typeof orderSchema>;

// 충전 상품 한 항목. 정가는 검사만 한다 (Q-22) — 통화는 생성 타입이 enum 이지만 모르는 통화도 계약 위반으로 치지 않는다
const productSchema = z.object({
  code: text,
  category: z.literal("TOP_UP"),
  productName: text,
  price: z.object({ currency: z.literal("KRW"), amount: count.min(1) }),
  // 충전 상품은 지급량이 있다 — null 이면 계약 위반
  paidAmount: count,
  bonusAmount: count,
  creditedAmount: count,
  active: z.boolean(),
  regularPrice: z.object({ currency: text, amount: count }).nullable(),
  fortuneType: z.null(),
  option: z.null(),
  saleEndsAt: z
    .string()
    .refine((value) => !Number.isNaN(Date.parse(value)))
    .nullable(),
});
const productsSchema = z.object({ items: z.array(productSchema) });
const refundSchema = z.object({
  refundId: z.uuid(),
  orderId: z.uuid(),
  state: text,
  amount: count,
  nextAction: text,
  completedAt: z
    .string()
    .refine((value) => !Number.isNaN(Date.parse(value)))
    .nullable(),
});

function toCashRefund(data: z.infer<typeof refundSchema>): CashRefund {
  const wire = {
    refundId: data.refundId,
    orderId: data.orderId,
    amount: data.amount,
    completedAt: data.completedAt,
  } satisfies Pick<
    components["schemas"]["CashRefundResponse"],
    "refundId" | "orderId" | "amount" | "completedAt"
  >;
  return {
    ...wire,
    state: toCashRefundState(data.state),
    nextAction: toCashRefundNextAction(data.nextAction),
  };
}

// 승인 · 조회 응답을 포트 모델로 바꾼다. null 을 0 으로 바꾸지 않는다
function toOrderState(data: OrderData): TopUpOrderState {
  // status · nextAction 은 모르는 값도 받으므로 enum 대조에서 뺀다. 이름 · 모양은 나머지로 대조한다
  const wire = {
    orderId: data.orderId,
    processing: data.processing,
    walletBalance: data.walletBalance,
    grantedShellAmount: data.grantedShellAmount,
    failureCode: data.failureCode,
  } satisfies Pick<
    components["schemas"]["OrderResponse"],
    | "orderId"
    | "processing"
    | "walletBalance"
    | "grantedShellAmount"
    | "failureCode"
  >;
  // failureCode 는 포트에 넣지 않는다 (TODO(Q-33))
  return {
    orderId: wire.orderId,
    status: toTopUpOrderStatus(data.status),
    processing: wire.processing,
    walletBalance: wire.walletBalance,
    grantedShellAmount: wire.grantedShellAmount,
    nextAction: toTopUpNextAction(data.nextAction),
  };
}

export function createTopUpAdapter(client: ApiClient): TopUpPort {
  return {
    async getWallet(): Promise<Wallet> {
      const { status, data } = await client.request({
        method: "GET",
        path: WALLET_PATH,
      });
      const parsed = walletSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "지갑 응답 모양이 다르다");
      }
      // 필드 이름이 생성 타입과 달라지면 typecheck 가 깨진다 — 객체 리터럴이라 초과 속성 검사가 된다
      const wire = {
        currency: parsed.data.currency,
        balance: parsed.data.balance,
        paidBalance: parsed.data.paidBalance,
        bonusBalance: parsed.data.bonusBalance,
      } satisfies components["schemas"]["WalletResponse"];
      // paidBalance · bonusBalance 는 화면이 아직 안 써서 포트에 넣지 않는다
      return { currency: wire.currency, balance: wire.balance };
    },

    async createOrder(input, idempotencyKey): Promise<TopUpOrder> {
      const body = {
        productCode: input.productCode,
      } satisfies components["schemas"]["CreateRequest"];
      const { status, data } = await client.request({
        method: "POST",
        path: ORDERS_PATH,
        command: createKeyedCommand(idempotencyKey, body),
      });
      const parsed = orderSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "충전 주문 응답 모양이 다르다");
      }
      // status 는 모르는 값도 받으므로 생성 타입의 enum 대조에서 빼고 따로 둔다
      const wire = {
        orderId: parsed.data.orderId,
        productCode: parsed.data.productCode,
        orderName: parsed.data.orderName,
        amount: parsed.data.amount,
        currency: parsed.data.currency,
        paidShellAmount: parsed.data.paidShellAmount,
        bonusShellAmount: parsed.data.bonusShellAmount,
        creditedShellAmount: parsed.data.creditedShellAmount,
      } satisfies Pick<
        components["schemas"]["OrderResponse"],
        | "orderId"
        | "productCode"
        | "orderName"
        | "amount"
        | "currency"
        | "paidShellAmount"
        | "bonusShellAmount"
        | "creditedShellAmount"
      >;
      // 예정 지급량(paid · bonus · credited ShellAmount)은 포트에 넣지 않는다 (TOPUP-DONE, BE-A 10/10)
      return {
        orderId: wire.orderId,
        orderName: wire.orderName,
        amount: { currency: wire.currency, amount: wire.amount },
        status: toTopUpOrderStatus(parsed.data.status),
      };
    },

    async listTopUpProducts(): Promise<readonly TopUpProduct[]> {
      // 로그인 · CSRF 가 필요 없는 GET. 쿼리는 문자열로 붙인다
      const { status, data } = await client.request({
        method: "GET",
        path: `${PRODUCTS_PATH}?category=TOP_UP`,
      });
      const parsed = productsSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "충전 상품 목록 응답 모양이 다르다");
      }
      return parsed.data.items.map((item) => {
        // 정가(regularPrice)는 enum 통화 대조에서 뺀다 (Q-22)
        const wire = {
          code: item.code,
          category: item.category,
          productName: item.productName,
          price: item.price,
          paidAmount: item.paidAmount,
          bonusAmount: item.bonusAmount,
          creditedAmount: item.creditedAmount,
          active: item.active,
          fortuneType: item.fortuneType,
          option: item.option,
          saleEndsAt: item.saleEndsAt,
        } satisfies Omit<
          components["schemas"]["ProductResponse"],
          "regularPrice"
        >;
        // productName · regularPrice 는 포트에 넣지 않는다 (화면 문구 PD, Q-22)
        return {
          code: wire.code,
          price: { currency: wire.price.currency, amount: wire.price.amount },
          paidAmount: wire.paidAmount,
          bonusAmount: wire.bonusAmount,
          creditedAmount: wire.creditedAmount,
          active: wire.active,
        };
      });
    },

    async confirm(ret): Promise<TopUpOrderState> {
      // 방어: readPaymentReturn 이 먼저 막는다. 1 이상의 안전한 정수 문자열이 아니면 요청하지 않는다
      const amount = Number(ret.amount);
      if (
        !/^\d+$/.test(ret.amount) ||
        amount < 1 ||
        !Number.isSafeInteger(amount)
      ) {
        throw new Error("승인 금액이 1 이상의 정수가 아니다");
      }
      const body = {
        paymentKey: ret.paymentKey,
        orderId: ret.orderId,
        amount,
      } satisfies components["schemas"]["ConfirmRequest"];
      // 승인 키 = 주문 ID (CONFIRM-KEY). 주문 ID 가 UUID 가 아니면 여기서 던진다. 200 · 202 모두 같은 방식으로 바꾼다
      const { status, data } = await client.request({
        method: "POST",
        path: CONFIRM_PATH.replace(
          "{orderId}",
          encodeURIComponent(ret.orderId),
        ),
        command: createOrderBoundCommand(ret.orderId, body),
      });
      const parsed = orderSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "충전 승인 응답 모양이 다르다");
      }
      return toOrderState(parsed.data);
    },

    async getOrder(orderId): Promise<TopUpOrderState> {
      const { status, data } = await client.request({
        method: "GET",
        path: ORDER_BY_ID_PATH.replace(
          "{orderId}",
          encodeURIComponent(orderId),
        ),
      });
      const parsed = orderSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "충전 주문 조회 응답 모양이 다르다");
      }
      return toOrderState(parsed.data);
    },

    async getRefund(orderId): Promise<CashRefund> {
      const { status, data } = await client.request({
        method: "GET",
        path: REFUND_PATH.replace("{orderId}", encodeURIComponent(orderId)),
      });
      const parsed = refundSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "충전 환불 조회 응답 모양이 다르다");
      }
      return toCashRefund(parsed.data);
    },
  };
}
