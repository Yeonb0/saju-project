// @vitest-environment jsdom
// 브라우저 저장소(sessionStorage · localStorage)를 쓰는 테스트라 jsdom 에서 돈다 (vitest.config.mts — .ts 는 기본 node).
import { afterEach, describe, expect, it } from "vitest";
import { checklistStorageKey, loadChecked, saveChecked } from "./checklist";

afterEach(() => localStorage.clear());

// 픽스처일 뿐이며 실제 결과와 무관하다
const ID = "88888888-8888-4888-8888-888888888888";

describe("수능 준비물 체크 저장 (localStorage)", () => {
  it("저장한 항목 ID 를 읽는다 — 중복은 하나로", () => {
    saveChecked(ID, "C", ["a", "b", "a"]);
    expect(loadChecked(ID, "C")).toEqual(["a", "b"]);
  });

  it("결과 ID · 섹션별로 따로 둔다", () => {
    saveChecked(ID, "C", ["a"]);
    expect(loadChecked(ID, "D")).toEqual([]);
    expect(loadChecked("other", "C")).toEqual([]);
  });

  it("값이 깨졌으면 빈 상태로 시작한다", () => {
    localStorage.setItem(checklistStorageKey(ID, "C"), "{broken");
    expect(loadChecked(ID, "C")).toEqual([]);
    localStorage.setItem(checklistStorageKey(ID, "C"), JSON.stringify([1, 2]));
    expect(loadChecked(ID, "C")).toEqual([]);
  });
});
