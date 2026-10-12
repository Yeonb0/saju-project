// 결과 포트의 가짜 구현 — 개발 서버 · Vercel 미리보기 전용 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 가짜 운세 구매가 끝나면 create 로 결과를 만들고, 화면은 getReading 으로 재열람한다 (같은 탭 메모리).
// 아래 내용 · 이름 · 날짜는 픽스처일 뿐이며 실제 문구 · 규칙과 무관하다 (결과 문장은 서버 · Liner 가 만든다, S-06).
import { ApiError } from "@/lib/api/errors";
import type { FortuneProduct } from "@/lib/ports/fortune";
import type { Reading, ReadingBody, ReadingPort } from "@/lib/ports/reading";

const TRACE = "fixture-trace";

// 픽스처일 뿐이며 실제 문구와 무관하다. 모르는 교시 코드 · 모르는 준비물 분류를 하나씩 지나가게 둔다 (RESULT-GUIDE-MODEL)
const FIXTURE_SUNEUNG_BODY: ReadingBody = {
  kind: "SUNEUNG_GUIDE",
  guide: {
    summary: "FIXTURE 요약",
    examDayGuide: "FIXTURE 시험 당일",
    examPeriods: [
      { period: "KOREAN", guidance: "FIXTURE 국어 안내" },
      { period: "FIXTURE_PERIOD", guidance: "FIXTURE 모르는 교시 안내" },
    ],
    focusTips: ["FIXTURE 집중 팁 1", "FIXTURE 집중 팁 2"],
    foodRecommendations: [
      { item: "FIXTURE 음식 1", reason: "FIXTURE 이유 1" },
      { item: "FIXTURE 음식 2", reason: "FIXTURE 이유 2" },
    ],
    preparationChecklist: [
      { id: "fixture-a", label: "FIXTURE 준비물 1", category: "DOCUMENT" },
      { id: "fixture-b", label: "FIXTURE 준비물 2", category: "WRITING_TOOL" },
      { id: "fixture-c", label: "FIXTURE 준비물 3", category: "UNKNOWN" },
    ],
    anxietyManagement: ["FIXTURE 마음 관리 1"],
    missingElementAdvice: "FIXTURE 부족한 오행",
  },
};

// 픽스처일 뿐이며 실제 섹션 구성과 무관하다 — 일반 운세 구성은 Q-35 · Q-05 확정 후. 예전 결과 모양(SECTIONS)이다
const FIXTURE_GENERAL_BODY: ReadingBody = {
  kind: "SECTIONS",
  sections: [
    { type: "TEXT", key: "SUMMARY", content: "FIXTURE 요약" },
    { type: "TEXT", key: "CURRENT_FLOW", content: "FIXTURE 풀이" },
    { type: "UNKNOWN", rawKey: "FIXTURE_NEW_KEY" },
  ],
};

export type FakeReadings = {
  port: ReadingPort;
  create(product: FortuneProduct, now?: Date): string;
};

export function createFakeReadings(): FakeReadings {
  const readings = new Map<string, Reading>();

  return {
    port: {
      async getReading(readingId) {
        const found = readings.get(readingId);
        if (!found) {
          throw new ApiError({
            status: 404,
            code: "READING_NOT_FOUND",
            traceId: TRACE,
          });
        }
        return found;
      },
    },
    create(product, now = new Date()) {
      const id = crypto.randomUUID();
      readings.set(id, {
        id,
        fortuneType: product.fortuneType,
        productOption: product.option,
        // 픽스처일 뿐이며 실제 사용자와 무관하다
        personDisplayName: "FIXTURE",
        // 픽스처 날짜 — 실제 시험일과 무관하다
        event:
          product.fortuneType === "SUNEUNG"
            ? { type: "SUNEUNG", date: "2000-01-01" }
            : null,
        body:
          product.fortuneType === "SUNEUNG"
            ? FIXTURE_SUNEUNG_BODY
            : FIXTURE_GENERAL_BODY,
        disclaimers: ["FOR_ENTERTAINMENT"],
        createdAt: now.toISOString(),
      });
      return id;
    },
  };
}
