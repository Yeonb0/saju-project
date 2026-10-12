// 결과 화면 임시 이름. 모두 TODO(PD 문구) — 서버가 제목을 주지 않는다 (Q-37).
// 근거: RESULT-GUIDE-MODEL, API_SPEC 8장 "수능운 결과 구조", liner/suneung-response.schema.json (교시 · 분류 enum).
import type { PreparationCategory, SuneungGuide } from "@/lib/ports/reading";

// 키가 빠지면 typecheck 가 깨진다
export const SUNEUNG_GUIDE_TITLES: Record<keyof SuneungGuide, string> = {
  summary: "요약",
  examDayGuide: "시험 당일",
  examPeriods: "교시별 안내",
  focusTips: "집중 팁",
  foodRecommendations: "추천 음식",
  preparationChecklist: "준비물",
  anxietyManagement: "마음 관리",
  missingElementAdvice: "부족한 오행",
};

// 키는 liner/suneung-response.schema.json 의 enum. API_SPEC 8장 예시의 다른 코드는 넣지 않는다
export const EXAM_PERIOD_LABELS: Record<string, string> = {
  BEFORE_ENTRY: "입실 전",
  KOREAN: "국어",
  MATH: "수학",
  LUNCH: "점심",
  ENGLISH: "영어",
  ELECTIVES: "탐구",
  FINAL_CHECK: "마지막 점검",
};

export const PREPARATION_CATEGORY_LABELS: Record<
  Exclude<PreparationCategory, "UNKNOWN">,
  string
> = {
  DOCUMENT: "서류",
  WRITING_TOOL: "필기구",
  TIMEPIECE: "시계",
  FOOD: "먹을거리",
  CLOTHING: "옷",
  OTHER: "기타",
};

// 예전 결과의 sections key. 나머지 key 는 제목 없음
// TODO(Q-26): 일반 운세 key 제목은 일반 결과 adapter 때 정한다
export const SECTION_TITLES: Record<string, string> = {
  SUMMARY: "요약",
  EXAM_DAY: "시험 당일",
  EXAM_PERIODS: "교시별 안내",
  FOCUS: "집중 팁",
  MEAL: "추천 음식",
  PREPARATION: "준비물",
  ANXIETY_MANAGEMENT: "마음 관리",
  MISSING_ELEMENT: "부족한 오행",
};

// 서버 코드가 "constructor" 같은 객체 기본 키여도 이름으로 착각하지 않게 자기 키만 본다
export function labelOf(
  labels: Readonly<Record<string, string>>,
  key: string,
): string | null {
  return Object.hasOwn(labels, key) ? labels[key] : null;
}
