import type { Breadcrumb, ErrorEvent } from "@sentry/nextjs";
import type { CaptureResult } from "posthog-js";
import { describe, expect, it } from "vitest";
import { posthogOptions, sentryOptions } from "./config";

// 이 파일의 토큰 · 주문번호 · 금액은 픽스처일 뿐, 실제 형식 · 가격과 무관하다.

describe("sentryOptions", () => {
  const options = sentryOptions("https://dsn.example/1", "development");

  it("개인정보 · 트레이싱 기본값", () => {
    expect(options.sendDefaultPii).toBe(false);
    expect(options.tracesSampleRate).toBe(0);
  });

  it("beforeSend 가 request.url 을 가린다", () => {
    const event = {
      request: { url: "https://x.vercel.app/g/abc123?ref=kakao" },
    } as ErrorEvent;
    expect(options.beforeSend(event).request?.url).toBe(
      "https://x.vercel.app/g/[token]",
    );
  });

  it("beforeSend 가 Referer 헤더를 가리고 query_string 을 지운다", () => {
    const event = {
      request: {
        url: "https://x.vercel.app/",
        headers: { Referer: "https://x.vercel.app/g/abc123?x=1" },
        query_string: "x=1",
      },
    } as unknown as ErrorEvent;
    const masked = options.beforeSend(event);
    expect(masked.request?.headers?.Referer).toBe(
      "https://x.vercel.app/g/[token]",
    );
    expect(masked.request?.query_string).toBeUndefined();
  });

  it("beforeBreadcrumb 가 data.url 을 가린다", () => {
    const breadcrumb = { data: { url: "/g/abc123?x=1" } } as Breadcrumb;
    expect(options.beforeBreadcrumb(breadcrumb).data?.url).toBe("/g/[token]");
  });
});

describe("posthogOptions", () => {
  const options = posthogOptions("https://ph.example");

  it("자동 수집 · 녹화 · 프로필 설정", () => {
    expect(options.autocapture).toBe(false);
    expect(options.disable_session_recording).toBe(true);
    expect(options.person_profiles).toBe("identified_only");
    expect(options.capture_pageview).toBe("history_change");
  });

  const before = options.before_send as (
    cr: CaptureResult | null,
  ) => CaptureResult | null;

  it("before_send 가 $set_once.$initial_current_url 과 properties.$initial_pathname 을 가린다", () => {
    const result = {
      uuid: "u",
      event: "$pageview",
      properties: { $initial_pathname: "/g/abc123" },
      $set_once: {
        $initial_current_url: "https://x.vercel.app/g/abc123?ref=kakao",
      },
    } as unknown as CaptureResult;
    const masked = before(result);
    expect(masked?.properties.$initial_pathname).toBe("/g/[token]");
    expect(masked?.$set_once?.$initial_current_url).toBe(
      "https://x.vercel.app/g/[token]",
    );
  });

  it('before_send 가 $referrer = "$direct" 는 그대로 둔다', () => {
    const result = {
      uuid: "u",
      event: "$pageview",
      properties: { $referrer: "$direct" },
    } as CaptureResult;
    expect(before(result)?.properties.$referrer).toBe("$direct");
  });

  it("$set · $set_once 가 없는 결과도 오류 없이 통과한다", () => {
    const result = {
      uuid: "u",
      event: "custom",
      properties: { $current_url: "/g/abc123" },
    } as CaptureResult;
    expect(() => before(result)).not.toThrow();
    expect(before(result)?.properties.$current_url).toBe("/g/[token]");
  });

  it("before_send 가 $current_url 을 가린다", () => {
    const result = {
      uuid: "u",
      event: "$pageview",
      properties: { $current_url: "https://x.vercel.app/g/abc123?ref=kakao" },
    } as CaptureResult;
    expect(before(result)?.properties.$current_url).toBe(
      "https://x.vercel.app/g/[token]",
    );
  });
});
