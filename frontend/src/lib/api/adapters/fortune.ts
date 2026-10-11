// 수능운 구매 진짜 adapter — POST /api/v1/quotes/fortune · GET /api/v1/quotes/{quoteId} · POST /api/v1/reading-purchases.
// 근거: ADAPTER-HTTP (createApiClient 로 요청하고 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다. 생성 타입은 이 파일 안에서만 쓴다),
// MOCK-PORT (포트 모델은 FE 모델), Q-04 · P-03 (수능운 요청은 personId — 구매는 + quoteId — 만 보낸다. fortuneType · productOption · questionKey ·
// relationType · counterpartPersonId 중 하나라도 있으면 BE 가 수능 요청으로 보지 않는다), Q-28 해결 (PR #19, API_SPEC 5장: 견적 productName 은 서버 필수 값 — 견적 생성 때의 snapshot),
// Q-22 · P-03 (regularPrice 정가 표시 방식 · 문구는 미정 — 응답 검사만 하고 포트에 넣지 않는다),
// Q-38 (readingId · balance · charged 의 null 을 0 이나 완료로 바꾸지 않는다, 모르는 status 는 UNKNOWN),
// I-05 (구매 키와 본문은 createKeyedCommand 로 고정, 키는 호출하는 쪽이 준 것만), Q-37 (reading · reused · *Version 은 쓰지 않는다).
// 일반 운세 5종은 요청 필드(productOption · questionKey · relationType)가 확정되지 않아 이번 범위 밖이다 (Q-26 · Q-35 2).
// TODO(PG-2 세션 adapter): ports/index.ts 연결은 CSRF 출처인 세션 adapter 가 생긴 뒤에 한다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import { createKeyedCommand } from "@/lib/api/idempotency";
import {
  type FortunePort,
  type FortuneQuote,
  type FortuneSelection,
  type ReadingPurchaseResult,
  toReadingPurchaseStatus,
} from "@/lib/ports/fortune";
import type { components, paths } from "@/types/api";

const QUOTE_PATH = "/api/v1/quotes/fortune" satisfies keyof paths;
const QUOTE_BY_ID_PATH = "/api/v1/quotes/{quoteId}" satisfies keyof paths;
const PURCHASE_PATH = "/api/v1/reading-purchases" satisfies keyof paths;

const count = z.number().int().min(0);
const text = z.string().min(1);

// 생성 타입은 필드가 모두 선택(?)이거나 null 을 모른다 — 필수와 null 허용은 여기서 정한다. null 허용 값도 키는 있어야 한다
const money = z.object({ currency: text, amount: count });

const quoteFields = {
  quoteId: z.uuid(),
  productCode: text,
  // Q-28 해결 (PR #19): 서버 필수 값, 견적 생성 때의 snapshot. 키 없음 · 빈 문자열 · null 은 계약 위반
  productName: text,
  charged: money,
  expiresAt: z.string().refine((value) => !Number.isNaN(Date.parse(value))),
  walletBalance: count,
  shortage: count,
  balanceAfter: count.nullable(),
  recommendedTopUp: text.nullable(),
  // TODO(Q-22): 정가 표시 방식 · 문구가 정해지면 포트에 넣는다 — 지금은 계약 검사만 (null 허용, 키는 있어야 한다)
  regularPrice: money.nullable(),
};
// event 는 포트에 넣지 않지만 계약 위반은 막는다
const suneungQuoteSchema = z.object({
  ...quoteFields,
  event: z.object({ type: text, date: text }),
});
const fundedQuoteSchema = z.object(quoteFields);

const purchaseSchema = z.object({
  purchaseId: z.uuid(),
  readingId: z.uuid().nullable(),
  // 모르는 상태는 UNKNOWN 으로 받는다 — 계약 위반 아님
  status: z.string(),
  charged: money.nullable(),
  balance: count.nullable(),
});

type QuoteData = z.infer<typeof fundedQuoteSchema>;

function toQuote(data: QuoteData): FortuneQuote {
  // 필드 이름이 생성 타입과 달라지면 typecheck 가 깨진다 — 객체 리터럴이라 초과 속성 검사가 된다
  const wire = {
    quoteId: data.quoteId,
    productCode: data.productCode,
    productName: data.productName,
    charged: { currency: data.charged.currency, amount: data.charged.amount },
    expiresAt: data.expiresAt,
    walletBalance: data.walletBalance,
    shortage: data.shortage,
    balanceAfter: data.balanceAfter,
    recommendedTopUp: data.recommendedTopUp,
    // regularPrice.currency 는 생성 타입이 enum(KRW · TURTLE_SHELL) 이다 — 모르는 통화도 계약 위반으로 치지 않고, 쓰지 않는 필드라 enum 대조에서 뺀다
    // (topUp adapter 의 status 와 같은 방식, ADAPTER-HTTP). 키 존재 · 모양은 zod 가 검사한다
  } satisfies Omit<
    components["schemas"]["FundedQuoteResponse"],
    "regularPrice"
  >;
  return {
    quoteId: wire.quoteId,
    productCode: wire.productCode,
    // 서버 견적의 productName 그대로 (Q-28 해결, PR #19). regularPrice 는 포트에 넣지 않는다 (TODO(Q-22))
    productName: wire.productName,
    charged: wire.charged,
    walletBalance: wire.walletBalance,
    balanceAfter: wire.balanceAfter,
    shortage: wire.shortage,
    recommendedTopUp: wire.recommendedTopUp,
    expiresAt: wire.expiresAt,
  };
}

// 이번 adapter 는 수능운만 안다 — 아니면 요청하기 전에 던진다 (잘못된 필드로 일반 운세를 수능 요청으로 보내지 않게)
function assertSuneung(selection: FortuneSelection) {
  // TODO(Q-26 · Q-35 2): 일반 운세 5종은 요청 필드가 확정되면 만든다
  if (selection.fortuneType !== "SUNEUNG") {
    throw new Error(
      "수능운이 아닌 운세의 진짜 계약이 아직 없다 (Q-26 · Q-35 2)",
    );
  }
  if (selection.counterpartPersonId !== null) {
    throw new Error("수능운 요청에는 상대 인물이 없다 (Q-04 · P-03)");
  }
}

export function createFortuneAdapter(client: ApiClient): FortunePort {
  return {
    // TODO(Q-28): 상품 목록 API 가 OpenAPI 에 없다
    async listProducts() {
      throw new Error(
        "listProducts 의 진짜 계약이 아직 없다 — GET /products 없음 (Q-28)",
      );
    },

    async createQuote(selection): Promise<FortuneQuote> {
      assertSuneung(selection);
      // personId 만 보낸다 — fortuneType 등 다른 필드가 오면 BE 가 수능 요청으로 보지 않는다 (Q-04 · P-03). 견적은 계산 요청이라 Idempotency-Key 없음
      const body = {
        personId: selection.personId,
      } satisfies components["schemas"]["QuoteRequest"];
      const { status, data } = await client.request({
        method: "POST",
        path: QUOTE_PATH,
        json: body,
      });
      const parsed = suneungQuoteSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "수능운 견적 응답 모양이 다르다");
      }
      return toQuote(parsed.data);
    },

    async getQuote(quoteId): Promise<FortuneQuote> {
      const { status, data } = await client.request({
        method: "GET",
        path: QUOTE_BY_ID_PATH.replace(
          "{quoteId}",
          encodeURIComponent(quoteId),
        ),
      });
      const parsed = fundedQuoteSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "견적 재확인 응답 모양이 다르다");
      }
      return toQuote(parsed.data);
    },

    async purchase(input, idempotencyKey): Promise<ReadingPurchaseResult> {
      assertSuneung(input.selection);
      const body = {
        quoteId: input.quoteId,
        personId: input.selection.personId,
      } satisfies components["schemas"]["PurchaseRequest"];
      const { status, data } = await client.request({
        method: "POST",
        path: PURCHASE_PATH,
        command: createKeyedCommand(idempotencyKey, body),
      });
      const parsed = purchaseSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "구매 응답 모양이 다르다");
      }
      // 생성 타입은 null 을 모르므로 null 허용을 덧붙인 모양과 이름을 대조한다. reading · reused · *Version 은 쓰지 않는다 (Q-37)
      const wire = {
        purchaseId: parsed.data.purchaseId,
        readingId: parsed.data.readingId,
        status: parsed.data.status,
        charged: parsed.data.charged,
        balance: parsed.data.balance,
      } satisfies {
        [K in "purchaseId" | "readingId" | "status" | "charged" | "balance"]:
          | components["schemas"]["PurchaseResponse"][K]
          | null;
      };
      // 201 이든 202 든 같은 방식으로 바꾼다 — 성공 여부 판단은 화면의 isFulfilled 몫 (Q-38)
      return {
        purchaseId: wire.purchaseId,
        readingId: wire.readingId,
        status: toReadingPurchaseStatus(wire.status),
        charged: wire.charged,
        balance: wire.balance,
      };
    },
  };
}
