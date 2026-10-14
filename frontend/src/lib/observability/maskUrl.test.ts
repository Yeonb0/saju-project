import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/screens";
import { maskUrl } from "./maskUrl";

// 이 파일의 토큰 · 주문번호 · 금액은 픽스처일 뿐, 실제 형식 · 가격과 무관하다.

describe("maskUrl", () => {
  it("선물 토큰 fragment를 제거하고, 절대 URL 은 origin 을 유지한다", () => {
    expect(maskUrl("/g#token=abc123")).toBe("/g");
    expect(maskUrl("https://x.vercel.app/g#token=abc123")).toBe(
      "https://x.vercel.app/g",
    );
  });

  it("동적 경로를 패턴으로 바꾼다", () => {
    expect(maskUrl("/suneung/r/r_1")).toBe("/suneung/r/[readingId]");
    expect(maskUrl("/fortune/love/questions")).toBe(
      "/fortune/[type]/questions",
    );
    expect(maskUrl("/me/people/p9")).toBe("/me/people/[id]");
    expect(maskUrl("/gift/done/o1")).toBe("/gift/done/[orderId]");
    expect(maskUrl("/talisman/t1")).toBe("/talisman/[id]");
    expect(maskUrl("/share/s1")).toBe("/share/[shareId]");
  });

  it("정적 경로는 동적 패턴보다 먼저 맞아 그대로 둔다", () => {
    expect(maskUrl("/suneung")).toBe("/suneung");
    expect(maskUrl("/me/people/new")).toBe("/me/people/new");
    expect(maskUrl("/gift/new")).toBe("/gift/new");
    expect(maskUrl("/wallet")).toBe("/wallet");
  });

  it("결제 복귀 쿼리를 지운다", () => {
    expect(maskUrl("/pay/success?paymentKey=pk&orderId=o&amount=1")).toBe(
      "/pay/success",
    );
  });

  it("ROUTES 의 모든 동적 경로가 임의 값을 넣어도 원래 패턴으로 돌아온다", () => {
    const dynamic = Object.keys(ROUTES).filter((p) => p.includes("["));
    expect(dynamic.length).toBeGreaterThan(0);
    for (const pattern of dynamic) {
      const filled = pattern.replace(/\[[^\]]+\]/g, "zz9");
      expect(maskUrl(filled), filled).toBe(pattern);
    }
  });

  it("표에 없는 경로는 그대로 두고 쿼리만 지운다", () => {
    expect(maskUrl("/unknown/abc?x=1")).toBe("/unknown/abc");
  });
});

describe("maskUrl — API 경로의 UUID (관측 도구)", () => {
  // 픽스처일 뿐이며 실제 ID 와 무관하다
  const ID = "11111111-2222-4333-8444-555555555555";
  const UPPER = "AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE";

  it("q. /api/v1/quotes/<uuid> 는 /api/v1/quotes/[id]", () => {
    expect(maskUrl(`/api/v1/quotes/${ID}`)).toBe("/api/v1/quotes/[id]");
  });

  it("r. 대문자 UUID 도 가리고 쿼리를 지운다", () => {
    expect(maskUrl(`/api/v1/top-up-orders/${UPPER}?paymentKey=x`)).toBe(
      "/api/v1/top-up-orders/[id]",
    );
  });

  it("s. UUID 가 없는 API 경로는 그대로", () => {
    expect(maskUrl("/api/v1/wallet")).toBe("/api/v1/wallet");
  });

  it("t. 절대 URL 은 origin 을 남기고 경로만 가린다", () => {
    expect(maskUrl(`https://example.com/api/v1/readings/${ID}`)).toBe(
      "https://example.com/api/v1/readings/[id]",
    );
  });
});
