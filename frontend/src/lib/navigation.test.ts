import { describe, expect, it } from "vitest";
import { isFortuneSlug, isMenuGroup, MENU } from "./navigation";
import { ROUTES } from "./screens";

describe("사이드 메뉴 항목", () => {
  it("모든 href 가 ROUTES 의 키다 (/fortune/<type> 은 /fortune/[type])", () => {
    const hrefs = MENU.flatMap((entry) =>
      isMenuGroup(entry) ? entry.items.map((i) => i.href) : [entry.href],
    );
    for (const href of hrefs) {
      const route = href.startsWith("/fortune/") ? "/fortune/[type]" : href;
      expect(route in ROUTES, href).toBe(true);
    }
  });

  it("a. 순서 · 그룹 · href 가 HOME-04 와이어 그대로다 (취업운 없음)", () => {
    const shape = MENU.map((entry) =>
      isMenuGroup(entry)
        ? [entry.label, entry.items.map((i) => [i.label, i.href])]
        : [entry.label, entry.href],
    );
    expect(shape).toEqual([
      ["홈", "/"],
      ["마이페이지", "/me"],
      ["내 부적 창고", "/vault"],
      ["오늘의 운세", "/today"],
      [
        "유료 운세",
        [
          ["애정운", "/fortune/love"],
          ["재물운", "/fortune/wealth"],
          ["종합운", "/fortune/overall"],
          ["신살", "/fortune/sinsal"],
          ["궁합", "/fortune/compatibility"],
        ],
      ],
      ["이벤트 운세", [["수능운", "/suneung"]]],
    ]);
  });
});

describe("isFortuneSlug (FORT-01)", () => {
  it("j. 5개 slug 만 true 이고 대소문자 · 공백 · 다른 값은 false", () => {
    for (const slug of [
      "love",
      "wealth",
      "overall",
      "sinsal",
      "compatibility",
    ]) {
      expect(isFortuneSlug(slug), slug).toBe(true);
    }
    for (const value of ["SUNEUNG", "suneung", "", "Love", "love "]) {
      expect(isFortuneSlug(value), JSON.stringify(value)).toBe(false);
    }
  });
});
