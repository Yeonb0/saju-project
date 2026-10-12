import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import dayjs from "@/lib/date";
import {
  createPersonSchema,
  type PersonFormInput,
  type PersonFormOutput,
  type PersonKind,
} from "@/lib/person/schema";
import { createPersonAdapter } from "./person";

// 픽스처일 뿐이며 실제 인물과 무관하다
const PERSON_ID = "88888888-8888-4888-8888-888888888888";
const TRACE = "fixture-trace";
const TODAY = dayjs("2026-10-12");

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

const created = (profileType: "SELF" | "OTHER" = "SELF") => ({
  id: PERSON_ID,
  profileType,
  displayName: "FIXTURE-NAME",
});

function form(
  kind: PersonKind,
  overrides: Partial<PersonFormInput> = {},
): PersonFormOutput {
  const base: PersonFormInput = {
    name: "FIXTURE-NAME",
    calendar: "solar",
    isLeapMonth: false,
    birthDate: "1990-01-02",
    timeUnknown: false,
    birthTime: "03:04",
    gender: "female",
    relation: kind === "other" ? "mother" : null,
    relationText: "",
    permissionConfirmed: kind === "other",
    ...overrides,
  };
  return createPersonSchema({ kind, today: TODAY }).parse(base);
}

function setup(...responses: Response[]) {
  const fetchMock = vi.fn<typeof fetch>();
  for (const response of responses) fetchMock.mockResolvedValueOnce(response);
  const csrf: CsrfSource = {
    current: vi.fn<CsrfSource["current"]>().mockResolvedValue("FIXTURE-CSRF"),
    refresh: vi.fn<CsrfSource["refresh"]>().mockResolvedValue("FIXTURE-CSRF-2"),
  };
  const adapter = createPersonAdapter(
    createApiClient({ csrf, fetch: fetchMock }),
  );
  return { adapter, fetchMock };
}

const sentBody = (fetchMock: ReturnType<typeof setup>["fetchMock"]) =>
  JSON.parse(String(fetchMock.mock.calls[0][1]?.body));

describe("인물 adapter — createSelf", () => {
  it("본문 전체: relationType 키 없음, thirdPartyAuthorizationConfirmed false", async () => {
    const { adapter, fetchMock } = setup(ok(created()));
    await adapter.createSelf(form("self"));
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/people");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("POST");
    expect(sentBody(fetchMock)).toEqual({
      profileType: "SELF",
      displayName: "FIXTURE-NAME",
      birthDate: "1990-01-02",
      birthTime: "03:04",
      birthTimeUnknown: false,
      calendarType: "SOLAR",
      isLeapMonth: false,
      gender: "FEMALE",
      thirdPartyAuthorizationConfirmed: false,
    });
  });

  it("POST 에 X-CSRF-Token, Idempotency-Key 없음", async () => {
    const { adapter, fetchMock } = setup(ok(created()));
    await adapter.createSelf(form("self"));
    const headers = fetchMock.mock.calls[0][1]?.headers as Record<
      string,
      string
    >;
    expect(headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    expect(headers["Idempotency-Key"]).toBeUndefined();
  });

  it("relation 이 있는 입력은 던지고 fetch 안 함", async () => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.createSelf(form("other"))).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("시간 미상이면 birthTime 키 없음, birthTimeUnknown true", async () => {
    const { adapter, fetchMock } = setup(ok(created()));
    await adapter.createSelf(form("self", { timeUnknown: true }));
    const body = sentBody(fetchMock);
    expect("birthTime" in body).toBe(false);
    expect(body.birthTimeUnknown).toBe(true);
  });

  it("음력 · 윤달이면 LUNAR · isLeapMonth true", async () => {
    const { adapter, fetchMock } = setup(ok(created()));
    await adapter.createSelf(
      form("self", { calendar: "lunar", isLeapMonth: true }),
    );
    const body = sentBody(fetchMock);
    expect(body.calendarType).toBe("LUNAR");
    expect(body.isLeapMonth).toBe(true);
  });

  it.each([
    ["male", "MALE"],
    ["female", "FEMALE"],
    ["unspecified", "UNSPECIFIED"],
  ] as const)("성별 %s → %s", async (gender, expected) => {
    const { adapter, fetchMock } = setup(ok(created()));
    await adapter.createSelf(form("self", { gender }));
    expect(sentBody(fetchMock).gender).toBe(expected);
  });
});

describe("인물 adapter — createOther", () => {
  it.each([
    ["mother", "FAMILY"],
    ["father", "FAMILY"],
    ["partner", "LOVER"],
    ["custom", "OTHER"],
  ] as const)("관계 %s → %s, thirdPartyAuthorizationConfirmed true, 직접 입력 이름은 보내지 않음", async (relation, expected) => {
    const { adapter, fetchMock } = setup(ok(created("OTHER")));
    await adapter.createOther(
      form("other", { relation, relationText: "FIXTURE-LABEL" }),
    );
    const body = sentBody(fetchMock);
    expect(body.profileType).toBe("OTHER");
    expect(body.relationType).toBe(expected);
    expect(body.thirdPartyAuthorizationConfirmed).toBe(true);
    expect(JSON.stringify(body)).not.toContain("FIXTURE-LABEL");
  });

  it("relation 이 없는 입력은 던지고 fetch 안 함", async () => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.createOther(form("self"))).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("409 PERSON_LIMIT_EXCEEDED 는 ApiError 로 code 그대로", async () => {
    const { adapter } = setup(failure(409, "PERSON_LIMIT_EXCEEDED"));
    const error = await adapter.createOther(form("other")).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.code).toBe("PERSON_LIMIT_EXCEEDED");
  });

  it("생성 응답이 계약과 다르면 ApiContractError", async () => {
    const { adapter } = setup(ok({ ...created("OTHER"), id: "not-a-uuid" }));
    await expect(adapter.createOther(form("other"))).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("인물 adapter — list", () => {
  const item = (id: string, profileType: string, name: string) => ({
    id,
    profileType,
    displayName: name,
  });
  const A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const C = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

  it("서버가 OTHER, SELF, OTHER 순이어도 SELF 가 맨 앞, 나머지 순서 유지", async () => {
    const { adapter, fetchMock } = setup(
      ok({
        items: [
          item(A, "OTHER", "FIXTURE-A"),
          item(B, "SELF", "FIXTURE-B"),
          item(C, "OTHER", "FIXTURE-C"),
        ],
      }),
    );
    const people = await adapter.list();
    expect(fetchMock.mock.calls[0][1]?.method).toBe("GET");
    expect(people).toEqual([
      { personId: B, isSelf: true, name: "FIXTURE-B" },
      { personId: A, isSelf: false, name: "FIXTURE-A" },
      { personId: C, isSelf: false, name: "FIXTURE-C" },
    ]);
  });

  it.each([
    ["id 가 uuid 아님", { items: [item("not-a-uuid", "SELF", "X")] }],
    ["profileType 모르는 값", { items: [item(A, "UNKNOWN", "X")] }],
    ["items 없음", {}],
  ])("계약 위반 (%s) 은 ApiContractError", async (_name, data) => {
    const { adapter } = setup(ok(data));
    await expect(adapter.list()).rejects.toBeInstanceOf(ApiContractError);
  });
});
