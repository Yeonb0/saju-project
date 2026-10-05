import { describe, expect, it } from "vitest";
import Page from "./page";

// 픽스처일 뿐이며 실제 사용자 · 규칙과 무관하다.
function props(query: Record<string, string | string[]>) {
  return {
    params: Promise.resolve({}),
    searchParams: Promise.resolve(query),
  } as PageProps<"/onboarding">;
}

// 페이지는 RequireSession 으로 OnboardingScreen 을 감싼다 — 렌더하지 않고 요소 트리의 children 에서 returnTo prop 을 읽는다.
async function returnToOf(query: Record<string, string | string[]>) {
  const element = await Page(props(query));
  expect(element.props.requirePerson).toBe(false);
  return element.props.children.props.returnTo;
}

describe("/onboarding page", () => {
  it("내부 returnTo 는 그대로 넘긴다", async () => {
    expect(await returnToOf({ returnTo: "/wallet" })).toBe("/wallet");
  });

  it("바깥 주소는 / 로 바꾼다", async () => {
    expect(await returnToOf({ returnTo: "//evil.example" })).toBe("/");
  });

  it("배열이면 무시하고 /", async () => {
    expect(await returnToOf({ returnTo: ["/a", "/b"] })).toBe("/");
  });

  it("없으면 /", async () => {
    expect(await returnToOf({})).toBe("/");
  });
});
