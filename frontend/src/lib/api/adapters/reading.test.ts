import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import { createReadingAdapter } from "./reading";

// 이 파일의 ID · 문장 · 이름은 픽스처일 뿐이며 실제 결과와 무관하다
const READING_ID = "66666666-6666-4666-8666-666666666666";
const TRACE = "fixture-trace";

const guideData = (overrides: Record<string, unknown> = {}) => ({
  summary: "FIXTURE 요약",
  examDayGuide: "FIXTURE 시험 당일",
  examPeriods: [
    { period: "KOREAN", guidance: "FIXTURE 국어" },
    { period: "FIXTURE_PERIOD", guidance: "FIXTURE 모르는 교시" },
  ],
  focusTips: ["FIXTURE 팁 1", "FIXTURE 팁 2"],
  foodRecommendations: [
    { item: "FIXTURE 음식 1", reason: "FIXTURE 이유 1" },
    { item: "FIXTURE 음식 2", reason: "FIXTURE 이유 2" },
  ],
  preparationChecklist: [
    { id: "fixture-a", label: "FIXTURE 준비물 a", category: "DOCUMENT" },
    { id: "fixture-b", label: "FIXTURE 준비물 b", category: "WRITING_TOOL" },
  ],
  anxietyManagement: ["FIXTURE 마음 관리"],
  missingElementAdvice: "FIXTURE 부족한 오행",
  ...overrides,
});

const readingData = (overrides: Record<string, unknown> = {}) => ({
  id: READING_ID,
  fortuneType: "SUNEUNG",
  productOption: "READING_WITH_TALISMAN",
  subjectDisplayName: "FIXTURE-NAME",
  event: { type: "SUNEUNG", date: "2000-01-01" },
  sections: [
    { key: "SUMMARY", content: "FIXTURE 섹션 요약", sourceFactKeys: ["F1"] },
  ],
  suneungGuide: guideData(),
  createdAt: "2000-01-01T00:00:00Z",
  disclaimers: ["FOR_ENTERTAINMENT"],
  // 포트에 넣지 않는 값 (보관하지 않는다)
  counterpart: { displayName: "FIXTURE" },
  questionKey: "FIXTURE",
  talisman: { fixture: true },
  calculationVersion: "FIXTURE-1",
  generationVersion: "FIXTURE-1",
  contentVersion: "FIXTURE-1",
  generationMode: "FIXTURE",
  ...overrides,
});

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const ok = (data: unknown) => json(200, { data, traceId: TRACE });
const failure = (status: number, code: string) =>
  json(status, {
    code,
    message: "픽스처 문구",
    traceId: TRACE,
    fieldErrors: [],
  });

function setup(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(response);
  const csrf: CsrfSource = {
    current: vi.fn<CsrfSource["current"]>().mockResolvedValue("FIXTURE-CSRF"),
    refresh: vi.fn<CsrfSource["refresh"]>().mockResolvedValue("FIXTURE-CSRF-2"),
  };
  const adapter = createReadingAdapter(
    createApiClient({ csrf, fetch: fetchMock }),
  );
  return { adapter, fetchMock };
}

describe("결과 adapter — 요청", () => {
  it("GET /api/v1/readings/<id>, CSRF · 멱등 키 헤더 없음", async () => {
    const { adapter, fetchMock } = setup(ok(readingData()));
    await adapter.getReading(READING_ID);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`/api/v1/readings/${READING_ID}`);
    expect(init?.method).toBe("GET");
    const headers = init?.headers as Record<string, string>;
    expect(headers["X-CSRF-Token"]).toBeUndefined();
    expect(headers["Idempotency-Key"]).toBeUndefined();
  });

  it("id 에 특수문자가 있으면 인코딩한다", async () => {
    const { adapter, fetchMock } = setup(ok(readingData()));
    await adapter.getReading("a/b?c#d");
    expect(fetchMock.mock.calls[0][0]).toBe(
      `/api/v1/readings/${encodeURIComponent("a/b?c#d")}`,
    );
  });
});

describe("결과 adapter — 성공", () => {
  it("가이드 결과를 포트 모델로 옮긴다 — 이름 변경 · 순서 유지 · 무시한 필드 없음", async () => {
    const { adapter } = setup(ok(readingData()));
    await expect(adapter.getReading(READING_ID)).resolves.toEqual({
      id: READING_ID,
      fortuneType: "SUNEUNG",
      productOption: "READING_WITH_TALISMAN",
      personDisplayName: "FIXTURE-NAME",
      event: { type: "SUNEUNG", date: "2000-01-01" },
      body: {
        kind: "SUNEUNG_GUIDE",
        guide: {
          summary: "FIXTURE 요약",
          examDayGuide: "FIXTURE 시험 당일",
          examPeriods: [
            { period: "KOREAN", guidance: "FIXTURE 국어" },
            { period: "FIXTURE_PERIOD", guidance: "FIXTURE 모르는 교시" },
          ],
          focusTips: ["FIXTURE 팁 1", "FIXTURE 팁 2"],
          foodRecommendations: [
            { item: "FIXTURE 음식 1", reason: "FIXTURE 이유 1" },
            { item: "FIXTURE 음식 2", reason: "FIXTURE 이유 2" },
          ],
          preparationChecklist: [
            {
              id: "fixture-a",
              label: "FIXTURE 준비물 a",
              category: "DOCUMENT",
            },
            {
              id: "fixture-b",
              label: "FIXTURE 준비물 b",
              category: "WRITING_TOOL",
            },
          ],
          anxietyManagement: ["FIXTURE 마음 관리"],
          missingElementAdvice: "FIXTURE 부족한 오행",
        },
      },
      disclaimers: ["FOR_ENTERTAINMENT"],
      createdAt: "2000-01-01T00:00:00Z",
    });
  });

  it.each([
    "DOCUMENT",
    "WRITING_TOOL",
    "TIMEPIECE",
    "FOOD",
    "CLOTHING",
    "OTHER",
  ])("준비물 분류 %s 는 그대로", async (category) => {
    const { adapter } = setup(
      ok(
        readingData({
          suneungGuide: guideData({
            preparationChecklist: [{ id: "x", label: "FIXTURE", category }],
          }),
        }),
      ),
    );
    const { body } = await adapter.getReading(READING_ID);
    if (body.kind !== "SUNEUNG_GUIDE") throw new Error("가이드가 아니다");
    expect(body.guide.preparationChecklist[0].category).toBe(category);
  });

  it("모르는 준비물 분류는 UNKNOWN (OTHER 가 아니다)", async () => {
    const { adapter } = setup(
      ok(
        readingData({
          suneungGuide: guideData({
            preparationChecklist: [
              { id: "x", label: "FIXTURE", category: "FIXTURE_CATEGORY" },
            ],
          }),
        }),
      ),
    );
    const { body } = await adapter.getReading(READING_ID);
    if (body.kind !== "SUNEUNG_GUIDE") throw new Error("가이드가 아니다");
    expect(body.guide.preparationChecklist[0].category).toBe("UNKNOWN");
  });

  it("가이드 목록이 빈 배열이어도 성공", async () => {
    const { adapter } = setup(
      ok(
        readingData({
          suneungGuide: guideData({
            examPeriods: [],
            focusTips: [],
            foodRecommendations: [],
            preparationChecklist: [],
            anxietyManagement: [],
          }),
        }),
      ),
    );
    const { body } = await adapter.getReading(READING_ID);
    expect(body.kind).toBe("SUNEUNG_GUIDE");
  });

  it("suneungGuide 가 null 이면 SECTIONS — 아는 key 는 TEXT, 모르는 key 는 UNKNOWN, 서버 순서 유지", async () => {
    const { adapter } = setup(
      ok(
        readingData({
          suneungGuide: null,
          event: null,
          sections: [
            { key: "CURRENT_FLOW", content: "FIXTURE 본문 1" },
            { key: "FIXTURE_NEW_KEY", content: "FIXTURE 본문 2" },
            { key: "SUMMARY", content: "FIXTURE 본문 3" },
          ],
        }),
      ),
    );
    const reading = await adapter.getReading(READING_ID);
    expect(reading.event).toBeNull();
    expect(reading.body).toEqual({
      kind: "SECTIONS",
      sections: [
        { type: "TEXT", key: "CURRENT_FLOW", content: "FIXTURE 본문 1" },
        { type: "UNKNOWN", rawKey: "FIXTURE_NEW_KEY" },
        { type: "TEXT", key: "SUMMARY", content: "FIXTURE 본문 3" },
      ],
    });
  });

  it("객체 기본 키(constructor)인 섹션 key 는 UNKNOWN", async () => {
    const { adapter } = setup(
      ok(
        readingData({
          suneungGuide: null,
          sections: [{ key: "constructor", content: "FIXTURE" }],
        }),
      ),
    );
    const { body } = await adapter.getReading(READING_ID);
    expect(body).toEqual({
      kind: "SECTIONS",
      sections: [{ type: "UNKNOWN", rawKey: "constructor" }],
    });
  });

  it("fortuneType · productOption 모르는 값은 UNKNOWN 으로 성공", async () => {
    const { adapter } = setup(
      ok(
        readingData({
          fortuneType: "FIXTURE_TYPE",
          productOption: "FIXTURE_OPTION",
        }),
      ),
    );
    const reading = await adapter.getReading(READING_ID);
    expect(reading.fortuneType).toBe("UNKNOWN");
    expect(reading.productOption).toBe("UNKNOWN");
  });
});

describe("결과 adapter — 실패", () => {
  it("404 READING_NOT_FOUND 는 ApiError 로 status · code 그대로", async () => {
    const { adapter } = setup(failure(404, "READING_NOT_FOUND"));
    const error = await adapter.getReading(READING_ID).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.code).toBe("READING_NOT_FOUND");
  });

  it.each([
    ["id 가 uuid 아님", { id: "not-a-uuid" }],
    ["subjectDisplayName 빈 문자열", { subjectDisplayName: "" }],
    ["suneungGuide 키 없음", { suneungGuide: undefined }],
    ["summary 빈 문자열", { suneungGuide: guideData({ summary: "" }) }],
    [
      "examPeriods 항목 guidance 빈 문자열",
      {
        suneungGuide: guideData({
          examPeriods: [{ period: "KOREAN", guidance: "" }],
        }),
      },
    ],
    [
      "준비물 id 중복",
      {
        suneungGuide: guideData({
          preparationChecklist: [
            { id: "dup", label: "FIXTURE 1", category: "DOCUMENT" },
            { id: "dup", label: "FIXTURE 2", category: "OTHER" },
          ],
        }),
      },
    ],
    ["createdAt 날짜 아님", { createdAt: "not-a-date" }],
    ["disclaimers 가 배열 아님", { disclaimers: "FOR_ENTERTAINMENT" }],
    [
      "sections 항목 content 빈 문자열",
      { sections: [{ key: "SUMMARY", content: "" }] },
    ],
  ])("계약 위반 (%s) 은 ApiContractError", async (_name, overrides) => {
    const { adapter } = setup(ok(readingData(overrides)));
    await expect(adapter.getReading(READING_ID)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});
