import { describe, expect, it } from "vitest";
import { isMenuGroup, MENU } from "./navigation";
import { ROUTES } from "./screens";

describe("사이드 메뉴 항목", () => {
  it("모든 href 가 ROUTES 의 키다", () => {
    const hrefs = MENU.flatMap((entry) =>
      isMenuGroup(entry) ? entry.items.map((i) => i.href) : [entry.href],
    );
    for (const href of hrefs) {
      expect(href in ROUTES, href).toBe(true);
    }
  });

  it("순서가 홈 · 마이페이지 · 내 부적 창고 · 오늘의 운세 · (유료 운세: 수능운) 이다", () => {
    const shape = MENU.map((entry) =>
      isMenuGroup(entry)
        ? [entry.label, entry.items.map((i) => i.label)]
        : entry.label,
    );
    expect(shape).toEqual([
      "홈",
      "마이페이지",
      "내 부적 창고",
      "오늘의 운세",
      ["유료 운세", ["수능운"]],
    ]);
  });
});
