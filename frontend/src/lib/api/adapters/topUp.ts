// 충전 진짜 adapter — GET /api/v1/wallet · POST /api/v1/top-up-orders. 근거: PG-3, Q-34 4 · Q-17 부분 (OpenAPI WalletResponse · OrderResponse),
// ADAPTER-HTTP (createApiClient 로 요청하고 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다), I-05 (주문 생성 키 = 구매 의도당 하나),
// TOPUP-DONE (예정 지급량은 포트에 넣지 않는다 — 지급 완료 · 수량 표시는 CREDITED 뒤 서버 값만).
// TODO(PG-2 세션 adapter): ports/index.ts 연결은 CSRF 출처인 세션 adapter 가 생긴 뒤에 한다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import { createKeyedCommand } from "@/lib/api/idempotency";
import {
  type TopUpOrder,
  type TopUpPort,
  toTopUpOrderStatus,
  type Wallet,
} from "@/lib/ports/topUp";
import type { components, paths } from "@/types/api";

const WALLET_PATH = "/api/v1/wallet" satisfies keyof paths;
const ORDERS_PATH = "/api/v1/top-up-orders" satisfies keyof paths;

const count = z.number().int().min(0);
const text = z.string().min(1);

// 포트에 넣지 않는 값도 계약 위반은 막는다
const walletSchema = z.object({
  currency: text,
  balance: count,
  paidBalance: count,
  bonusBalance: count,
});

const orderSchema = z.object({
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
});

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
      } satisfies Omit<components["schemas"]["OrderResponse"], "status">;
      // 예정 지급량(paid · bonus · credited ShellAmount)은 포트에 넣지 않는다 (TOPUP-DONE, BE-A 10/10)
      return {
        orderId: wire.orderId,
        orderName: wire.orderName,
        amount: { currency: wire.currency, amount: wire.amount },
        status: toTopUpOrderStatus(parsed.data.status),
      };
    },

    // TODO(Q-28): 상품 목록 API 가 OpenAPI 에 없다
    async listTopUpProducts() {
      throw new Error(
        "listTopUpProducts 의 진짜 계약이 아직 없다 — GET /products 없음 (Q-28)",
      );
    },

    // TODO(Q-17): 승인 API 가 OpenAPI 에 없다
    async confirm() {
      throw new Error(
        "confirm 의 진짜 계약이 아직 없다 — 승인 API 없음 (Q-17)",
      );
    },

    // TODO(Q-17): 주문 조회에 processing · walletBalance 가 없다
    async getOrder() {
      throw new Error(
        "getOrder 의 진짜 계약이 아직 없다 — 주문 조회에 processing · walletBalance 없음 (Q-17 · TOPUP-DONE)",
      );
    },
  };
}
