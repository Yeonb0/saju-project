import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { loadPolicy } from "./load";

// 픽스처일 뿐이며 실제 약관 · 규칙과 무관하다.
let dir: string;
beforeEach(() => {
  dir = mkdtempSync(path.join(tmpdir(), "policy-"));
});
afterEach(() => rmSync(dir, { recursive: true, force: true }));

describe("loadPolicy", () => {
  it("파일이 없으면 null", () => {
    expect(loadPolicy("terms", dir)).toBeNull();
  });

  it("정상 원고는 PolicyDoc", () => {
    writeFileSync(
      path.join(dir, "terms.md"),
      "# 제목\n\n## 조\n\n글\n",
      "utf8",
    );
    const doc = loadPolicy("terms", dir);
    expect(doc?.sections).toHaveLength(1);
    expect(doc?.sections[0].id).toBe("article-1");
  });

  it("잘못된 원고는 던진다", () => {
    writeFileSync(
      path.join(dir, "refund.md"),
      "# 제목\n\n## 조\n\n[자리표시]\n",
      "utf8",
    );
    expect(() => loadPolicy("refund", dir)).toThrow(/5줄/);
  });
});
