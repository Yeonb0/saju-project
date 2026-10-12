// 결과 포트 (FE 모델, MOCK-PORT). 근거: RESULT-GUIDE-MODEL (2026-10-12, 사용자 — 결과 포트를 서버 suneungGuide 구조에 맞춘다),
// Q-37 답 (API_SPEC 8장 "수능운 결과 구조", PR #20 · backend/src/main/resources/liner/suneung-response.schema.json),
// VIEWER, F-05 (재열람 스냅샷), F-08 (고지), S-06 (서버 결과만 표시), Q-05 (모르는 값은 그 부분만 건너뜀).
//
// - 이 모델은 FE 모델이다. 진짜 구현(adapters)이 OpenAPI 생성 타입을 이 모양으로 바꾼다.
// - 수능운은 서버 suneungGuide 가 있으면 SUNEUNG_GUIDE, 없는 예전 결과 · 일반 운세는 SECTIONS 다 (API_SPEC 8장 전환 기간 규칙).
//   서버 sections 는 key · content 뿐이다 — 제목은 서버가 주지 않는다 (src/lib/reading/labels.ts).
// - SECTIONS 는 서버가 준 순서 그대로 둔다. 모르는 섹션은 UNKNOWN 으로 남기고 화면이 그 섹션만 건너뛴다.
// - 교시 코드(period)는 서버 코드 그대로 둔다 — 화면 이름은 뷰어가 붙인다. 모르는 준비물 분류는 UNKNOWN.
// - 고지(disclaimers)는 서버 코드 그대로 둔다 — 문구는 PD (F-08). 모르는 코드도 버리지 않는다.
// - share(카카오톡 공유 메타데이터)는 G-11 미정이라 아직 넣지 않는다. 버전 필드(calculationVersion 등)는 화면이 쓰지 않아 넣지 않는다.
// - 소유자가 아니거나 없는 결과는 404 (가짜도 같다). 결과 URL 을 결제 없이 열면 403/404 (Phase 4 완료 기준).
import type { FortuneType, ProductOption } from "./fortune";

export const PREPARATION_CATEGORIES = [
  "DOCUMENT",
  "WRITING_TOOL",
  "TIMEPIECE",
  "FOOD",
  "CLOTHING",
  "OTHER",
] as const;

export type PreparationCategory =
  | (typeof PREPARATION_CATEGORIES)[number]
  | "UNKNOWN";

export function toPreparationCategory(raw: string): PreparationCategory {
  return (PREPARATION_CATEGORIES as readonly string[]).includes(raw)
    ? (raw as PreparationCategory)
    : "UNKNOWN";
}

export type SuneungGuide = Readonly<{
  summary: string;
  examDayGuide: string;
  // period 는 서버 코드 그대로 — 화면 이름은 뷰어가 붙인다
  examPeriods: readonly Readonly<{ period: string; guidance: string }>[];
  focusTips: readonly string[];
  foodRecommendations: readonly Readonly<{ item: string; reason: string }>[];
  preparationChecklist: readonly Readonly<{
    id: string;
    label: string;
    category: PreparationCategory;
  }>[];
  anxietyManagement: readonly string[];
  missingElementAdvice: string;
}>;

export type ReadingSection =
  | Readonly<{ type: "TEXT"; key: string; content: string }>
  // 모르는 섹션 — 화면은 건너뛰고 Sentry 에 경고만 남긴다 (key 만, 본문은 보내지 않는다)
  | Readonly<{ type: "UNKNOWN"; rawKey: string }>;

export type ReadingBody =
  | Readonly<{ kind: "SUNEUNG_GUIDE"; guide: SuneungGuide }>
  | Readonly<{ kind: "SECTIONS"; sections: readonly ReadingSection[] }>;

export type Reading = Readonly<{
  id: string;
  fortuneType: FortuneType;
  productOption: ProductOption;
  personDisplayName: string;
  // 이벤트 운세(수능운)만. 날짜는 서버 값 그대로 (YYYY-MM-DD)
  event: Readonly<{ type: string; date: string }> | null;
  body: ReadingBody;
  disclaimers: readonly string[];
  // ISO 시각 문자열
  createdAt: string;
}>;

export type ReadingPort = {
  getReading(readingId: string): Promise<Reading>;
};
