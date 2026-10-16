import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import Page from "./page";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

function props(query: Record<string, string | string[]>) {
  return {
    params: Promise.resolve({}),
    searchParams: Promise.resolve(query),
  } as PageProps<"/pay/fail">;
}

const FAILURE_SUB = "결제 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";

describe("/pay/fail (PAY-05)", () => {
  it("code=PAY_PROCESS_CANCELED 면 취소 문구, PG 원문은 화면에 없다", async () => {
    // 픽스처일 뿐이며 실제 PG 코드 · 메시지와 무관하다
    render(
      await Page(
        props({ code: "PAY_PROCESS_CANCELED", message: "FIXTURE 원문" }),
      ),
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았어요",
    );
    expect(screen.getByText("결제를 취소했어요.")).toBeVisible();
    expect(screen.queryByText(FAILURE_SUB)).toBeNull();
    expect(
      screen.getByText("결제가 완료되지 않아 등껍질은 지급되지 않았어요."),
    ).toBeVisible();
    expect(document.body.textContent).not.toContain("FIXTURE 원문");
    expect(document.body.textContent).not.toContain("PAY_PROCESS_CANCELED");
  });

  it.each([
    ["다른 code", { code: "FIXTURE_OTHER_CODE", message: "FIXTURE 원문" }],
    ["code 없음", { message: "FIXTURE 원문" }],
    ["code 가 여러 번", { code: ["PAY_PROCESS_CANCELED", "X"] }],
  ])("%s 면 실패 문구, code · message 글자는 화면에 없다", async (_name, query) => {
    render(await Page(props(query)));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았어요",
    );
    expect(screen.getByText(FAILURE_SUB)).toBeVisible();
    expect(screen.queryByText("결제를 취소했어요.")).toBeNull();
    expect(document.body.textContent).not.toContain("FIXTURE");
  });

  it("product=FIXTURE_TOP_UP_X 면 다시 결제하기가 상품을 유지하고, 그 코드는 화면 글자에 없다 (TOPUP-RETRY-URL)", async () => {
    // 픽스처일 뿐이며 실제 상품 코드와 무관하다
    render(await Page(props({ product: "FIXTURE_TOP_UP_X" })));
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/wallet?product=FIXTURE_TOP_UP_X",
    );
    expect(document.body.textContent).not.toContain("FIXTURE_TOP_UP_X");
  });

  it.each([
    ["이상한 값", { product: "x<y" }],
    ["없음", {}],
    ["여러 번", { product: ["FIXTURE_TOP_UP_X", "FIXTURE_TOP_UP_Y"] }],
  ])("product %s 면 다시 결제하기는 /wallet", async (_name, query) => {
    render(await Page(props(query)));
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/wallet",
    );
  });

  it("다시 결제하기(/wallet) · 홈으로(/) 링크, 헤더 없음", async () => {
    render(await Page(props({})));
    const links = within(screen.getByTestId("app-cta")).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual([
      "다시 결제하기",
      "홈으로",
    ]);
    expect(links.map((l) => l.getAttribute("href"))).toEqual(["/wallet", "/"]);
    expect(screen.queryByRole("banner")).toBeNull();
    expect(document.body.textContent).not.toContain("청구");
  });
});

describe("/pay/fail?flow=direct (DIRECT-PAY)", () => {
  const BOX = "결제가 완료되지 않아 결제 금액은 청구되지 않았어요.";

  it("F1. return 경로로 다시 결제하기, box 는 직접결제 문장이고 등껍질 문장은 없다", async () => {
    render(
      await Page(
        props({
          flow: "direct",
          return: "/fortune/love/questions",
          code: "PAY_PROCESS_CANCELED",
        }),
      ),
    );
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/fortune/love/questions",
    );
    expect(screen.getByText(BOX)).toBeVisible();
    expect(screen.getByText("결제를 취소했어요.")).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았어요",
    );
    expect(document.body.textContent).not.toContain("등껍질");
  });

  it("F1. 인코딩된 쿼리가 든 return 도 그대로 이어 준다 (product 는 보지 않는다)", async () => {
    render(
      await Page(
        props({
          flow: "direct",
          return: "/fortune/love/questions?personId=abc",
          product: "FIXTURE_TOP_UP_X",
        }),
      ),
    );
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/fortune/love/questions?personId=abc",
    );
  });

  it("F2. return 이 없으면 다시 결제하기 없이 홈으로만", async () => {
    render(await Page(props({ flow: "direct" })));
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
    const links = within(screen.getByTestId("app-cta")).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual(["홈으로"]);
    expect(screen.getByText(BOX)).toBeVisible();
  });

  it("F2. return 이 여러 번 오면 다시 결제하기 없이 홈으로만", async () => {
    render(await Page(props({ flow: "direct", return: ["/a", "/b"] })));
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
  });

  it("F3. 바깥 주소 return 은 safeReturnTo 결과(/)가 된다", async () => {
    render(
      await Page(props({ flow: "direct", return: "https://evil.example" })),
    );
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/",
    );
  });

  it("F4. flow 가 다른 값이면 지금 동작 그대로(충전 문장 · /wallet)", async () => {
    render(await Page(props({ flow: "other", return: "/x" })));
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/wallet",
    );
    expect(
      screen.getByText("결제가 완료되지 않아 등껍질은 지급되지 않았어요."),
    ).toBeVisible();
  });
});
