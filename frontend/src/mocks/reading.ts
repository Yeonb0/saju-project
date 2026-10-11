// 결과 포트의 가짜 구현 — 개발 서버 · Vercel 미리보기 전용 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 가짜 운세 구매가 끝나면 create 로 결과를 만들고, 화면은 getReading 으로 재열람한다 (같은 탭 메모리).
// 아래 섹션 내용 · 이름 · 날짜는 픽스처일 뿐이며 실제 문구 · 규칙과 무관하다 (결과 문장은 서버 · Liner 가 만든다, S-06).
import { ApiError } from "@/lib/api/errors";
import type { FortuneProduct } from "@/lib/ports/fortune";
import type { Reading, ReadingPort, ReadingSection } from "@/lib/ports/reading";

const TRACE = "fixture-trace";

// 픽스처일 뿐이며 실제 섹션 구성 · 문구와 무관하다. 타입 네 가지와 모르는 타입 하나를 모두 지나가게 둔다. 수능 전용 타입은 SUNEUNG 에만 둔다.
const FIXTURE_SUNEUNG_SECTIONS: readonly ReadingSection[] = [
  { key: "SUMMARY", type: "TEXT", title: "FIXTURE 요약", content: "FIXTURE" },
  {
    key: "PERIODS",
    type: "PERIOD_GUIDANCE",
    title: "FIXTURE 시간대",
    items: [
      { label: "FIXTURE 1", guidance: "FIXTURE", focusPoint: "FIXTURE" },
      { label: "FIXTURE 2", guidance: "FIXTURE", focusPoint: null },
    ],
  },
  {
    key: "MEAL",
    type: "FOOD_RECOMMENDATION",
    title: "FIXTURE 음식",
    primary: { name: "FIXTURE 음식 1", reason: "FIXTURE" },
    alternatives: [{ name: "FIXTURE 음식 2", reason: "FIXTURE" }],
  },
  {
    key: "PREPARATION",
    type: "CHECKLIST",
    title: "FIXTURE 준비물",
    items: [
      { id: "fixture-a", label: "FIXTURE 준비물 1", personalized: false },
      { id: "fixture-b", label: "FIXTURE 준비물 2", personalized: true },
    ],
  },
  // 서버가 나중에 새 타입을 보내는 경우 (Q-05)
  { key: "FIXTURE_NEW", type: "UNKNOWN", rawType: "FIXTURE_NEW_TYPE" },
];

// 픽스처일 뿐이며 실제 섹션 구성과 무관하다 — 일반 운세 구성은 Q-35 · Q-05 확정 후. 수능 전용 섹션을 섞지 않는다
const FIXTURE_GENERAL_SECTIONS: readonly ReadingSection[] = [
  { key: "SUMMARY", type: "TEXT", title: "FIXTURE 요약", content: "FIXTURE" },
  { key: "DETAIL", type: "TEXT", title: "FIXTURE 풀이", content: "FIXTURE" },
  { key: "FIXTURE_NEW", type: "UNKNOWN", rawType: "FIXTURE_NEW_TYPE" },
];

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
        birthTimeUnknown: false,
        // 픽스처 날짜 — 실제 시험일과 무관하다
        event:
          product.fortuneType === "SUNEUNG"
            ? { type: "SUNEUNG", date: "2000-01-01" }
            : null,
        sections:
          product.fortuneType === "SUNEUNG"
            ? FIXTURE_SUNEUNG_SECTIONS
            : FIXTURE_GENERAL_SECTIONS,
        disclaimers: ["FOR_ENTERTAINMENT"],
        createdAt: now.toISOString(),
      });
      return id;
    },
  };
}
