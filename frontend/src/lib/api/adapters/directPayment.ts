// 직접결제 진짜 adapter — POST /api/v1/direct-payment-batches · GET .../{batchId} · POST .../{batchId}/confirm · POST .../{batchId}/reading-fulfillment.
// 근거: DIRECT-PAY, PR #49 c2a1392 의 DirectPaymentBatchController · DirectPaymentReadingController (브랜치 feat/direct-payment-gift-fulfillment).
// TODO(OPENAPI-FE-SYNC): 아래 경로 · 필드는 PR #49 c2a1392 컨트롤러 기준이다 — 공유 OpenAPI(src/types/api.d.ts)에 들어오면 생성 타입과 대조한다.
// ADAPTER-HTTP (createApiClient 로 요청하고 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다. 위반은 ApiContractError),
// I-05 (주문 생성 · 결제 뒤 생성 키 = 호출하는 쪽이 준 것만, 같은 키 · 같은 본문), CONFIRM-KEY (승인 키 = 주문 ID, createOrderBoundCommand),
// Q-38 (readingId null 을 완료로 바꾸지 않는다, 모르는 status 는 UNKNOWN), P-09 (금액은 서버 주문 값 그대로 — 계산하지 않는다).
// 결제 키(paymentKey)와 생년 정보는 어떤 로그 · 오류 메시지에도 넣지 않는다.
// 수능운은 결제 뒤 생성 경로가 아직 없다 (Q-64) — fulfillReading 이 요청 전에 막는다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import {
  createKeyedCommand,
  createOrderBoundCommand,
} from "@/lib/api/idempotency";
import {
  type DirectPaymentOrder,
  type DirectPaymentPort,
  type DirectReadingFulfillment,
  toDirectPaymentStatus,
} from "@/lib/ports/directPayment";
import { toReadingPurchaseStatus } from "@/lib/ports/fortune";
import { checkSelection } from "./fortune";

// TODO(OPENAPI-FE-SYNC): satisfies keyof paths 로 바꾼다
const BATCHES_PATH = "/api/v1/direct-payment-batches";
const BATCH_BY_ID_PATH = "/api/v1/direct-payment-batches/{batchId}";
const CONFIRM_PATH = "/api/v1/direct-payment-batches/{batchId}/confirm";
const FULFILLMENT_PATH =
  "/api/v1/direct-payment-batches/{batchId}/reading-fulfillment";

const text = z.string().min(1);
const date = z.string().refine((value) => !Number.isNaN(Date.parse(value)));

// 주문 응답 (DirectPaymentBatchController.Response). createdAt 은 검사만 한다. 통화는 원화여야 한다 (DIRECT-PAY)
const orderSchema = z.object({
  orderId: z.uuid(),
  productCode: text,
  orderName: text,
  amount: z.number().int().min(1),
  currency: z.literal("KRW"),
  // 모르는 상태는 UNKNOWN 으로 받는다 — 계약 위반 아님
  status: z.string(),
  createdAt: date,
});

// 결제 뒤 생성 응답 (DirectPaymentReadingController.Response). reused 는 검사만 한다
const fulfillmentSchema = z.object({
  purchaseId: z.uuid(),
  readingId: z.uuid().nullable(),
  status: z.string(),
  reused: z.boolean(),
});

function toOrder(data: z.infer<typeof orderSchema>): DirectPaymentOrder {
  return {
    orderId: data.orderId,
    productCode: data.productCode,
    orderName: data.orderName,
    amount: { currency: "KRW", amount: data.amount },
    status: toDirectPaymentStatus(data.status),
  };
}

export function createDirectPaymentAdapter(
  client: ApiClient,
): DirectPaymentPort {
  return {
    async createOrder(input, idempotencyKey): Promise<DirectPaymentOrder> {
      // 수량은 1 고정 (선물만 여러 명 — 이번 범위 밖)
      const body = { quoteId: input.quoteId, quantity: 1 };
      const { status, data } = await client.request({
        method: "POST",
        path: BATCHES_PATH,
        command: createKeyedCommand(idempotencyKey, body),
      });
      const parsed = orderSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "직접결제 주문 응답 모양이 다르다");
      }
      return toOrder(parsed.data);
    },

    async confirm(ret): Promise<DirectPaymentOrder> {
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
      };
      // 승인 키 = 주문 ID (CONFIRM-KEY). 주문 ID 가 UUID 가 아니면 여기서 던진다. 200 · 202 모두 같은 방식으로 바꾼다
      const { status, data } = await client.request({
        method: "POST",
        path: CONFIRM_PATH.replace(
          "{batchId}",
          encodeURIComponent(ret.orderId),
        ),
        command: createOrderBoundCommand(ret.orderId, body),
      });
      const parsed = orderSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "직접결제 승인 응답 모양이 다르다");
      }
      return toOrder(parsed.data);
    },

    async getOrder(orderId): Promise<DirectPaymentOrder> {
      const { status, data } = await client.request({
        method: "GET",
        path: BATCH_BY_ID_PATH.replace(
          "{batchId}",
          encodeURIComponent(orderId),
        ),
      });
      const parsed = orderSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(
          status,
          "직접결제 주문 조회 응답 모양이 다르다",
        );
      }
      return toOrder(parsed.data);
    },

    async fulfillReading(
      input,
      idempotencyKey,
    ): Promise<DirectReadingFulfillment> {
      const checked = checkSelection(input.selection);
      if (checked.kind === "SUNEUNG") {
        // 결제 뒤 생성 요청은 옵션 · 질문이 필수라 수능운은 보낼 수 없다 — 우회하지 않는다
        throw new Error("수능운은 결제 뒤 생성 경로가 아직 없다 (Q-64)");
      }
      const body =
        checked.kind === "COMPATIBILITY"
          ? {
              quoteId: input.quoteId,
              personId: checked.personId,
              fortuneType: "COMPATIBILITY",
              productOption: checked.productOption,
              questionKey: checked.questionKey,
              counterpartPersonId: checked.counterpartPersonId,
              relationType: checked.relationType,
              personalizedConcern: null,
            }
          : {
              quoteId: input.quoteId,
              personId: checked.personId,
              fortuneType: checked.fortuneType,
              productOption: checked.productOption,
              questionKey: checked.questionKey,
              counterpartPersonId: null,
              relationType: null,
              personalizedConcern: null,
            };
      const { status, data } = await client.request({
        method: "POST",
        path: FULFILLMENT_PATH.replace(
          "{batchId}",
          encodeURIComponent(input.orderId),
        ),
        command: createKeyedCommand(idempotencyKey, body),
      });
      const parsed = fulfillmentSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(
          status,
          "결제 뒤 운세 생성 응답 모양이 다르다",
        );
      }
      // readingId null 을 완료로 바꾸지 않는다 (Q-38)
      return {
        purchaseId: parsed.data.purchaseId,
        readingId: parsed.data.readingId,
        status: toReadingPurchaseStatus(parsed.data.status),
      };
    },
  };
}
