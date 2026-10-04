import { describe, expect, it } from "vitest";
import Page from "./page";

// 픽스처일 뿐이며 실제 사용자 · 규칙과 무관하다.
function props(query: Record<string, string | string[]>) {
  return {
    params: Promise.resolve({}),
    searchParams: Promise.resolve(query),
  } as PageProps<"/login">;
}

describe("/login page", () => {
  it("내부 returnTo 는 그대로 LoginScreen 에 넘긴다", async () => {
    const element = await Page(props({ returnTo: "/wallet" }));
    expect(element.props.returnTo).toBe("/wallet");
  });

  it("바깥 주소는 / 로 바꾼다", async () => {
    const element = await Page(props({ returnTo: "//evil.example" }));
    expect(element.props.returnTo).toBe("/");
  });

  it("배열이면 무시하고 /", async () => {
    const element = await Page(props({ returnTo: ["/a", "/b"] }));
    expect(element.props.returnTo).toBe("/");
  });

  it("없으면 /", async () => {
    const element = await Page(props({}));
    expect(element.props.returnTo).toBe("/");
  });
});
