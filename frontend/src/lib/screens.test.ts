import { existsSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  NOT_BUILT_SCREENS,
  ROUTELESS_SCREENS,
  ROUTES,
  SCREENS,
} from "./screens";

const APP_DIR = join(process.cwd(), "src", "app");

const pageFileOf = (path: string) =>
  path === "/" ? join(APP_DIR, "page.tsx") : join(APP_DIR, path, "page.tsx");

function findPageRoutes(dir: string, prefix = ""): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    if (entry.isDirectory()) {
      return findPageRoutes(join(dir, entry.name), `${prefix}/${entry.name}`);
    }
    return entry.name === "page.tsx" ? [prefix === "" ? "/" : prefix] : [];
  });
}

describe("screens · routes", () => {
  it("ROUTES 의 모든 경로에 page.tsx 가 있다", () => {
    for (const path of Object.keys(ROUTES)) {
      expect(existsSync(pageFileOf(path)), path).toBe(true);
    }
  });

  it("src/app 의 모든 page.tsx 가 ROUTES 에 있다", () => {
    const routePaths = new Set(Object.keys(ROUTES));
    for (const path of findPageRoutes(APP_DIR)) {
      expect(routePaths.has(path), path).toBe(true);
    }
  });

  it("화면 ID 는 최종 와이어 프레임 이름 형식이다 (FIGMA-FINAL)", () => {
    for (const id of Object.keys(SCREENS)) {
      expect(id).toMatch(
        /^(HOME|TODAY|MY|TALBOX|FORT|MATCH|PAY|CSAT|GIFT|RECV|RECV-T)-\d{2}$/,
      );
    }
  });

  it("ROUTES 가 가리키는 화면 ID 가 모두 SCREENS 에 있다", () => {
    for (const [path, route] of Object.entries(ROUTES)) {
      for (const id of route.screens) {
        expect(id in SCREENS, `${path} ${id}`).toBe(true);
      }
    }
  });

  it("모든 화면은 경로 · 라우트 없는 화면 · 만들지 않는 화면 중 하나에 있다", () => {
    const placed = new Set<string>([
      ...Object.values(ROUTES).flatMap((route) => [...route.screens]),
      ...Object.keys(ROUTELESS_SCREENS),
      ...Object.keys(NOT_BUILT_SCREENS),
    ]);
    for (const id of Object.keys(SCREENS)) {
      expect(placed.has(id), id).toBe(true);
    }
  });

  it("만들지 않는 화면은 어느 경로에도 없다", () => {
    const routed = new Set<string>(
      Object.values(ROUTES).flatMap((route) => [...route.screens]),
    );
    for (const id of Object.keys(NOT_BUILT_SCREENS)) {
      expect(routed.has(id), id).toBe(false);
    }
  });
});
