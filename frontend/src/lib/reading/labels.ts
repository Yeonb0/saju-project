// 결과 화면 이름. PD 확정 (FUNCTIONAL_SPEC 8장 v0.4, Q-40 · Q-45) — 서버가 제목을 주지 않는다 (Q-37).
// 근거: RESULT-GUIDE-MODEL, API_SPEC 8장 "수능운 결과 구조", liner/suneung-response.schema.json (교시 · 분류 enum).
import type { PreparationCategory, SuneungGuide } from "@/lib/ports/reading";

// 요약 카드 제목만 인물 이름이 들어간다
export function suneungSummaryTitle(personDisplayName: string): string {
  return `${personDisplayName}님의 수능 운세`;
}

// 요약을 뺀 7개는 고정 문자열. 키가 빠지면 typecheck 가 깨진다
export const SUNEUNG_GUIDE_TITLES: Record<
  Exclude<keyof SuneungGuide, "summary">,
  string
> = {
  examDayGuide: "시험 날 하루 흐름",
  examPeriods: "교시별 응원",
  focusTips: "집중 꿀팁",
  foodRecommendations: "수능 도시락",
  preparationChecklist: "준비물 체크리스트",
  anxietyManagement: "마음 다스리기",
  missingElementAdvice: "부족한 기운 & 수능 부적",
};

// 키는 liner/suneung-response.schema.json 의 enum. API_SPEC 8장 예시의 다른 코드는 넣지 않는다
export const EXAM_PERIOD_LABELS: Record<string, string> = {
  BEFORE_ENTRY: "입실 전",
  KOREAN: "1교시 국어",
  MATH: "2교시 수학",
  LUNCH: "점심시간",
  ENGLISH: "3교시 영어",
  ELECTIVES: "4 · 5교시 한국사 · 탐구 · 제2외국어",
  FINAL_CHECK: "마지막 점검",
};

// 준비물 묶음. 화면 순서 그대로이고, 모든 알려진 분류가 정확히 한 묶음에 들어간다
export const PREPARATION_GROUPS: readonly Readonly<{
  id: string;
  title: string;
  categories: readonly Exclude<PreparationCategory, "UNKNOWN">[];
}>[] = [
  { id: "DOCUMENT", title: "꼭 챙기기", categories: ["DOCUMENT"] },
  {
    id: "EXAM_KIT",
    title: "시험장 소지품",
    categories: ["WRITING_TOOL", "TIMEPIECE"],
  },
  { id: "FOOD", title: "도시락 · 간식", categories: ["FOOD"] },
  { id: "CLOTHING", title: "옷차림", categories: ["CLOTHING"] },
  { id: "OTHER", title: "그 밖에 챙길 것", categories: ["OTHER"] },
];

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
