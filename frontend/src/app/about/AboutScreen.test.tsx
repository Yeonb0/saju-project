import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { TopUpPort } from "@/lib/ports/topUp";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeTopUpPort, FIXTURE_TOP_UP_PRODUCTS } from "@/mocks/topUp";
import { AboutScreen } from "./AboutScreen";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/topUp.ts 의 픽스처다.
const NOTICE = "판매 중인 충전 상품이 없습니다";

function portWith(products: typeof FIXTURE_TOP_UP_PRODUCTS) {
  const port = createFakeTopUpPort();
  vi.spyOn(port, "listTopUpProducts").mockResolvedValue(products);
  return port;
}

function setup(port: TopUpPort = createFakeTopUpPort()) {
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <AboutScreen port={port} />
    </QueryClientProvider>,
  );
}

class Boundary extends Component<
  { children: ReactNode; onCatch: (error: unknown) => void },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch(error: unknown) {
    this.props.onCatch(error);
  }
  render() {
    return this.state.failed ? <p>caught</p> : this.props.children;
  }
}

describe("AboutScreen (PG-4 /about)", () => {
  it("a. 활성 상품 6개의 가격 · 유료 · 보너스 · 총을 서버(가짜) 값 그대로 보인다", async () => {
    setup();
    const items = await screen.findAllByRole("listitem");
    expect(items).toHaveLength(6);
    // 첫 상품: 1,111 KRW · 유료 11 · 보너스 0 · 총 11
    expect(items[0]).toHaveTextContent(
      "1,111 KRW · 유료 11 · 보너스 0 · 총 11",
    );
    // 셋째 상품: 3,333 KRW · 유료 33 · 보너스 3 · 총 36
    expect(items[2]).toHaveTextContent(
      "3,333 KRW · 유료 33 · 보너스 3 · 총 36",
    );
    // 여섯째 상품: 6,666 KRW · 유료 66 · 보너스 6 · 총 72
    expect(items[5]).toHaveTextContent(
      "6,666 KRW · 유료 66 · 보너스 6 · 총 72",
    );
    expect(screen.queryByText(NOTICE)).toBeNull();
  });

  it("b. 비활성 상품(7,777 KRW)은 보이지 않는다", async () => {
    setup();
    await screen.findAllByRole("listitem");
    expect(screen.queryByText(/7,777/)).toBeNull();
  });

  it("c. 전부 비활성이면 안내 문구만 보이고 상품 값은 없다", async () => {
    setup(
      portWith(FIXTURE_TOP_UP_PRODUCTS.map((p) => ({ ...p, active: false }))),
    );
    expect(await screen.findByText(NOTICE)).toBeInTheDocument();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
    expect(screen.queryByText(/KRW/)).toBeNull();
  });

  it("d. 빈 배열이어도 같은 안내", async () => {
    setup(portWith([]));
    expect(await screen.findByText(NOTICE)).toBeInTheDocument();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
  });

  it("e. 충전하러 가기는 /wallet, 환불정책은 /refund 로 간다", async () => {
    setup();
    await screen.findAllByRole("listitem");
    // 푸터에도 환불정책 링크가 있어 본문(main) 안에서만 찾는다
    const main = within(screen.getByRole("main"));
    expect(main.getByRole("link", { name: "충전하러 가기" })).toHaveAttribute(
      "href",
      "/wallet",
    );
    expect(main.getByRole("link", { name: "환불정책" })).toHaveAttribute(
      "href",
      "/refund",
    );
  });

  it("f. 서비스명 h1 은 뿌기사주", async () => {
    setup();
    await screen.findAllByRole("listitem");
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(
      screen.getByRole("heading", { level: 1, name: "뿌기사주" }),
    ).toBeInTheDocument();
  });

  it("g. 상품 조회가 500 이면 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    const port = createFakeTopUpPort();
    vi.spyOn(port, "listTopUpProducts").mockRejectedValue(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <AboutScreen port={port} />
        </Boundary>
      </QueryClientProvider>,
    );
    // 기본 retry 1 (makeQueryClient) 뒤에 던진다
    expect(
      await screen.findByText("caught", {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(500);
    silence.mockRestore();
  });

  it("h. 진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <AboutScreen />
        </Boundary>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("caught", {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(String(caught)).toContain("MOCK-PORT");
    silence.mockRestore();
  });

  it("i. h2 가 순서대로 서비스 소개 · 충전 상품 · 제공 방법 · 기간 · 유효기간 · 환불 · 고객센터다", async () => {
    setup();
    await screen.findAllByRole("listitem");
    expect(
      screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent),
    ).toEqual([
      "서비스 소개",
      "충전 상품",
      "제공 방법 · 기간",
      "유효기간",
      "환불",
      "고객센터",
    ]);
  });

  it("j. 원고 · 가격 자리는 하나씩 있고 모두 비어 있다", async () => {
    setup();
    await screen.findAllByRole("listitem");
    const main2 = screen.getByRole("main");
    for (const slot of [
      "intro",
      "content-prices",
      "period",
      "validity",
      "refund-summary",
      "support",
    ]) {
      const found = main2.querySelectorAll(`[data-slot="${slot}"]`);
      expect(found).toHaveLength(1);
      expect(found[0].textContent).toBe("");
    }
  });

  it("k. 활성 상품 li 는 상품 상자 안에 있고 콘텐츠 가격 상자에는 li 가 없다", async () => {
    setup();
    const items = await screen.findAllByRole("listitem");
    const main = screen.getByRole("main");
    const products = main.querySelector('[data-slot="top-up-products"]');
    expect(products).not.toBeNull();
    for (const item of items) expect(products).toContainElement(item);
    const prices = main.querySelector('[data-slot="content-prices"]');
    expect(prices?.querySelectorAll("li")).toHaveLength(0);
  });

  it("l. 캐릭터 자리는 aria-hidden 으로 하나 있다", async () => {
    setup();
    await screen.findAllByRole("listitem");
    const slots = screen
      .getByRole("main")
      .querySelectorAll('[data-slot="character"]');
    expect(slots).toHaveLength(1);
    expect(slots[0]).toHaveAttribute("aria-hidden", "true");
  });
});
