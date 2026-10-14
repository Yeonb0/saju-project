// 선택 질문 표 한 곳 — 화면 · 가짜 구현 · 구매 선택 복원이 이 파일만 본다 (키 목록을 다른 파일에 다시 쓰지 않는다).
// 사용자 문구: FUNCTIONAL_SPEC v0.4 8장 "정보·질문" 표 (PD 확정). 서버 값(questionKey): API_SPEC 8장 "허용 questionKey" 표.
// 관계 값(relationType): OpenAPI CompatibilityQuoteRequest. 근거: Q-26.
import type { FortuneType, RelationType } from "@/lib/ports/fortune";

export type QuestionOption = Readonly<{ key: string; label: string }>;

export type QuestionSet = Readonly<{
  title: string;
  options: readonly QuestionOption[];
}>;

export type QuestionFortuneType =
  | "OVERALL"
  | "LOVE"
  | "WEALTH"
  | "SINSAL"
  | "COMPATIBILITY";

export const QUESTION_SETS: Readonly<Record<QuestionFortuneType, QuestionSet>> =
  {
    OVERALL: {
      title: "요즘 가장 궁금한 건?",
      options: [
        { key: "OVERALL_FLOW", label: "전체 흐름" },
        { key: "RELATIONSHIPS", label: "인간관계" },
        { key: "STUDY_AND_WORK", label: "학업 · 일" },
        { key: "WEALTH", label: "돈" },
        { key: "CONDITION", label: "컨디션" },
      ],
    },
    WEALTH: {
      title: "요즘 돈 고민은?",
      options: [
        { key: "OVERALL_WEALTH", label: "전체 재물 흐름" },
        { key: "INCOME", label: "수입 늘리기" },
        { key: "SPENDING", label: "지출 관리" },
        { key: "INVESTMENT", label: "재테크 · 투자" },
        { key: "CAREER_FINANCE", label: "진로와 돈" },
      ],
    },
    SINSAL: {
      title: "어떤 부분이 신경 쓰여요?",
      options: [
        { key: "OVERALL_SINSAL", label: "전체적으로" },
        { key: "RELATIONSHIPS", label: "인간관계" },
        { key: "WORK_AND_STUDY", label: "학업 · 일" },
        { key: "WEALTH", label: "돈" },
      ],
    },
    LOVE: {
      title: "애정운에서 무엇이 궁금한가요?",
      options: [
        { key: "CURRENT_RELATIONSHIP", label: "지금 관계" },
        { key: "NEW_RELATIONSHIP", label: "새로운 인연" },
        { key: "RECONCILIATION", label: "재회" },
        { key: "MARRIAGE", label: "결혼" },
      ],
    },
    COMPATIBILITY: {
      title: "가장 궁금한 건?",
      options: [
        { key: "OVERALL_MATCH", label: "우리 잘 맞을까?" },
        { key: "COMMUNICATION", label: "대화 · 소통" },
        { key: "CONFLICT", label: "다툼 줄이는 법" },
        { key: "LONG_TERM_POTENTIAL", label: "앞으로의 관계" },
      ],
    },
  };

export const RELATION_QUESTION: Readonly<{
  title: string;
  options: readonly Readonly<{ key: RelationType; label: string }>[];
}> = {
  title: "두 사람은 어떤 사이예요?",
  options: [
    { key: "LOVER", label: "연인" },
    { key: "CRUSH", label: "썸 · 짝사랑" },
    { key: "FRIEND", label: "친구" },
    { key: "FAMILY", label: "가족" },
    { key: "WORK_SCHOOL", label: "직장 · 학교" },
    { key: "OTHER", label: "기타" },
  ],
};

// 그 운세 표에 있는 키인가. 수능운 · UNKNOWN 은 질문이 없어 항상 false
export function isQuestionKeyOf(
  fortuneType: FortuneType,
  key: string,
): boolean {
  if (!Object.hasOwn(QUESTION_SETS, fortuneType)) return false;
  return QUESTION_SETS[fortuneType as QuestionFortuneType].options.some(
    (option) => option.key === key,
  );
}
