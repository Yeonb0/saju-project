import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/screens";
import { maskUrl } from "./maskUrl";

// 이 파일의 토큰 · 주문번호 · 금액은 픽스처일 뿐, 실제 형식 · 가격과 무관하다.

describe("maskUrl", () => {
  it("선물 토큰을 패턴으로 바꾸고, 절대 URL 은 origin 을 유지하며 쿼리 · 해시를 지운다", () => {
    expect(maskUrl("/g/abc123")).toBe("/g/[token]");
    expect(maskUrl("https://x.vercel.app/g/abc123?ref=kakao#top")).toBe(
      "https://x.vercel.app/g/[token]",
    );
  });

  it("동적 경로를 패턴으로 바꾼다", () => {
    expect(maskUrl("/suneung/r/r_1")).toBe("/suneung/r/[readingId]");
    expect(maskUrl("/fortune/love/checkout")).toBe("/fortune/[type]/checkout");
    expect(maskUrl("/me/people/p9")).toBe("/me/people/[id]");
    expect(maskUrl("/gift/done/o1")).toBe("/gift/done/[orderId]");
    expect(maskUrl("/talisman/t1")).toBe("/talisman/[id]");
    expect(maskUrl("/share/s1")).toBe("/share/[shareId]");
  });

  it("정적 경로는 동적 패턴보다 먼저 맞아 그대로 둔다", () => {
    expect(maskUrl("/suneung/checkout")).toBe("/suneung/checkout");
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
