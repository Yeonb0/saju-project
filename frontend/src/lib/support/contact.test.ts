import { describe, expect, it } from "vitest";
import { BUSINESS_INFO } from "@/lib/business";
import { type SupportRef, supportMailtoHref } from "./contact";

// 메일 제목을 꺼낸다 — 받는 주소와 subject 두 부분으로만 이뤄져야 한다
function parse(href: string | null): { to: string; subject: string } {
  if (href === null) throw new Error("mailto 가 null 이다");
  const match = /^mailto:([^?]+)\?subject=([^&]*)$/.exec(href);
  if (!match) throw new Error(`mailto 모양이 아니다: ${href}`);
  return { to: match[1], subject: decodeURIComponent(match[2]) };
}

describe("supportMailtoHref (FUNCTIONAL_SPEC 13장 · Q-38 g · Q-47)", () => {
  // 픽스처일 뿐이며 실제 주문 · 구매 · 견적 번호와 무관하다
  it.each<[string, SupportRef | undefined, string]>([
    [
      "충전 주문번호",
      { kind: "topUpOrder", id: "FIXTURE-1" },
      "[뿌기사주 문의] 충전 주문번호 FIXTURE-1",
    ],
    [
      "운세 구매번호",
      { kind: "purchase", id: "FIXTURE-2" },
      "[뿌기사주 문의] 운세 구매번호 FIXTURE-2",
    ],
    [
      "운세 견적번호",
      { kind: "quote", id: "FIXTURE-3" },
      "[뿌기사주 문의] 운세 견적번호 FIXTURE-3",
    ],
    ["번호 없음", undefined, "[뿌기사주 문의]"],
  ])("%s 제목", (_name, ref, subject) => {
    const parsed = parse(supportMailtoHref(ref));
    expect(parsed.subject).toBe(subject);
    expect(parsed.to).toBe(BUSINESS_INFO.email);
  });

  it("번호에 & · # · 공백이 있어도 subject 밖으로 새지 않는다", () => {
    // 픽스처일 뿐이며 실제 번호와 무관하다
    const href = supportMailtoHref({ kind: "purchase", id: "a&b #c" });
    expect(parse(href).subject).toBe("[뿌기사주 문의] 운세 구매번호 a&b #c");
    expect(href).not.toContain("#");
    expect(href?.split("&")).toHaveLength(1);
  });

  it("본문(body)은 넣지 않는다", () => {
    expect(supportMailtoHref()).not.toContain("body=");
  });
});
