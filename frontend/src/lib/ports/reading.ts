// 결과 포트 (FE 모델, MOCK-PORT). 근거: API_SPEC 2장 · 8장 초안 (ReadingSectionType · GET /readings/{readingId}), VIEWER, F-05 (재열람 스냅샷).
//
// - 이 모델은 FE 모델이다. 진짜 구현(adapters)이 OpenAPI 생성 타입을 이 모양으로 바꾼다. 섹션 키 · 타입 enum 은 BE-B OpenAPI (Q-05 부분).
// - 섹션은 서버가 준 순서 그대로 둔다. 모르는 섹션 타입은 UNKNOWN 으로 남기고 화면이 그 섹션만 건너뛴다.
// - 고지(disclaimers)는 서버 코드 그대로 둔다 — 문구는 PD (F-08). 모르는 코드도 버리지 않는다.
// - share(카카오톡 공유 메타데이터)는 G-11 미정이라 아직 넣지 않는다. 버전 필드(calculationVersion 등)는 화면이 쓰지 않아 넣지 않는다.
// - 소유자가 아니거나 없는 결과는 404 (가짜도 같다). 결과 URL 을 결제 없이 열면 403/404 (Phase 4 완료 기준).
import type { FortuneType, ProductOption } from "./fortune";

export const READING_SECTION_TYPES = [
  "TEXT",
  "PERIOD_GUIDANCE",
  "FOOD_RECOMMENDATION",
  "CHECKLIST",
] as const;

type Titled = Readonly<{ key: string; title: string }>;

export type FoodItem = Readonly<{ name: string; reason: string }>;

export type ReadingSection =
  | (Titled & Readonly<{ type: "TEXT"; content: string }>)
  | (Titled &
      Readonly<{
        type: "PERIOD_GUIDANCE";
        items: readonly Readonly<{
          label: string;
          guidance: string;
          focusPoint: string | null;
        }>[];
      }>)
  | (Titled &
      Readonly<{
        type: "FOOD_RECOMMENDATION";
        primary: FoodItem;
        alternatives: readonly FoodItem[];
      }>)
  | (Titled &
      Readonly<{
        type: "CHECKLIST";
        items: readonly Readonly<{
          id: string;
          label: string;
          personalized: boolean;
        }>[];
      }>)
  // 모르는 타입 — 화면은 건너뛰고 Sentry 에 경고만 남긴다 (rawType 만, 본문은 보내지 않는다)
  | Readonly<{ type: "UNKNOWN"; key: string; rawType: string }>;

export type Reading = Readonly<{
  id: string;
  fortuneType: FortuneType;
  productOption: ProductOption;
  personDisplayName: string;
  birthTimeUnknown: boolean;
  // 이벤트 운세(수능운)만. 날짜는 서버 값 그대로 (YYYY-MM-DD)
  event: Readonly<{ type: string; date: string }> | null;
  sections: readonly ReadingSection[];
  disclaimers: readonly string[];
  // ISO 시각 문자열
  createdAt: string;
}>;

export type ReadingPort = {
  getReading(readingId: string): Promise<Reading>;
};
