import { QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { BUSINESS_INFO } from "@/lib/business";
import {
  DELIVERY_LINES,
  PAYMENT_LIMIT_LINE,
  REFUND_SUMMARY_LINES,
} from "@/lib/payment/directNotice";
import type { FortuneProduct } from "@/lib/ports/fortune";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeFortunePort } from "@/mocks/fortune";
import { AboutScreen } from "./AboutScreen";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다 (직접결제는 KRW 가격으로 만든다).
const NOTICE = "판매 중인 상품이 없습니다";
const TYPES = [
  "SUNEUNG",
  "OVERALL",
  "LOVE",
  "WEALTH",
  "COMPATIBILITY",
  "SINSAL",
] as const;

function krwPort() {
  return createFakeFortunePort({ priceCurrency: "KRW" });
}

// 6종 상품을 서버(가짜) 순서대로 모은다 — 기대 문장은 이 목록에서 만든다
async function allProducts(port: ReturnType<typeof krwPort>) {
  return (await Promise.all(TYPES.map((t) => port.listProducts(t)))).flat();
}

const NAMES: Record<string, string> = {
  SUNEUNG: "수능운",
  OVERALL: "종합운",
  LOVE: "애정운",
  WEALTH: "재물운",
  COMPATIBILITY: "궁합",
  SINSAL: "신살",
};
function optionOf(p: FortuneProduct) {
  if (p.fortuneType === "SUNEUNG") return "수능 부적 포함";
  return p.option === "READING_ONLY" ? "사주만" : "사주 + 행운 부적";
}
const lineOf = (p: FortuneProduct) =>
  `${NAMES[p.fortuneType]} · ${optionOf(p)} · ${p.price.amount.toLocaleString("ko-KR")}원`;

function portWith(
  transform: (products: readonly FortuneProduct[]) => FortuneProduct[],
) {
  const port = krwPort();
  const original = port.listProducts.bind(port);
  vi.spyOn(port, "listProducts").mockImplementation(async (type) =>
    transform(await original(type)),
  );
  return port;
}

// 상품 상자에 li 가 그려질 때까지 기다린다 — 안내 목록(제공 · 환불)의 li 는 조회 전에도 있어 listitem 으로는 기다릴 수 없다
async function productsShown() {
  await waitFor(() =>
    expect(
      document.querySelectorAll('[data-slot="products"] li').length,
    ).toBeGreaterThan(0),
  );
}

function setup(port = krwPort()) {
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <AboutScreen fortunePort={port} />
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
  it("A1. 6종 상품이 종류 순서대로 「이름 · 옵션 · 금액원」 — 금액은 같은 포트의 상품 값이다", async () => {
    const port = krwPort();
    setup(port);
    const expected = (await allProducts(port))
      .filter((p) => p.active)
      .sort(
        (a, b) =>
          TYPES.indexOf(a.fortuneType as never) -
          TYPES.indexOf(b.fortuneType as never),
      )
      .map(lineOf);
    expect(expected.length).toBeGreaterThan(6);
    const products = screen
      .getByRole("main")
      .querySelector('[data-slot="products"]');
    await waitFor(() =>
      expect(
        Array.from(products?.querySelectorAll("li") ?? []).map(
          (li) => li.textContent,
        ),
      ).toEqual(expected),
    );
    expect(screen.queryByText(NOTICE)).toBeNull();
    // 통화 코드 글자는 화면에 내지 않는다
    expect(screen.queryByText(/KRW/)).toBeNull();
  });

  it("A2. 비활성 상품은 보이지 않는다", async () => {
    const port = portWith((list) =>
      list.map((p, i) => (i === 0 ? { ...p, active: false } : p)),
    );
    setup(port);
    const first = (await krwPort().listProducts("OVERALL"))[0];
    const hidden = lineOf(first);
    const other = lineOf((await krwPort().listProducts("OVERALL"))[1]);
    await screen.findByText(other, { exact: false });
    expect(screen.queryByText(hidden)).toBeNull();
  });

  it("A3. 가격이 TURTLE_SHELL 인 활성 상품이 오면 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    const port = createFakeFortunePort({ priceCurrency: "TURTLE_SHELL" });
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <AboutScreen fortunePort={port} />
        </Boundary>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("caught", {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(String(caught)).toContain("직접결제 상품 가격이 KRW 가 아니다");
    silence.mockRestore();
  });

  it("A4. 활성 상품이 하나도 없으면 안내 문구만 보인다", async () => {
    setup(portWith((list) => list.map((p) => ({ ...p, active: false }))));
    expect(await screen.findByText(NOTICE)).toBeInTheDocument();
    const products = screen
      .getByRole("main")
      .querySelector('[data-slot="products"]');
    expect(products?.querySelectorAll("li")).toHaveLength(0);
  });

  it("A4. 빈 배열이어도 같은 안내", async () => {
    setup(portWith(() => []));
    expect(await screen.findByText(NOTICE)).toBeInTheDocument();
  });

  it("A6. 화면에 「등껍질」 · 「충전」 글자가 없고 /wallet 링크도 없다", async () => {
    setup();
    await productsShown();
    const main = screen.getByRole("main");
    expect(main.textContent).not.toContain("등껍질");
    expect(main.textContent).not.toContain("충전");
    expect(main.querySelector('a[href="/wallet"]')).toBeNull();
  });

  it("A7. 한도 · 제공 · 환불 문장이 그대로 보이고 환불정책 링크는 /refund", async () => {
    setup();
    await productsShown();
    const main = within(screen.getByRole("main"));
    expect(main.getByText(PAYMENT_LIMIT_LINE)).toBeInTheDocument();
    for (const line of [...DELIVERY_LINES, ...REFUND_SUMMARY_LINES]) {
      expect(main.getByText(line)).toBeInTheDocument();
    }
    expect(main.getByRole("link", { name: "환불정책" })).toHaveAttribute(
      "href",
      "/refund",
    );
  });

  it("A8. 고객센터에 사업자 이메일 · 전화가 그대로 보인다", async () => {
    setup();
    await productsShown();
    const support = screen
      .getByRole("main")
      .querySelector('[data-slot="support"]');
    expect(BUSINESS_INFO.email).not.toBeNull();
    expect(BUSINESS_INFO.phone).not.toBeNull();
    expect(support?.textContent).toContain(`이메일: ${BUSINESS_INFO.email}`);
    expect(support?.textContent).toContain(`전화: ${BUSINESS_INFO.phone}`);
  });

  it("f. 서비스명 h1 은 뿌기사주", async () => {
    setup();
    await productsShown();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(
      screen.getByRole("heading", { level: 1, name: "뿌기사주" }),
    ).toBeInTheDocument();
  });

  it("g. 상품 조회가 500 이면 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    const port = krwPort();
    vi.spyOn(port, "listProducts").mockRejectedValue(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <AboutScreen fortunePort={port} />
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
    expect(String(caught)).not.toContain("FIXTURE");
    silence.mockRestore();
  });

  it("A5. h2 가 순서대로 서비스 소개 · 상품과 가격 · 제공 방법 · 기간 · 환불 · 고객센터다", async () => {
    setup();
    await productsShown();
    expect(
      screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent),
    ).toEqual([
      "서비스 소개",
      "상품과 가격",
      "제공 방법 · 기간",
      "환불",
      "고객센터",
    ]);
  });

  it("j. 서비스 소개 자리는 하나 있고 비어 있다 (PD 원고 대기)", async () => {
    setup();
    await productsShown();
    const found = screen
      .getByRole("main")
      .querySelectorAll('[data-slot="intro"]');
    expect(found).toHaveLength(1);
    expect(found[0].textContent).toBe("");
    expect(
      screen.getByRole("main").querySelector('[data-slot="content-prices"]'),
    ).toBeNull();
  });

  it("l. 캐릭터 자리는 aria-hidden 으로 하나 있다", async () => {
    setup();
    await productsShown();
    const slots = screen
      .getByRole("main")
      .querySelectorAll('[data-slot="character"]');
    expect(slots).toHaveLength(1);
    expect(slots[0]).toHaveAttribute("aria-hidden", "true");
  });
});
