import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode, StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiContractError } from "@/lib/api/errors";
import { createFakeTopUpPort, type FakeTopUpScenario } from "@/mocks/topUp";
import { TopUpSuccess } from "./TopUpSuccess";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 가격 · 결제 키 · 규칙과 무관하다.
const KEY = "11111111-1111-4111-8111-111111111111";

async function prepare(scenario: FakeTopUpScenario) {
  const fake = createFakeTopUpPort({ scenario });
  const order = await fake.createOrder(
    { productCode: "FIXTURE_TOP_UP_B" },
    KEY,
  );
  const port = { confirm: vi.fn(fake.confirm), getOrder: vi.fn(fake.getOrder) };
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
    expect(await screen.findByText("충전이 완료되었습니다")).toBeVisible();
    const { balance } = await fake.getWallet();
    expect(
      screen.getByText(`보유 ${balance.toLocaleString("ko-KR")}`),
    ).toBeVisible();
  });

  it("StrictMode 이중 실행에도 승인은 한 번", async () => {
    const { port, ret, deps } = await prepare("credited");
    render(
      <StrictMode>
        <TopUpSuccess ret={ret} port={port} deps={deps} />
      </StrictMode>,
    );
    await screen.findByText("충전이 완료되었습니다");
    expect(port.confirm).toHaveBeenCalledTimes(1);
  });

  it("처음에는 확인 중이고, 완료 전에는 완료 문구가 없다", async () => {
    const { port, ret, deps } = await prepare("paid_then_credited");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "결제를 확인하고 있습니다",
    );
    expect(screen.queryByText("충전이 완료되었습니다")).toBeNull();
    expect(await screen.findByText("충전이 완료되었습니다")).toBeVisible();
  });

  it("30초 안에 확정되지 않으면 확인 중 안내 + 주문 확인 버튼, 다시 눌러도 승인은 다시 보내지 않는다", async () => {
    const user = userEvent.setup();
    const { port, ret, deps } = await prepare("stuck_paid");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    const button = await screen.findByRole("button", { name: "주문 확인" });
    expect(screen.queryByRole("alert")).toBeNull();
    const pollsBefore = port.getOrder.mock.calls.length;
    await user.click(button);
    await screen.findByRole("button", { name: "주문 확인" });
    expect(port.getOrder.mock.calls.length).toBeGreaterThan(pollsBefore);
    expect(port.confirm).toHaveBeenCalledTimes(1);
  });

  it("승인 응답이 끊겨도 실패로 표시하지 않는다", async () => {
    const { port, ret, deps } = await prepare("confirm_lost");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(await screen.findByText("충전이 완료되었습니다")).toBeVisible();
  });

  it("결제 거절은 실패 안내", async () => {
    const { port, ret, deps } = await prepare("rejected");
    render(<TopUpSuccess ret={ret} port={port} deps={deps} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "충전이 완료되지 않았습니다",
    );
  });

  it("응답 모양이 계약과 다르면 삼키지 않고 던진다 (오류 화면)", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const { ret, deps } = await prepare("credited");
    const contract = new ApiContractError(200, "fixture");
    const port = {
      confirm: vi.fn(async () => {
        throw contract;
      }),
      getOrder: vi.fn(),
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
