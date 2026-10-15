import { QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  render as renderPlain,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode, StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiNetworkError } from "@/lib/api/errors";
import { savePurchaseSelection } from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import { WALLET_QUERY_KEY } from "@/lib/wallet/query";
import { createFakeTopUpPort, type FakeTopUpScenario } from "@/mocks/topUp";
import { TopUpSuccess } from "./TopUpSuccess";

// 잔액 캐시 무효화(useQueryClient)를 위해 QueryClientProvider 로 감싼다 — 기존 단언은 그대로다
function render(ui: ReactNode, client = makeQueryClient()) {
  return renderPlain(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(() => {
  cleanup();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 가격 · 결제 키 · 규칙과 무관하다.
const KEY = "11111111-1111-4111-8111-111111111111";

async function prepare(scenario: FakeTopUpScenario) {
  const fake = createFakeTopUpPort({ scenario });
  const order = await fake.createOrder(
    { productCode: "FIXTURE_TOP_UP_B" },
    KEY,
  );
  const port = {
    confirm: vi.fn(fake.confirm),
    getOrder: vi.fn(fake.getOrder),
    getWallet: vi.fn(fake.getWallet),
  };
  const ret = {
    paymentKey: "fixture-payment-key",
    orderId: order.orderId,
    amount: String(order.amount.amount),
  };
  let t = 0;
  const deps = {
    now: () => t,
    sleep: async (ms: number) => {
      t += ms;
    },
  };
  return { fake, port, ret, deps };
}

describe("TopUpSuccess (/pay/success)", () => {
  it("CREDITED 면 완료와 서버 잔액을 보여 준다", async () => {
    const { fake, port, ret, deps } = await prepare("credited");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(await screen.findByText("충전 완료!")).toBeVisible();
    const { balance } = await fake.getWallet();
    expect(
      document.querySelector('[data-slot="wallet-balance"]')?.textContent,
    ).toBe(balance.toLocaleString("ko-KR"));
  });

  it("구매 선택이 저장돼 있으면 완료 뒤 앞 화면으로 이어 간다 (PURCHASE-RESTORE)", async () => {
    // 픽스처일 뿐이며 실제 상품 · 규칙과 무관하다
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "fixture-quote",
      selection: {
        fortuneType: "SUNEUNG",
        productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
        personId: "66666666-6666-4666-8666-666666666666",
        counterpartPersonId: null,
        productOption: null,
        questionKey: null,
        relationType: null,
      },
    });
    const { port, ret, deps } = await prepare("credited");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(
      await screen.findByRole("link", { name: "이어서 사주 보러 가기" }),
    ).toHaveAttribute("href", "/suneung");
    const links = within(screen.getByTestId("app-cta")).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual([
      "이어서 사주 보러 가기",
      "홈으로",
    ]);
    expect(links[1]).toHaveAttribute("href", "/");
  });

  it("구매 선택이 없으면 홈으로 하나만", async () => {
    const { port, ret, deps } = await prepare("credited");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(await screen.findByRole("link", { name: "홈으로" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(
      within(screen.getByTestId("app-cta")).getAllByRole("link"),
    ).toHaveLength(1);
    expect(
      screen.queryByRole("link", { name: "이어서 사주 보러 가기" }),
    ).toBeNull();
    expect(screen.queryByRole("link", { name: "확인" })).toBeNull();
  });

  it("StrictMode 이중 실행에도 승인은 한 번", async () => {
    const { port, ret, deps } = await prepare("credited");
    render(
      <StrictMode>
        <TopUpSuccess ret={ret} port={port} deps={deps} />
      </StrictMode>,
    );
    await screen.findByText("충전 완료!");
    expect(port.confirm).toHaveBeenCalledTimes(1);
  });

  it("처음에는 확인 중이고, 완료 전에는 완료 문구가 없다", async () => {
    const { port, ret, deps } = await prepare("paid_then_credited");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "결제를 확인하고 있어요",
    );
    expect(screen.queryByText("충전 완료!")).toBeNull();
    expect(await screen.findByText("충전 완료!")).toBeVisible();
  });

  it("30초 안에 확정되지 않으면 확인 중 안내 + 주문 확인 버튼, 다시 눌러도 승인은 다시 보내지 않는다", async () => {
    const user = userEvent.setup();
    const { port, ret, deps } = await prepare("stuck_paid");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    const button = await screen.findByRole("button", { name: "주문 확인하기" });
    expect(screen.queryByRole("alert")).toBeNull();
    const pollsBefore = port.getOrder.mock.calls.length;
    await user.click(button);
    await screen.findByRole("button", { name: "주문 확인하기" });
    expect(port.getOrder.mock.calls.length).toBeGreaterThan(pollsBefore);
    expect(port.confirm).toHaveBeenCalledTimes(1);
  });

  it("승인 응답이 끊겨도 실패로 표시하지 않는다", async () => {
    const { port, ret, deps } = await prepare("confirm_lost");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(await screen.findByText("충전 완료!")).toBeVisible();
  });

  it("결제 거절은 실패 안내", async () => {
    const { port, ret, deps } = await prepare("rejected");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았어요",
    );
  });

  it("r. 결제 거절(오류 code)은 재결제 허용을 모르므로 다시 결제하기 없이 홈으로(/)만 있다", async () => {
    const { port, ret, deps } = await prepare("rejected");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    await screen.findByRole("alert");
    const links = within(screen.getByTestId("app-cta")).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual(["홈으로"]);
    expect(links.map((l) => l.getAttribute("href"))).toEqual(["/"]);
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
  });

  it("p. 확인 중에는 상자와 주문 확인 버튼이 없고, 30초 뒤에는 상자 안에 늦어지는 안내가 있다", async () => {
    const { port, ret, deps } = await prepare("stuck_paid");
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
    );
    expect(container.querySelector('[data-slot="pay-result-box"]')).toBeNull();
    expect(screen.queryByRole("button", { name: "주문 확인하기" })).toBeNull();
    await screen.findByRole("button", { name: "주문 확인하기" });
    expect(
      container.querySelector('[data-slot="pay-result-box"]'),
    ).toHaveTextContent("확인이 조금 늦어지고 있어요");
    expect(screen.getByRole("status")).toHaveTextContent(
      "결제를 확인하고 있어요",
    );
  });

  it("q. CREDITED 인데 잔액이 null 이면 잔액 칸이 비어 있다", async () => {
    const { fake, ret, deps } = await prepare("credited");
    // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
    const port = {
      confirm: vi.fn(async () => ({
        orderId: ret.orderId,
        status: "CREDITED" as const,
        processing: false,
        walletBalance: null,
        grantedShellAmount: null,
        nextAction: "NONE" as const,
      })),
      getOrder: vi.fn(),
      getWallet: vi.fn(fake.getWallet),
    };
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
    );
    await screen.findByText("충전 완료!");
    const balance = container.querySelector('[data-slot="wallet-balance"]');
    expect(balance).not.toBeNull();
    expect(balance?.textContent).toBe("");
    // 지급 수량도 null 이면 비운다 — 0 으로 바꾸지 않는다
    const granted = container.querySelector('[data-slot="credited-amount"]');
    expect(granted).not.toBeNull();
    expect(granted?.textContent).toBe("");
  });

  it("r. CREDITED 의 지급 수량과 잔액은 서로 다른 서버 필드를 읽는다", async () => {
    const { fake, ret, deps } = await prepare("credited");
    // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
    const port = {
      confirm: vi.fn(async () => ({
        orderId: ret.orderId,
        status: "CREDITED" as const,
        processing: false,
        walletBalance: 5678,
        grantedShellAmount: 1234,
        nextAction: "VIEW_WALLET" as const,
      })),
      getOrder: vi.fn(),
      getWallet: vi.fn(fake.getWallet),
    };
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
    );
    await screen.findByText("충전 완료!");
    expect(
      container.querySelector('[data-slot="credited-amount"]')?.textContent,
    ).toBe("등껍질 1,234개가 들어왔어요");
    expect(
      container.querySelector('[data-slot="wallet-balance"]')?.textContent,
    ).toBe("5,678");
  });

  it("s. 헤더(banner)가 없고 캐릭터 자리가 aria-hidden 으로 하나 있다 (확인 중 · 완료)", async () => {
    const { port, ret, deps } = await prepare("paid_then_credited");
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
    );
    for (const phase of ["confirming", "credited"]) {
      if (phase === "credited") await screen.findByText("충전 완료!");
      expect(screen.queryByRole("banner")).toBeNull();
      const slots = container.querySelectorAll('[data-slot="character"]');
      expect(slots).toHaveLength(1);
      expect(slots[0]).toHaveAttribute("aria-hidden", "true");
    }
  });

  it("응답 모양이 계약과 다르면 삼키지 않고 던진다 (오류 화면)", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const { fake, ret, deps } = await prepare("credited");
    const contract = new ApiContractError(200, "fixture");
    const port = {
      confirm: vi.fn(async () => {
        throw contract;
      }),
      getOrder: vi.fn(),
      getWallet: vi.fn(fake.getWallet),
    };
    const probe = makeProbe();
    render(
      <probe.Boundary>
        <TopUpSuccess ret={ret} port={port} deps={deps} />
      </probe.Boundary>,
    );
    expect(await screen.findByText("caught")).toBeInTheDocument();
    expect(probe.caught()).toBe(contract);
    silence.mockRestore();
  });

  it("g. CREDITED 면 옛 잔액 캐시 대신 지갑을 다시 받아 유료 · 보너스를 보여 준다 (P-09 · Q-22)", async () => {
    const { fake, port, ret, deps } = await prepare("credited");
    const client = makeQueryClient();
    // 옛 잔액 캐시를 심어 둔다 (픽스처일 뿐이며 실제 잔액과 무관하다)
    client.setQueryData(WALLET_QUERY_KEY, {
      currency: "TURTLE_SHELL",
      balance: 100,
      paidBalance: 90,
      bonusBalance: 10,
    });
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
      client,
    );
    await screen.findByText("충전 완료!");
    const fresh = await fake.getWallet();
    const expected = `유료 ${fresh.paidBalance.toLocaleString("ko-KR")} · 보너스 ${fresh.bonusBalance.toLocaleString("ko-KR")}`;
    await waitFor(() =>
      expect(
        container.querySelector('[data-slot="wallet-breakdown"]')?.textContent,
      ).toBe(expected),
    );
    expect(port.getWallet).toHaveBeenCalled();
  });

  it("h. 아직 CREDITED 가 아닌 동안(확인 중 · 처리 중)에는 무효화하지 않는다", async () => {
    const { port, ret, deps } = await prepare("stuck_paid");
    const client = makeQueryClient();
    client.setQueryData(WALLET_QUERY_KEY, {
      currency: "TURTLE_SHELL",
      balance: 100,
      paidBalance: 90,
      bonusBalance: 10,
    });
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />, client);
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(false);
    await screen.findByRole("button", { name: "주문 확인하기" });
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(false);
    expect(port.getWallet).not.toHaveBeenCalled();
  });

  it("s. 내역은 지갑 응답 그대로, 보유는 주문 응답 walletBalance 그대로 — 서로 맞추지 않는다", async () => {
    const { ret, deps } = await prepare("credited");
    // 픽스처일 뿐이며 실제 잔액 · 규칙과 무관하다
    const port = {
      confirm: vi.fn(async () => ({
        orderId: ret.orderId,
        status: "CREDITED" as const,
        processing: false,
        walletBalance: 45,
        grantedShellAmount: 33,
        nextAction: "VIEW_WALLET" as const,
      })),
      getOrder: vi.fn(),
      getWallet: vi.fn(async () => ({
        currency: "TURTLE_SHELL",
        balance: 1045,
        paidBalance: 1040,
        bonusBalance: 5,
      })),
    };
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
    );
    await screen.findByText("충전 완료!");
    await waitFor(() =>
      expect(
        container.querySelector('[data-slot="wallet-breakdown"]')?.textContent,
      ).toBe("유료 1,040 · 보너스 5"),
    );
    expect(
      container.querySelector('[data-slot="wallet-balance"]')?.textContent,
    ).toBe("45");
  });

  it("t. 지갑 조회가 네트워크 오류여도 완료 안내는 보이고 내역 칸만 비어 있다", async () => {
    const { fake, ret, deps } = await prepare("credited");
    const port = {
      confirm: vi.fn(fake.confirm),
      getOrder: vi.fn(fake.getOrder),
      getWallet: vi.fn(async () => {
        throw new ApiNetworkError();
      }),
    };
    const client = makeQueryClient();
    client.setDefaultOptions({ queries: { retry: false } });
    const { container } = render(
      <TopUpSuccess ret={ret} port={port} deps={deps} />,
      client,
    );
    expect(await screen.findByText("충전 완료!")).toBeVisible();
    await waitFor(() =>
      expect(client.getQueryState(WALLET_QUERY_KEY)?.status).toBe("error"),
    );
    expect(screen.getByText("충전 완료!")).toBeVisible();
    expect(
      container.querySelector('[data-slot="wallet-breakdown"]')?.textContent,
    ).toBe("");
  });

  it("u. 지갑 응답이 계약과 다르면 삼키지 않고 오류 경계가 받는다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const { fake, ret, deps } = await prepare("credited");
    const contract = new ApiContractError(200, "fixture");
    const port = {
      confirm: vi.fn(fake.confirm),
      getOrder: vi.fn(fake.getOrder),
      getWallet: vi.fn(async () => {
        throw contract;
      }),
    };
    const client = makeQueryClient();
    client.setDefaultOptions({ queries: { retry: false } });
    const probe = makeProbe();
    render(
      <probe.Boundary>
        <TopUpSuccess ret={ret} port={port} deps={deps} />
      </probe.Boundary>,
      client,
    );
    expect(await screen.findByText("caught")).toBeInTheDocument();
    expect(probe.caught()).toBe(contract);
    silence.mockRestore();
  });

  it("v. 처리 중(stuck_paid)에는 주문 확인 버튼이 나온 뒤에도 지갑을 조회하지 않는다", async () => {
    const { port, ret, deps } = await prepare("stuck_paid");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    await screen.findByRole("button", { name: "주문 확인하기" });
    expect(port.getWallet).not.toHaveBeenCalled();
  });
});

describe("TopUpSuccess — 문구 · 실패 화면 (FUNCTIONAL_SPEC 7장 v0.4, Q-50)", () => {
  // 픽스처일 뿐이며 실제 주문 · 규칙과 무관하다
  const failedPort = (
    orderId: string,
    nextAction: "RETRY_PAYMENT" | "CONTACT_SUPPORT" | "NONE",
  ) => ({
    confirm: vi.fn(async () => ({
      orderId,
      status: "FAILED" as const,
      processing: false,
      walletBalance: null,
      grantedShellAmount: null,
      nextAction,
    })),
    getOrder: vi.fn(),
    getWallet: vi.fn(),
  });
  const RET = {
    paymentKey: "fixture-payment-key",
    amount: "1",
  };

  it("확인 중: 제목과 부제", async () => {
    const { port, ret, deps } = await prepare("paid_then_credited");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "결제를 확인하고 있어요",
    );
    expect(
      screen.getByText("잠시만 기다려 주세요. 이 화면을 닫지 말아 주세요."),
    ).toBeVisible();
    await screen.findByText("충전 완료!");
  });

  it("30초 지남: 제목 · 부제 · 상자 안내 · 주문 확인하기", async () => {
    const { port, ret, deps } = await prepare("stuck_paid");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    await screen.findByRole("button", { name: "주문 확인하기" });
    expect(screen.getByRole("status")).toHaveTextContent(
      "결제를 확인하고 있어요",
    );
    expect(
      screen.getByText("잠시만 기다려 주세요. 이 화면을 닫지 말아 주세요."),
    ).toBeVisible();
    expect(
      screen.getByText(
        "확인이 조금 늦어지고 있어요. 다시 결제하지 말고 아래 버튼으로 주문 상태를 확인해 주세요.",
      ),
    ).toBeVisible();
  });

  it("완료: 제목과 부제(등껍질 N개가 들어왔어요), 지급량이 null 이면 부제 없음", async () => {
    const { fake, ret, deps } = await prepare("credited");
    const withGranted = (grantedShellAmount: number | null) => ({
      confirm: vi.fn(async () => ({
        orderId: ret.orderId,
        status: "CREDITED" as const,
        processing: false,
        walletBalance: 10,
        grantedShellAmount,
        nextAction: "VIEW_WALLET" as const,
      })),
      getOrder: vi.fn(),
      getWallet: vi.fn(fake.getWallet),
    });
    const first = render(
      <TopUpSuccess ret={ret} port={withGranted(7777)} deps={deps} />,
    );
    expect(await screen.findByText("충전 완료!")).toBeVisible();
    expect(screen.getByText("등껍질 7,777개가 들어왔어요")).toBeVisible();
    first.unmount();
    cleanup();
    const second = render(
      <TopUpSuccess
        ret={{ ...ret, orderId: `${ret.orderId}-null` }}
        port={withGranted(null)}
        deps={deps}
      />,
    );
    await screen.findByText("충전 완료!");
    expect(
      second.container.querySelector('[data-slot="credited-amount"]')
        ?.textContent,
    ).toBe("");
  });

  it("실패 retry: 다시 결제하기(/wallet) · 홈으로, 문구", async () => {
    const orderId = "fixture-order-retry";
    render(
      <TopUpSuccess
        ret={{ ...RET, orderId }}
        port={failedPort(orderId, "RETRY_PAYMENT")}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았어요",
    );
    expect(
      screen.getByText("결제 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요."),
    ).toBeVisible();
    expect(
      screen.getByText("결제가 완료되지 않아 등껍질은 지급되지 않았어요."),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: "다시 결제하기" })).toHaveAttribute(
      "href",
      "/wallet",
    );
    expect(screen.getByRole("link", { name: "홈으로" })).toBeVisible();
    expect(document.body.textContent).not.toContain("청구");
  });

  it("실패 none: 다시 결제하기 없음 · 홈으로 있음", async () => {
    const orderId = "fixture-order-none";
    render(
      <TopUpSuccess
        ret={{ ...RET, orderId }}
        port={failedPort(orderId, "NONE")}
      />,
    );
    await screen.findByRole("alert");
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
    expect(screen.getByRole("link", { name: "홈으로" })).toBeVisible();
    expect(document.body.textContent).not.toContain("청구");
  });

  it("실패 contact: 문의 제목 · 문의하기 메일(충전 주문번호) · 다시 결제하기 없음", async () => {
    const orderId = "fixture-order-contact";
    render(
      <TopUpSuccess
        ret={{ ...RET, orderId }}
        port={failedPort(orderId, "CONTACT_SUPPORT")}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "확인이 필요한 결제예요. 고객센터로 문의해 주세요.",
    );
    const href =
      screen.getByRole("link", { name: "문의하기" }).getAttribute("href") ?? "";
    expect(decodeURIComponent(href.split("?subject=")[1])).toBe(
      `[뿌기사주 문의] 충전 주문번호 ${orderId}`,
    );
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
    expect(screen.getByRole("link", { name: "홈으로" })).toBeVisible();
    expect(document.body.textContent).not.toContain("청구");
  });
});

// 오류 경계 — 던진 오류를 붙잡아 확인한다 (error.tsx 자리)
function makeProbe() {
  let last: unknown = null;
  class Boundary extends Component<
    { children: ReactNode },
    { error: unknown }
  > {
    state = { error: null as unknown };
    static getDerivedStateFromError(error: unknown) {
      return { error };
    }
    componentDidCatch(error: unknown) {
      last = error;
    }
    render() {
      return this.state.error ? <p>caught</p> : this.props.children;
    }
  }
  return { Boundary, caught: () => last };
}
