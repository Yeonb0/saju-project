import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, classifyApiError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import { ELEMENTS } from "@/lib/ports/basicSaju";
import { createBasicSajuAdapter } from "./basicSaju";

// 픽스처일 뿐이며 실제 인물 · 계산 결과와 무관하다
const PERSON_ID = "66666666-6666-4666-8666-666666666666";
const TRACE = "fixture-trace";

const resultData = (overrides: Record<string, unknown> = {}) => ({
  fiveElements: {
    counts: { WATER: 1, WOOD: 3, METAL: 0, FIRE: 2, EARTH: 2 },
    dominant: ["WOOD"],
    missing: ["METAL"],
  },
  birthTimeKnown: true,
  calculationVersion: "FIXTURE-1",
  pillars: { year: "FIXTURE" },
  dayMaster: { hangul: "FIXTURE" },
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
  const adapter = createBasicSajuAdapter(
    createApiClient({ csrf, fetch: fetchMock }),
  );
  return { adapter, fetchMock };
}

describe("오행분석 adapter — 요청", () => {
  it("POST /api/v1/fortune/basic 에 { personId } 만, CSRF 헤더, 멱등 키 없음", async () => {
    const { adapter, fetchMock } = setup(ok(resultData()));
    await adapter.getBasicSaju(PERSON_ID);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/fortune/basic");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ personId: PERSON_ID });
    const headers = init?.headers as Record<string, string>;
    expect(headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    expect(headers["Idempotency-Key"]).toBeUndefined();
  });
});

describe("오행분석 adapter — 성공", () => {
  it("counts 를 ELEMENTS 순서로 바꾸고 나머지는 그대로, 다른 필드가 있어도 성공", async () => {
    const { adapter } = setup(ok(resultData()));
    const saju = await adapter.getBasicSaju(PERSON_ID);
    expect(saju.fiveElements.map((e) => e.element)).toEqual([...ELEMENTS]);
    expect(saju.fiveElements).toEqual([
      { element: "WOOD", count: 3 },
      { element: "FIRE", count: 2 },
      { element: "EARTH", count: 2 },
      { element: "METAL", count: 0 },
      { element: "WATER", count: 1 },
    ]);
    expect(saju.birthTimeKnown).toBe(true);
    expect(saju.calculationVersion).toBe("FIXTURE-1");
  });
});

describe("오행분석 adapter — 계약 위반은 ApiContractError", () => {
  const counts = (c: Record<string, unknown>) =>
    resultData({ fiveElements: { counts: c } });
  const full = { WOOD: 1, FIRE: 1, EARTH: 1, METAL: 1, WATER: 1 };

  it.each([
    ["counts 에 WATER 없음", counts({ WOOD: 1, FIRE: 1, EARTH: 1, METAL: 1 })],
    ["counts 에 모르는 키", counts({ ...full, SKY: 1 })],
    ["count 가 -1", counts({ ...full, WOOD: -1 })],
    ["count 가 1.5", counts({ ...full, WOOD: 1.5 })],
    ["birthTimeKnown 없음", resultData({ birthTimeKnown: undefined })],
    ["calculationVersion 빈 문자열", resultData({ calculationVersion: "" })],
    ["fiveElements 없음", resultData({ fiveElements: undefined })],
  ])("%s", async (_name, data) => {
    const { adapter } = setup(ok(data));
    await expect(adapter.getBasicSaju(PERSON_ID)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("오행분석 adapter — 오류는 그대로 전달", () => {
  it("422 BIRTH_TIME_REQUIRED_AT_TERM", async () => {
    const { adapter } = setup(failure(422, "BIRTH_TIME_REQUIRED_AT_TERM"));
    const error = await adapter.getBasicSaju(PERSON_ID).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(classifyApiError(error)).toBe("birth_time_required");
  });

  it("404 RESOURCE_NOT_FOUND", async () => {
    const { adapter } = setup(failure(404, "RESOURCE_NOT_FOUND"));
    const error = await adapter.getBasicSaju(PERSON_ID).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
  });

  it("503 READING_FULFILLMENT_UNAVAILABLE", async () => {
    const { adapter } = setup(failure(503, "READING_FULFILLMENT_UNAVAILABLE"));
    const error = await adapter.getBasicSaju(PERSON_ID).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(classifyApiError(error)).toBe("server");
  });
});
