import { describe, expect, it } from "vitest";
import { ApiError, classifyApiError } from "@/lib/api/errors";
import { ELEMENTS } from "@/lib/ports/basicSaju";
import { createFakeAccount } from "./account";
import { createFakeBasicSajuPort } from "./basicSaju";

// 픽스처일 뿐이며 실제 계산 · 규칙과 무관하다. 값은 src/mocks/basicSaju.ts 의 픽스처다.
const EXPECTED_COUNTS = [2, 1, 3, 0, 2];

async function caught(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error("던지지 않았다");
}

describe("가짜 오행분석 포트 (MOCK-PORT)", () => {
  it("a. 본인 personId 면 ELEMENTS 순서 5개와 픽스처 count 를 준다", async () => {
    const account = createFakeAccount("signed_in");
    const port = createFakeBasicSajuPort(account);
    const result = await port.getBasicSaju(account.getSelf()?.personId ?? "");
    expect(result.fiveElements.map((e) => e.element)).toEqual([...ELEMENTS]);
    expect(result.fiveElements.map((e) => e.count)).toEqual(EXPECTED_COUNTS);
    expect(result.birthTimeKnown).toBe(true);
  });

  it("b. 저장된 타인 personId 로도 값을 준다", async () => {
    const account = createFakeAccount("signed_in_with_other");
    const port = createFakeBasicSajuPort(account);
    const other = account.getOthers()[0];
    expect(other).toBeDefined();
    const result = await port.getBasicSaju(other.personId);
    expect(result.fiveElements.map((e) => e.count)).toEqual(EXPECTED_COUNTS);
  });

  it("c. 없는 personId 는 404 PERSON_NOT_FOUND", async () => {
    const port = createFakeBasicSajuPort(createFakeAccount("signed_in"));
    const error = await caught(
      port.getBasicSaju("99999999-9999-4999-8999-999999999999"),
    );
    expect(error.status).toBe(404);
    expect(error.code).toBe("PERSON_NOT_FOUND");
  });

  it("d. 로그인하지 않았으면 401 AUTHENTICATION_REQUIRED", async () => {
    const account = createFakeAccount("signed_out");
    const port = createFakeBasicSajuPort(account);
    const error = await caught(
      port.getBasicSaju(account.getSelf()?.personId ?? ""),
    );
    expect(error.status).toBe(401);
    expect(error.code).toBe("AUTHENTICATION_REQUIRED");
  });

  it("e. 절기 경계일 시간 미상이면 422 BIRTH_TIME_REQUIRED_AT_TERM, 분류는 birth_time_required", async () => {
    const account = createFakeAccount("signed_in");
    const port = createFakeBasicSajuPort(account, {
      failure: "birth_time_required",
    });
    const error = await caught(
      port.getBasicSaju(account.getSelf()?.personId ?? ""),
    );
    expect(error.status).toBe(422);
    expect(error.code).toBe("BIRTH_TIME_REQUIRED_AT_TERM");
    expect(classifyApiError(error)).toBe("birth_time_required");
  });

  it("f. 받은 배열을 고쳐도 다음 호출 값은 픽스처 그대로다", async () => {
    const account = createFakeAccount("signed_in");
    const port = createFakeBasicSajuPort(account);
    const personId = account.getSelf()?.personId ?? "";
    const first = await port.getBasicSaju(personId);
    (first.fiveElements as { element: string; count: number }[])[0].count = 99;
    (first.fiveElements as unknown[]).length = 0;
    const second = await port.getBasicSaju(personId);
    expect(second.fiveElements.map((e) => e.count)).toEqual(EXPECTED_COUNTS);
  });
});
