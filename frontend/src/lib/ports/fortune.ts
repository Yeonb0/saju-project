// 운세 구매 포트 — 등껍질 차감 확인 팝업(CHECKOUT-POPUP)이 부르는 함수 모양. 근거: docs/FRONTEND.md 1-1 · 1-2 (MOCK-PORT),
// API_SPEC 5장 · 8장 초안 (GET /products · POST /quotes/fortune · GET /quotes/{quoteId} · POST /reading-purchases),
// Q-07 해결 (견적 유효 30분 · GET /quotes/{quoteId} 재확인 · 409 QUOTE_EXPIRED 면 새 견적), P-06 · P-09.
//
// - 이 모델은 FE 모델이다. 백엔드 타입이 아니며, 진짜 구현(adapters)이 OpenAPI 생성 타입을 이 모양으로 바꾼다.
// - 금액 · 등껍질 수량 · 구매 후 잔액은 서버(또는 가짜 구현)가 준 값만 표시한다. 화면에서 계산하지 않는다 (P-03 · P-09).
// - 견적 필드: BE-A 내부 계약(main a064ea6, backend/docs/FE_COMPATIBILITY.md)에 quoteId · walletBalance · balanceAfter · shortage · recommendedTopUp 이 있고 productName 은 아직 없다 — API_SPEC · OpenAPI 반영은 BE-A (Q-17).
// - 고민 입력(interestKey · memo)은 F-04 · Q-26 확정 전이라 넣지 않는다. talismanType 은 T-01 자동생성으로 빠졌다.
// - 실패는 src/lib/api/errors.ts 의 ApiError · ApiNetworkError · ApiContractError 로 던진다 (가짜도 같다).
import type { Money } from "./topUp";

// API_SPEC 2장 초안 — 모르는 값은 UNKNOWN
export const FORTUNE_TYPES = [
  "OVERALL",
  "LOVE",
  "WEALTH",
  "COMPATIBILITY",
  "SINSAL",
  "SUNEUNG",
] as const;

export type FortuneType = (typeof FORTUNE_TYPES)[number] | "UNKNOWN";

export const PRODUCT_OPTIONS = [
  "READING_ONLY",
  "READING_WITH_TALISMAN",
] as const;

export type ProductOption = (typeof PRODUCT_OPTIONS)[number] | "UNKNOWN";

export function toFortuneType(raw: string): FortuneType {
  return (FORTUNE_TYPES as readonly string[]).includes(raw)
    ? (raw as FortuneType)
    : "UNKNOWN";
}

export function toProductOption(raw: string): ProductOption {
  return (PRODUCT_OPTIONS as readonly string[]).includes(raw)
    ? (raw as ProductOption)
    : "UNKNOWN";
}

export type FortuneProduct = Readonly<{
  code: string;
  fortuneType: FortuneType;
  option: ProductOption;
  price: Money;
  active: boolean;
  // ISO 시각 문자열. 판매 마감 판단은 서버 active · saleEndsAt 기준 (F-07) — 화면은 표시만
  saleEndsAt: string | null;
}>;

// 견적 요청 = 구매 선택. 궁합은 counterpartPersonId 를 쓴다 (API_SPEC 8장 초안, 관계 값은 Q-26 확정 후)
export type FortuneSelection = Readonly<{
  productCode: string;
  personId: string;
  counterpartPersonId: string | null;
}>;

export type FortuneQuote = Readonly<{
  quoteId: string;
  productCode: string;
  productName: string;
  price: Money;
  walletBalance: number;
  // 잔액이 충분할 때만 서버가 준 구매 후 잔액, 부족하면 null
  balanceAfter: number | null;
  // 0 이면 잔액 충분. 부족분 · 추천 충전 상품은 서버 값만 (P-06)
  shortage: number;
  recommendedTopUp: string | null;
  // ISO 시각 문자열 (유효 30분, Q-07)
  expiresAt: string;
}>;

// API_SPEC 13장 본인 구매 상태 초안 — 모르는 값은 UNKNOWN
export const READING_PURCHASE_STATUSES = [
  "CREATED",
  "DEBITED",
  "GENERATING",
  "FULFILLED",
  "FAILED",
  "REFUNDED",
] as const;

export type ReadingPurchaseStatus =
  | (typeof READING_PURCHASE_STATUSES)[number]
  | "UNKNOWN";

export function toReadingPurchaseStatus(raw: string): ReadingPurchaseStatus {
  return (READING_PURCHASE_STATUSES as readonly string[]).includes(raw)
    ? (raw as ReadingPurchaseStatus)
    : "UNKNOWN";
}

export type ReadingPurchaseResult = Readonly<{
  purchaseId: string;
  // 처리 중 · 재요청 응답에서 null 일 수 있다 — null 을 0 · 완료로 바꾸지 않는다 (Q-38, BE-A 10/10)
  readingId: string | null;
  status: ReadingPurchaseStatus;
  charged: Money;
  // 구매 응답의 서버 잔액. 처리 중 · 재요청 응답에서 null 일 수 있다 (Q-38, BE-A 10/10)
  balance: number | null;
  // refunded 는 두지 않는다 — OpenAPI PurchaseResponse 에 refunded 가 없고, 오류 code 만으로 환급을 단정하지 않는다 (Q-38)
}>;

// 결과 화면으로 보내도 되는 구매 응답 — FULFILLED 이고 readingId 가 있을 때만 (Q-38)
export type FulfilledReadingPurchase = ReadingPurchaseResult & {
  status: "FULFILLED";
  readingId: string;
};

export type FortunePort = {
  listProducts(fortuneType: FortuneType): Promise<readonly FortuneProduct[]>;
  createQuote(selection: FortuneSelection): Promise<FortuneQuote>;
  // 만료면 409 QUOTE_EXPIRED
  getQuote(quoteId: string): Promise<FortuneQuote>;
  // 차감은 이 명령 안에서 서버가 한다. idempotencyKey 는 구매 의도(견적) 하나에 하나 (I-05)
  purchase(
    input: Readonly<{ quoteId: string; selection: FortuneSelection }>,
    idempotencyKey: string,
  ): Promise<ReadingPurchaseResult>;
};
