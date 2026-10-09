import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, ApiNetworkError } from "./errors";
import { type CsrfSource, createApiClient } from "./http";
import { createIdempotentCommand } from "./idempotency";

// 픽스처일 뿐이며 실제 API · 가격 · 토큰 · 규칙과 무관하다. 경로 · code · traceId 는 분기를 보기 위한 임의 값이다.
const TRACE = "fixture-trace";

function json(status: number, body: unknown, headers?: Record<string, string>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", ...headers },
  });
}

function errorBody(code: string) {
  return { code, message: "픽스처 문구", traceId: TRACE, fieldErrors: [] };
}

function setup(responses: Array<Response | Error>) {
  const fetchMock = vi.fn<typeof fetch>();
  for (const r of responses) {
    if (r instanceof Error) fetchMock.mockRejectedValueOnce(r);
    else fetchMock.mockResolvedValueOnce(r);
  }
  const csrf = {
    current: vi.fn<CsrfSource["current"]>().mockResolvedValue("csrf-1"),
    refresh: vi.fn<CsrfSource["refresh"]>().mockResolvedValue("csrf-2"),
  };
  const client = createApiClient({ csrf, fetch: fetchMock });
  const sent = (i: number) => {
    const init = fetchMock.mock.calls[i][1] ?? {};
    return {
      path: fetchMock.mock.calls[i][0],
      headers: init.headers as Record<string, string>,
      body: init.body,
    };
  };
  return { client, fetchMock, csrf, sent };
}

describe("성공 응답", () => {
  it("껍데기에서 data · traceId 를 꺼낸다", async () => {
    const { client } = setup([json(200, { data: { a: 1 }, traceId: TRACE })]);
    await expect(
      client.request({ method: "GET", path: "/api/v1/fixture" }),
    ).resolves.toEqual({ status: 200, data: { a: 1 }, traceId: TRACE });
  });

  it("204 는 본문 없이 null", async () => {
    const { client } = setup([new Response(null, { status: 204 })]);
    await expect(
      client.request({ method: "DELETE", path: "/api/v1/fixture" }),
    ).resolves.toEqual({ status: 204, data: null, traceId: null });
  });

  it("2xx 인데 껍데기가 다르면 계약 오류로 던진다 (조용히 넘어가지 않음)", async () => {
    const { client } = setup([json(200, { a: 1 })]);
    await expect(
      client.request({ method: "GET", path: "/api/v1/fixture" }),
    ).rejects.toBeInstanceOf(ApiContractError);
  });
});

describe("오류 응답", () => {
  it("code · status · traceId 를 담고, Error.message 에 서버 문구를 넣지 않는다", async () => {
    const { client } = setup([json(409, errorBody("INSUFFICIENT_BALANCE"))]);
    const error = await client
      .request({ method: "GET", path: "/api/v1/fixture" })
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe("INSUFFICIENT_BALANCE");
    expect(apiError.traceId).toBe(TRACE);
    expect(apiError.message).not.toContain("픽스처 문구");
  });

  it("JSON 이 아닌 오류 본문(게이트웨이)은 code 없이 status 로", async () => {
    const { client } = setup([
      new Response("<html>Bad Gateway</html>", {
        status: 502,
        headers: { "content-type": "text/html" },
      }),
    ]);
    const error = (await client
      .request({ method: "GET", path: "/api/v1/fixture" })
      .catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBeNull();
    expect(error.status).toBe(502);
  });

  it("JSON 오류인데 껍데기가 다르면 계약 오류", async () => {
    const { client } = setup([json(400, { error: "x" })]);
    await expect(
      client.request({ method: "GET", path: "/api/v1/fixture" }),
    ).rejects.toBeInstanceOf(ApiContractError);
  });

  it("429 의 Retry-After 를 초로 담는다", async () => {
    const { client } = setup([
      json(429, errorBody("RATE_LIMITED"), { "retry-after": "12" }),
    ]);
    const error = (await client
      .request({ method: "GET", path: "/api/v1/fixture" })
      .catch((e: unknown) => e)) as ApiError;
    expect(error.retryAfterSeconds).toBe(12);
  });

  it("네트워크 실패는 결과 불명확 오류", async () => {
    const { client } = setup([new TypeError("network")]);
    await expect(
      client.request({ method: "GET", path: "/api/v1/fixture" }),
    ).rejects.toBeInstanceOf(ApiNetworkError);
  });
});

describe("멱등 명령", () => {
  it("Idempotency-Key 와 만들 때 고정한 본문을 그대로 보낸다", async () => {
    const command = createIdempotentCommand({ productCode: "FIXTURE" });
    const { client, sent } = setup([json(201, { data: {}, traceId: TRACE })]);
    await client.request({
      method: "POST",
      path: "/api/v1/fixture",
      command,
    });
    expect(sent(0).headers["Idempotency-Key"]).toBe(command.key);
    expect(sent(0).body).toBe(command.body);
    expect(sent(0).headers["X-CSRF-Token"]).toBe("csrf-1");
  });
});

describe("CSRF (Q-02)", () => {
  it("403 CSRF_FAILED 면 세션을 한 번 다시 불러 같은 키 · 같은 본문으로 1회 재시도한다", async () => {
    const command = createIdempotentCommand({ productCode: "FIXTURE" });
    const { client, csrf, fetchMock, sent } = setup([
      json(403, errorBody("CSRF_FAILED")),
      json(200, { data: { ok: true }, traceId: TRACE }),
    ]);
    const result = await client.request({
      method: "POST",
      path: "/api/v1/fixture",
      command,
    });
    expect(result.data).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(csrf.refresh).toHaveBeenCalledTimes(1);
    expect(sent(1).headers["X-CSRF-Token"]).toBe("csrf-2");
    expect(sent(1).headers["Idempotency-Key"]).toBe(command.key);
    expect(sent(1).body).toBe(sent(0).body);
  });

  it("재시도도 CSRF_FAILED 면 더 보내지 않고 던진다", async () => {
    const { client, csrf, fetchMock } = setup([
      json(403, errorBody("CSRF_FAILED")),
      json(403, errorBody("CSRF_FAILED")),
    ]);
    const error = (await client
      .request({ method: "POST", path: "/api/v1/fixture", json: { a: 1 } })
      .catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe("CSRF_FAILED");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(csrf.refresh).toHaveBeenCalledTimes(1);
  });

  it("json 본문도 재시도에 같은 문자열을 보낸다", async () => {
    const { client, sent } = setup([
      json(403, errorBody("CSRF_FAILED")),
      json(200, { data: null, traceId: TRACE }),
    ]);
    await client.request({
      method: "POST",
      path: "/api/v1/fixture",
      json: { a: 1 },
    });
    expect(sent(1).body).toBe(sent(0).body);
    expect(sent(1).headers["Idempotency-Key"]).toBeUndefined();
  });

  it("403 FORBIDDEN 은 재시도하지 않는다", async () => {
    const { client, csrf, fetchMock } = setup([
      json(403, errorBody("FORBIDDEN")),
    ]);
    await expect(
      client.request({ method: "POST", path: "/api/v1/fixture", json: {} }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(csrf.refresh).not.toHaveBeenCalled();
  });

  it("GET 은 CSRF 토큰을 묻지도 보내지도 않는다", async () => {
    const { client, csrf, sent } = setup([
      json(200, { data: null, traceId: TRACE }),
    ]);
    await client.request({ method: "GET", path: "/api/v1/fixture" });
    expect(csrf.current).not.toHaveBeenCalled();
    expect(sent(0).headers["X-CSRF-Token"]).toBeUndefined();
  });
});

describe("요청 검사", () => {
  it("같은 출처 /api/v1/ 밖의 경로는 던진다", async () => {
    const { client, fetchMock } = setup([]);
    await expect(
      client.request({ method: "GET", path: "https://example.com/api/v1/x" }),
    ).rejects.toThrow();
    await expect(
      client.request({ method: "GET", path: "/api/v2/x" }),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("GET 에 본문, command 와 json 동시 지정은 던진다", async () => {
    const { client } = setup([]);
    const command = createIdempotentCommand({});
    await expect(
      client.request({ method: "GET", path: "/api/v1/x", command }),
    ).rejects.toThrow();
    await expect(
      client.request({
        method: "POST",
        path: "/api/v1/x",
        command,
        json: {},
      }),
    ).rejects.toThrow();
  });
});
