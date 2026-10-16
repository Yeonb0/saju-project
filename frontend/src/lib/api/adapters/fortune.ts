// 운세 구매 진짜 adapter — GET /api/v1/products?category=FORTUNE · POST /api/v1/quotes/fortune (수능운) ·
// POST /api/v1/quotes/fortune/{overall|love|wealth|sinsal|compatibility} (일반 운세 5종) · GET /api/v1/quotes/{quoteId} ·
// POST /api/v1/reading-purchases (수능운) · POST /api/v1/reading-purchases/{같은 다섯} (일반 운세 5종) · GET /api/v1/reading-purchases/by-quote/{quoteId}.
// 근거: ADAPTER-HTTP (createApiClient 로 요청하고 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다. 생성 타입은 이 파일 안에서만 쓴다),
// MOCK-PORT (포트 모델은 FE 모델), Q-04 · P-03 (수능운 요청은 personId — 구매는 + quoteId — 만 보낸다. fortuneType · productOption · questionKey ·
// relationType · counterpartPersonId 중 하나라도 있으면 BE 가 수능 요청으로 보지 않는다), Q-28 해결 (PR #19, API_SPEC 5장: 견적 productName 은 서버 필수 값 — 견적 생성 때의 snapshot),
// Q-22 · P-03 (regularPrice 정가 표시 방식 · 문구는 미정 — 응답 검사만 하고 포트에 넣지 않는다),
// Q-38 (readingId · balance · charged 의 null 을 0 이나 완료로 바꾸지 않는다, 모르는 status 는 UNKNOWN),
// Q-38 답 · PURCHASE-STATUS-WAIT 해제 (2026-10-12): 구매 상태 조회는 견적 ID 만으로 하고, 구매 기록이 없으면 404 PURCHASE_NOT_FOUND → null.
// I-05 (구매 키와 본문은 createKeyedCommand 로 고정, 키는 호출하는 쪽이 준 것만), Q-37 (reading · reused · *Version 은 쓰지 않는다).
// 일반 운세 5종 (Q-26): 견적 요청은 {personId, productOption, questionKey} (궁합은 + counterpartPersonId · relationType) — fortuneType · productCode 는 보내지 않는다.
// 구매 요청은 견적 발급 때 쓴 필드를 똑같이 + quoteId (API_SPEC 8장 "POST /reading-purchases/{category}", GeneralReadingService.validateInput · validateQuoteSnapshot).
// 운세별 결과 상세 경로(GET /readings/{category}/{readingId})는 reading adapter 범위라 여기서 다루지 않는다.
// 상품 목록: API_SPEC 5장 GET /products (PR #19), F-07 (서버가 판매 중인 상품만 준다 — 화면이 active 를 본다), DIRECT-PAY (운세는 원화 건별 결제), Q-22 (정가 표시는 미정 — 검사만).
import { z } from "zod";
import { ApiContractError, ApiError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import { createKeyedCommand } from "@/lib/api/idempotency";
import { isQuestionKeyOf } from "@/lib/fortune/questions";
import {
  type FortunePort,
  type FortuneProduct,
  type FortuneQuote,
  type FortuneSelection,
  type ReadingPurchaseResult,
  type ReadingPurchaseState,
  toCompensationStatus,
  toFortuneType,
  toProductOption,
  toPurchaseNextAction,
  toReadingPurchaseStatus,
} from "@/lib/ports/fortune";
import type { components, paths } from "@/types/api";

const QUOTE_PATH = "/api/v1/quotes/fortune" satisfies keyof paths;
const QUOTE_BY_ID_PATH = "/api/v1/quotes/{quoteId}" satisfies keyof paths;
const PURCHASE_PATH = "/api/v1/reading-purchases" satisfies keyof paths;
// 일반 운세 5종의 견적 · 구매 경로 — 문자열을 이어 붙여 만들지 않는다 (생성 paths 에 없으면 typecheck 가 깨진다)
const GENERAL_FORTUNES = [
  "OVERALL",
  "LOVE",
  "WEALTH",
  "SINSAL",
  "COMPATIBILITY",
] as const;
type GeneralFortune = (typeof GENERAL_FORTUNES)[number];
type StandardFortune = Exclude<GeneralFortune, "COMPATIBILITY">;

const GENERAL_QUOTE_PATH = {
  OVERALL: "/api/v1/quotes/fortune/overall",
  LOVE: "/api/v1/quotes/fortune/love",
  WEALTH: "/api/v1/quotes/fortune/wealth",
  SINSAL: "/api/v1/quotes/fortune/sinsal",
  COMPATIBILITY: "/api/v1/quotes/fortune/compatibility",
} as const satisfies Record<GeneralFortune, keyof paths>;
const GENERAL_PURCHASE_PATH = {
  OVERALL: "/api/v1/reading-purchases/overall",
  LOVE: "/api/v1/reading-purchases/love",
  WEALTH: "/api/v1/reading-purchases/wealth",
  SINSAL: "/api/v1/reading-purchases/sinsal",
  COMPATIBILITY: "/api/v1/reading-purchases/compatibility",
} as const satisfies Record<GeneralFortune, keyof paths>;

const PURCHASE_BY_QUOTE_PATH =
  "/api/v1/reading-purchases/by-quote/{quoteId}" satisfies keyof paths;
const PRODUCTS_PATH = "/api/v1/products" satisfies keyof paths;

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
// 일반 운세 견적 (CategoryQuoteResponse) — event 는 없고 productOption · questionKey 가 있다
const generalQuoteSchema = z.object({
  ...quoteFields,
  productOption: text,
  questionKey: text,
});

const purchaseSchema = z.object({
  purchaseId: z.uuid(),
  readingId: z.uuid().nullable(),
  // 모르는 상태는 UNKNOWN 으로 받는다 — 계약 위반 아님
  status: z.string(),
  charged: money.nullable(),
  balance: count.nullable(),
  // 모르는 값은 UNKNOWN 으로 받는다 — 키는 있어야 한다 (Q-38 답, API_SPEC 8장)
  compensationStatus: z.string(),
  nextAction: z.string(),
});

type PurchaseData = z.infer<typeof purchaseSchema>;

// 수능운 · 일반 운세 5종의 구매 응답을 같은 방식으로 포트 모델로 바꾼다 (201 이든 202 든 — 성공 여부 판단은 화면의 isFulfilled 몫, Q-38).
// 생성 타입은 null 을 모르므로 null 허용을 덧붙인 모양과 이름을 대조한다. 다섯 일반 운세 응답(CategoryPurchaseResponse*)의 공통 필드 이름은 같다 —
// 그중 Overall 로 대조한다. reading · reused · *Version 은 쓰지 않는다 (Q-37)
function toPurchaseResult(data: PurchaseData): ReadingPurchaseResult {
  const wire = {
    purchaseId: data.purchaseId,
    readingId: data.readingId,
    status: data.status,
    charged: data.charged,
    balance: data.balance,
    compensationStatus: data.compensationStatus,
    nextAction: data.nextAction,
  } satisfies {
    [K in
      | "purchaseId"
      | "readingId"
      | "status"
      | "charged"
      | "balance"
      | "compensationStatus"
      | "nextAction"]: components["schemas"]["PurchaseResponse"][K] | null;
  } & {
    [K in
      | "purchaseId"
      | "readingId"
      | "status"
      | "charged"
      | "balance"
      | "compensationStatus"
      | "nextAction"]:
      | components["schemas"]["CategoryPurchaseResponseOverallReadingResponse"][K]
      | null;
  };
  return {
    purchaseId: wire.purchaseId,
    readingId: wire.readingId,
    status: toReadingPurchaseStatus(wire.status),
    compensationStatus: toCompensationStatus(wire.compensationStatus),
    nextAction: toPurchaseNextAction(wire.nextAction),
    charged: wire.charged,
    balance: wire.balance,
  };
}

// 구매 상태 조회 (GET by-quote). failureCode · createdAt · fulfilledAt 은 검사 · 보관하지 않는다 (TODO(Q-38 c))
const purchaseStatusSchema = z.object({
  purchaseId: z.uuid(),
  quoteId: z.uuid(),
  readingId: z.uuid().nullable(),
  status: z.string(),
  compensationStatus: z.string(),
  nextAction: z.string(),
});

// 운세 상품 한 항목. fortuneType · option 은 모르는 값도 받고, 요청한 fortuneType 과의 일치는 호출할 때 검사한다.
// 운세 상품은 원화 건별 결제다 (DIRECT-PAY, BE 카탈로그 전환 V202610151400) — 지급량(paidAmount · bonusAmount · creditedAmount)은 null 이어야 한다. 정가는 검사만 한다 (TODO(Q-22))
const productSchema = z.object({
  code: text,
  category: z.literal("FORTUNE"),
  fortuneType: text,
  option: text,
  productName: text,
  price: z.object({
    // 운세 상품은 원화 건별 결제 — DIRECT-PAY, BE 카탈로그 전환 V202610151400. 등껍질 가격은 계약 위반이다
    currency: z.literal("KRW"),
    amount: count.min(1),
  }),
  regularPrice: z.object({ currency: text, amount: count }).nullable(),
  paidAmount: z.null(),
  bonusAmount: z.null(),
  creditedAmount: z.null(),
  active: z.boolean(),
  saleEndsAt: z
    .string()
    .refine((value) => !Number.isNaN(Date.parse(value)))
    .nullable(),
});
const productsSchema = z.object({ items: z.array(productSchema) });

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

type ProductOptionWire =
  components["schemas"]["StandardQuoteRequest"]["productOption"];
type RelationTypeWire =
  components["schemas"]["CompatibilityQuoteRequest"]["relationType"];

export type CheckedSelection =
  | { kind: "SUNEUNG"; personId: string }
  | {
      kind: "STANDARD";
      fortuneType: StandardFortune;
      personId: string;
      productOption: ProductOptionWire;
      questionKey: string;
    }
  | {
      kind: "COMPATIBILITY";
      personId: string;
      counterpartPersonId: string;
      relationType: RelationTypeWire;
      productOption: ProductOptionWire;
      questionKey: string;
    };

// 요청하기 전에 선택이 서버 규칙에 맞는지 본다 — 어긋나면 던진다 (화면 코드 오류라 error.tsx 로 간다).
// 근거: Q-04 · P-03 (수능운 요청에 질문 값이 하나라도 있으면 BE 가 수능 요청으로 보지 않는다), API_SPEC 8장 허용 questionKey 표, GeneralReadingService.validateInput.
export function checkSelection(selection: FortuneSelection): CheckedSelection {
  const {
    fortuneType,
    personId,
    counterpartPersonId,
    productOption,
    questionKey,
    relationType,
  } = selection;
  if (fortuneType === "SUNEUNG") {
    if (counterpartPersonId !== null) {
      throw new Error("수능운 요청에는 상대 인물이 없다 (Q-04 · P-03)");
    }
    if (
      productOption !== null ||
      questionKey !== null ||
      relationType !== null
    ) {
      throw new Error(
        "수능운 요청에는 옵션 · 질문 · 관계가 없다 (Q-04 · P-03)",
      );
    }
    return { kind: "SUNEUNG", personId };
  }
  if (productOption === null) {
    throw new Error("일반 운세 요청에는 옵션이 있어야 한다 (Q-26)");
  }
  if (questionKey === null || !isQuestionKeyOf(fortuneType, questionKey)) {
    throw new Error("이 운세에서 허용하지 않는 질문 키다 (API_SPEC 8장)");
  }
  if (fortuneType === "COMPATIBILITY") {
    if (counterpartPersonId === null || relationType === null) {
      throw new Error("궁합 요청에는 상대 인물과 관계가 있어야 한다 (Q-26)");
    }
    return {
      kind: "COMPATIBILITY",
      personId,
      counterpartPersonId,
      relationType,
      productOption,
      questionKey,
    };
  }
  if (counterpartPersonId !== null || relationType !== null) {
    throw new Error("궁합이 아닌 운세 요청에는 상대 인물 · 관계가 없다 (Q-26)");
  }
  return {
    kind: "STANDARD",
    fortuneType,
    personId,
    productOption,
    questionKey,
  };
}

export function createFortuneAdapter(client: ApiClient): FortunePort {
  return {
    async listProducts(fortuneType): Promise<readonly FortuneProduct[]> {
      if (fortuneType === "UNKNOWN") {
        throw new Error("UNKNOWN 운세 종류의 상품 목록은 요청할 수 없다");
      }
      const { status, data } = await client.request({
        method: "GET",
        path: `${PRODUCTS_PATH}?category=FORTUNE&fortuneType=${fortuneType}`,
      });
      const parsed = productsSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "운세 상품 목록 응답 모양이 다르다");
      }
      return parsed.data.items.map((item) => {
        if (item.fortuneType !== fortuneType) {
          throw new ApiContractError(
            status,
            "요청한 운세 종류와 다른 상품이 왔다",
          );
        }
        // fortuneType · option · price(통화)는 모르는 값도 받으므로 생성 타입의 enum 대조에서 뺀다 (topUp adapter 의 status 와 같은 이유).
        // 이름 · 모양은 나머지로 대조한다. productName · regularPrice · 지급량은 포트에 넣지 않는다
        const wire = {
          code: item.code,
          category: item.category,
          productName: item.productName,
          paidAmount: item.paidAmount,
          bonusAmount: item.bonusAmount,
          creditedAmount: item.creditedAmount,
          active: item.active,
          saleEndsAt: item.saleEndsAt,
        } satisfies Omit<
          components["schemas"]["ProductResponse"],
          "regularPrice" | "fortuneType" | "option" | "price"
        >;
        return {
          code: wire.code,
          fortuneType: toFortuneType(item.fortuneType),
          option: toProductOption(item.option),
          price: {
            currency: item.price.currency,
            amount: item.price.amount,
          },
          active: wire.active,
          saleEndsAt: wire.saleEndsAt,
        };
      });
    },

    async createQuote(selection): Promise<FortuneQuote> {
      const checked = checkSelection(selection);
      if (checked.kind !== "SUNEUNG") {
        // 견적은 계산 요청이라 Idempotency-Key 없음 (수능운과 같다). fortuneType · productCode 는 보내지 않는다
        const sent =
          checked.kind === "COMPATIBILITY"
            ? ({
                personId: checked.personId,
                counterpartPersonId: checked.counterpartPersonId,
                relationType: checked.relationType,
                productOption: checked.productOption,
                questionKey: checked.questionKey,
              } satisfies components["schemas"]["CompatibilityQuoteRequest"])
            : ({
                personId: checked.personId,
                productOption: checked.productOption,
                questionKey: checked.questionKey,
              } satisfies components["schemas"]["StandardQuoteRequest"]);
        const { status, data } = await client.request({
          method: "POST",
          path:
            checked.kind === "COMPATIBILITY"
              ? GENERAL_QUOTE_PATH.COMPATIBILITY
              : GENERAL_QUOTE_PATH[checked.fortuneType],
          json: sent,
        });
        const parsed = generalQuoteSchema.safeParse(data);
        if (!parsed.success) {
          throw new ApiContractError(
            status,
            "일반 운세 견적 응답 모양이 다르다",
          );
        }
        // 이름 대조 (CategoryQuoteResponse). 서버가 다른 질문의 견적을 주면 차감 팝업에 쓰지 않는다
        const echoed = {
          productOption: parsed.data.productOption,
          questionKey: parsed.data.questionKey,
        } satisfies Pick<
          components["schemas"]["CategoryQuoteResponse"],
          "productOption" | "questionKey"
        >;
        if (
          echoed.productOption !== sent.productOption ||
          echoed.questionKey !== sent.questionKey
        ) {
          throw new ApiContractError(
            status,
            "일반 운세 견적 응답의 옵션 · 질문이 요청과 다르다",
          );
        }
        return toQuote(parsed.data);
      }
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
      const checked = checkSelection(input.selection);
      if (checked.kind !== "SUNEUNG") {
        // 견적 발급 때 쓴 필드를 똑같이 + quoteId (API_SPEC 8장). 키와 본문은 createKeyedCommand 로 고정한다 (I-05)
        const sent =
          checked.kind === "COMPATIBILITY"
            ? ({
                quoteId: input.quoteId,
                personId: checked.personId,
                counterpartPersonId: checked.counterpartPersonId,
                relationType: checked.relationType,
                productOption: checked.productOption,
                questionKey: checked.questionKey,
              } satisfies components["schemas"]["CompatibilityPurchaseRequest"])
            : ({
                quoteId: input.quoteId,
                personId: checked.personId,
                productOption: checked.productOption,
                questionKey: checked.questionKey,
              } satisfies components["schemas"]["StandardPurchaseRequest"]);
        const { status, data } = await client.request({
          method: "POST",
          path:
            checked.kind === "COMPATIBILITY"
              ? GENERAL_PURCHASE_PATH.COMPATIBILITY
              : GENERAL_PURCHASE_PATH[checked.fortuneType],
          command: createKeyedCommand(idempotencyKey, sent),
        });
        const parsed = purchaseSchema.safeParse(data);
        if (!parsed.success) {
          throw new ApiContractError(
            status,
            "일반 운세 구매 응답 모양이 다르다",
          );
        }
        return toPurchaseResult(parsed.data);
      }
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
      return toPurchaseResult(parsed.data);
    },

    // 선택 검사(checkSelection)는 하지 않는다 — 입력이 견적 ID 뿐이다. 일반 운세도 같은 경로 (API_SPEC 8장)
    async getPurchaseStatus(quoteId): Promise<ReadingPurchaseState | null> {
      let response: Awaited<ReturnType<ApiClient["request"]>>;
      try {
        response = await client.request({
          method: "GET",
          path: PURCHASE_BY_QUOTE_PATH.replace(
            "{quoteId}",
            encodeURIComponent(quoteId),
          ),
        });
      } catch (error) {
        // 구매 기록이 없는 것만 null — 다른 404 code 를 포함한 나머지 오류는 그대로 던진다
        if (
          error instanceof ApiError &&
          error.status === 404 &&
          error.code === "PURCHASE_NOT_FOUND"
        ) {
          return null;
        }
        throw error;
      }
      const { status, data } = response;
      const parsed = purchaseStatusSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "구매 상태 응답 모양이 다르다");
      }
      if (parsed.data.quoteId !== quoteId) {
        throw new ApiContractError(
          status,
          "요청한 견적과 다른 구매 상태가 왔다",
        );
      }
      // status · compensationStatus · nextAction 은 모르는 값도 받으므로 생성 타입의 enum 대조에서 뺀다 (purchase 의 status 와 같은 이유).
      // 이름은 나머지로 대조한다
      const wire = {
        purchaseId: parsed.data.purchaseId,
        quoteId: parsed.data.quoteId,
        readingId: parsed.data.readingId,
      } satisfies Pick<
        components["schemas"]["PurchaseStatus"],
        "purchaseId" | "quoteId"
      > & { readingId: string | null };
      return {
        purchaseId: wire.purchaseId,
        readingId: wire.readingId,
        status: toReadingPurchaseStatus(parsed.data.status),
        compensationStatus: toCompensationStatus(
          parsed.data.compensationStatus,
        ),
        nextAction: toPurchaseNextAction(parsed.data.nextAction),
      };
    },
  };
}
