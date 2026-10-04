import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PaymentLauncher } from "@/lib/ports/paymentLauncher";
import type { TopUpPort } from "@/lib/ports/topUp";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeTopUpPort } from "@/mocks/topUp";
import { WalletScreen } from "./WalletScreen";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/topUp.ts 의 픽스처다.
function setup(port: TopUpPort = createFakeTopUpPort()) {
  const createOrder = vi.spyOn(port, "createOrder");
  const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <WalletScreen port={port} launcher={launcher} />
    </QueryClientProvider>,
  );
  return { createOrder, launcher };
}

describe("WalletScreen (PAY-01)", () => {
  it("서버(가짜) 상품 값을 그대로 보여 주고, 비활성 상품은 고를 수 없다", async () => {
    setup();
    const radios = await screen.findAllByRole("radio");
    expect(radios).toHaveLength(3);
    expect(radios[2]).toBeDisabled();
    expect(screen.getByText(/1,111 KRW/)).toBeInTheDocument();
  });

  it("상품을 고르기 전에는 결제할 수 없다", async () => {
    setup();
    await screen.findAllByRole("radio");
    expect(screen.getByRole("button", { name: "결제하기" })).toBeDisabled();
  });

  it("서버 주문 그대로 결제창을 연다", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click((await screen.findAllByRole("radio"))[1]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    const order = await createOrder.mock.results[0].value;
    expect(launcher.requestPayment).toHaveBeenCalledWith(order);
  });

  it("실패 후 다시 눌러도 같은 상품이면 같은 Idempotency-Key", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    const createOrder = vi
      .spyOn(port, "createOrder")
      .mockRejectedValueOnce(new TypeError("fixture"));
    const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen port={port} launcher={launcher} />
      </QueryClientProvider>,
    );
    await user.click((await screen.findAllByRole("radio"))[0]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder.mock.calls[1][1]).toBe(createOrder.mock.calls[0][1]);
  });

  it("다른 상품으로 바꾸면 새 구매 의도라 새 키", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    const createOrder = vi
      .spyOn(port, "createOrder")
      .mockRejectedValueOnce(new TypeError("fixture"));
    const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen port={port} launcher={launcher} />
      </QueryClientProvider>,
    );
    const radios = await screen.findAllByRole("radio");
    await user.click(radios[0]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await screen.findByRole("alert");
    await user.click(radios[1]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder.mock.calls[1][1]).not.toBe(createOrder.mock.calls[0][1]);
  });

  it("같은 틱에 두 번 눌러도 주문 · 결제창은 하나", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click((await screen.findAllByRole("radio"))[0]);
    const button = screen.getByRole("button", { name: "결제하기" });
    // 렌더 사이 없이 두 번 — 실제 더블 탭 경쟁
    button.click();
    button.click();
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(createOrder).toHaveBeenCalledTimes(1);
  });

  it("진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    class Boundary extends Component<
      { children: ReactNode },
      { failed: boolean }
    > {
      state = { failed: false };
      static getDerivedStateFromError() {
        return { failed: true };
      }
      componentDidCatch(error: unknown) {
        caught = error;
      }
      render() {
        return this.state.failed ? <p>caught</p> : this.props.children;
      }
    }
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary>
          <WalletScreen />
        </Boundary>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("caught", {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(String(caught)).toContain("MOCK-PORT");
    silence.mockRestore();
  });
});
