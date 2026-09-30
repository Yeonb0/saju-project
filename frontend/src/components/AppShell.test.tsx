import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { AppShell } from "./AppShell";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

const LINKS = [
  ["홈", "/"],
  ["마이페이지", "/me"],
  ["내 부적 창고", "/vault"],
  ["오늘의 운세", "/today"],
  ["수능운", "/suneung"],
] as const;

async function openMenu() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "메뉴 열기" }));
  return user;
}

describe("AppShell 사이드 메뉴", () => {
  it("처음엔 닫혀 있고, 열면 링크 5개가 각자의 href 로 보인다", async () => {
    render(<AppShell>본문</AppShell>);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await openMenu();
    const dialog = screen.getByRole("dialog", { name: "메뉴" });
    expect(dialog.querySelectorAll("a")).toHaveLength(5);
    for (const [label, href] of LINKS) {
      expect(screen.getByRole("link", { name: label })).toHaveAttribute(
        "href",
        href,
      );
    }
  });

  it('"유료 운세"는 보이지만 링크가 아니다', async () => {
    render(<AppShell>본문</AppShell>);
    await openMenu();
    expect(screen.getByText("유료 운세")).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "유료 운세" }),
    ).not.toBeInTheDocument();
  });

  it("Esc 로 닫힌다", async () => {
    render(<AppShell>본문</AppShell>);
    const user = await openMenu();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it('"메뉴 닫기"로 닫힌다', async () => {
    render(<AppShell>본문</AppShell>);
    const user = await openMenu();
    await user.click(screen.getByRole("button", { name: "메뉴 닫기" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("항목 링크를 누르면 닫힌다", async () => {
    render(<AppShell>본문</AppShell>);
    const user = await openMenu();
    await user.click(screen.getByRole("link", { name: "수능운" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("AppShell 헤더", () => {
  it("backHref 가 없으면 뒤로 링크가 없다", () => {
    render(<AppShell>본문</AppShell>);
    expect(
      screen.queryByRole("link", { name: "뒤로" }),
    ).not.toBeInTheDocument();
  });

  it("backHref 가 있으면 그 href 로 뒤로 링크가 있다", () => {
    render(<AppShell backHref="/suneung">본문</AppShell>);
    expect(screen.getByRole("link", { name: "뒤로" })).toHaveAttribute(
      "href",
      "/suneung",
    );
  });
});

describe("AppShell 하단 CTA", () => {
  it("cta 를 넘기면 렌더링된다", () => {
    render(
      <AppShell cta={<button type="button">결제하기</button>}>본문</AppShell>,
    );
    expect(screen.getByTestId("app-cta")).toContainElement(
      screen.getByRole("button", { name: "결제하기" }),
    );
  });

  it("cta 를 안 넘기면 CTA 영역이 없다", () => {
    render(<AppShell>본문</AppShell>);
    expect(screen.queryByTestId("app-cta")).not.toBeInTheDocument();
  });
});
