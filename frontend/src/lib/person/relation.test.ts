import { describe, expect, it } from "vitest";
import { RELATION_TYPES } from "@/lib/ports/fortune";
import { RELATION_TYPE_OF_KIND } from "./relation";
import { RELATIONS } from "./schema";

describe("화면 관계 → 서버 관계 표 (A-07 · Q-35)", () => {
  it("a. 네 종류 매핑이 API_SPEC 4장 표와 같다", () => {
    expect(RELATION_TYPE_OF_KIND).toEqual({
      mother: "FAMILY",
      father: "FAMILY",
      partner: "LOVER",
      custom: "OTHER",
    });
  });

  it("a2. 화면 관계 네 종류를 빠짐없이 덮고 값은 모두 RELATION_TYPES 안에 있다", () => {
    expect(Object.keys(RELATION_TYPE_OF_KIND).sort()).toEqual(
      [...RELATIONS].sort(),
    );
    for (const value of Object.values(RELATION_TYPE_OF_KIND)) {
      expect(RELATION_TYPES).toContain(value);
    }
  });
});
