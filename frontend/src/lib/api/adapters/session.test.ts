import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, ApiNetworkError } from "@/lib/api/errors";
import { createRealSession } from "./session";

// 픽스처일 뿐이며 실제 사용자 · 토큰과 무관하다
const USER_ID = "77777777-7777-4777-8777-777777777777";
const TRACE = "fixture-trace";

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

const signedIn = (overrides: Record<string, unknown> = {}) => ({
  authenticated: true,
  userId: USER_ID,
  nickname: "FIXTURE-NICK",
  hasPrimaryPerson: true,
  csrfToken: "FIXTURE-CSRF",
  expiresAt: "2030-01-01T00:00:00Z",
  ...overrides,
});

const signedOut = {
  authenticated: false,
  userId: null,
  nickname: null,
  hasPrimaryPerson: null,
  csrfToken: null,
};

function setup(...responses: Response[]) {
  const fetchMock = vi.fn<typeof fetch>();
  for (const response of responses) fetchMock.mockResolvedValueOnce(response);
  const assign = vi.fn<(url: string) => void>();
  const session = createRealSession({ fetch: fetchMock, assign });
  return { ...session, fetchMock, assign };
}

describe("세션 adapter — getSession", () => {
  it("비로그인 응답이면 signed_out 이고 토큰은 null", async () => {
    const { port, csrf, fetchMock } = setup(ok(signedOut));
    await expect(port.getSession()).resolves.toEqual({ status: "signed_out" });
    expect(await csrf.current()).toBeNull();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/session");
    expect(init?.method).toBe("GET");
  });

  it("로그인 응답이면 signed_in 값 그대로, 토큰은 응답 토큰", async () => {
    const { port, csrf } = setup(ok(signedIn()));
    await expect(port.getSession()).resolves.toEqual({
      status: "signed_in",
      userId: USER_ID,
      nickname: "FIXTURE-NICK",
      hasPrimaryPerson: true,
    });
    expect(await csrf.current()).toBe("FIXTURE-CSRF");
  });

  it("로그인 뒤 비로그인 응답을 받으면 토큰을 지운다", async () => {
    const { port, csrf } = setup(ok(signedIn()), ok(signedOut));
    await port.getSession();
    expect(await csrf.current()).toBe("FIXTURE-CSRF");
    await expect(port.getSession()).resolves.toEqual({ status: "signed_out" });
    expect(await csrf.current()).toBeNull();
  });

  it.each([
    "userId",
    "hasPrimaryPerson",
    "csrfToken",
  ])("authenticated=true 인데 %s 가 null 이면 ApiContractError", async (key) => {
    const { port } = setup(ok(signedIn({ [key]: null })));
    await expect(port.getSession()).rejects.toBeInstanceOf(ApiContractError);
  });

  it("JSON 이 아닌 502 는 ApiError (status 502, code null)", async () => {
    const { port } = setup(
      new Response("<html>Bad Gateway</html>", {
        status: 502,
        headers: { "content-type": "text/html" },
      }),
    );
    const error = await port.getSession().catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.code).toBeNull();
  });
});

describe("세션 adapter — csrf.refresh", () => {
  it("GET /api/v1/session 을 한 번 부르고 새 토큰을 돌려준다", async () => {
    const { csrf, fetchMock } = setup(
      ok(signedIn({ csrfToken: "FIXTURE-CSRF-NEW" })),
    );
    await expect(csrf.refresh()).resolves.toBe("FIXTURE-CSRF-NEW");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/session");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("GET");
  });
});

describe("세션 adapter — startLogin", () => {
  it("KAKAO 는 assign 을 정확한 URL 로 한 번 부른다 (쿼리 · 한글 인코딩)", async () => {
    const { port, assign, fetchMock } = setup();
    await port.startLogin("KAKAO", "/result/가나?a=1&b=2", vi.fn());
    expect(assign).toHaveBeenCalledTimes(1);
    expect(assign).toHaveBeenCalledWith(
      `/api/v1/auth/kakao/authorize?returnTo=${encodeURIComponent("/result/가나?a=1&b=2")}`,
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("외부 주소 returnTo 는 safeReturnTo 결과로 바뀐다", async () => {
    const { port, assign } = setup();
    await port.startLogin("KAKAO", "https://evil.example/x", vi.fn());
    expect(assign).toHaveBeenCalledWith(
      `/api/v1/auth/kakao/authorize?returnTo=${encodeURIComponent("/")}`,
    );
  });

  it.each([
    "NAVER",
    "GOOGLE",
  ] as const)("%s 는 던지고 assign · fetch 를 부르지 않는다", async (provider) => {
    const { port, assign, fetchMock } = setup();
    await expect(port.startLogin(provider, "/", vi.fn())).rejects.toThrow();
    expect(assign).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("세션 adapter — logout", () => {
  it("토큰이 없으면 GET /session 다음 POST /auth/logout, X-CSRF-Token 만 보내고 성공 뒤 토큰 null", async () => {
    const { port, csrf, fetchMock } = setup(
      ok(signedIn()),
      new Response(null, { status: 204 }),
    );
    await port.logout();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/session");
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/v1/auth/logout");
    expect(init?.method).toBe("POST");
    const headers = init?.headers as Record<string, string>;
    expect(headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    expect(headers["Idempotency-Key"]).toBeUndefined();
    expect(init?.body).toBeUndefined();
    expect(await csrf.current()).toBeNull();
  });

  it("403 CSRF_FAILED 면 refresh 후 같은 POST 를 1회 재시도한다", async () => {
    const { port, fetchMock } = setup(
      ok(signedIn()),
      failure(403, "CSRF_FAILED"),
      ok(signedIn({ csrfToken: "FIXTURE-CSRF-NEW" })),
      new Response(null, { status: 204 }),
    );
    await port.logout();
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/auth/logout");
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/session");
    expect(fetchMock.mock.calls[3][0]).toBe("/api/v1/auth/logout");
    const headers = fetchMock.mock.calls[3][1]?.headers as Record<
      string,
      string
    >;
    expect(headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF-NEW");
  });

  it("fetch 가 거절되면 ApiNetworkError", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(ok(signedIn()))
      .mockRejectedValueOnce(new TypeError("network"));
    const { port } = createRealSession({ fetch: fetchMock, assign: vi.fn() });
    await expect(port.logout()).rejects.toBeInstanceOf(ApiNetworkError);
  });
});
