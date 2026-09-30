import { existsSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ROUTELESS_SCREENS, ROUTES, SCREENS } from "./screens";

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

  it("ROUTES 가 가리키는 화면 번호가 모두 SCREENS 에 있다", () => {
    for (const [path, route] of Object.entries(ROUTES)) {
      for (const no of route.screens) {
        expect(no in SCREENS, `${path} #${no}`).toBe(true);
      }
    }
  });

  it("라우트 없는 화면(5·40)을 뺀 모든 화면이 어느 경로에 들어 있다", () => {
    const used = new Set<number>(
      Object.values(ROUTES).flatMap((route) => [...route.screens]),
    );
    const routeless = new Set(Object.keys(ROUTELESS_SCREENS).map(Number));
    for (let no = 1; no <= 40; no++) {
      if (routeless.has(no)) continue;
      expect(used.has(no), `#${no}`).toBe(true);
    }
  });

  it("SCREENS 에 1~40 이 빠짐없이 있다", () => {
    const nos = Object.keys(SCREENS)
      .map(Number)
      .sort((a, b) => a - b);
    expect(nos).toEqual(Array.from({ length: 40 }, (_, i) => i + 1));
  });
});
