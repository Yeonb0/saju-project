import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { MockPanel } from "./MockPanel";
import { MOCK_OVERRIDES_KEY } from "./overrides";

// 모드를 테스트마다 바꿔 넣는다 (getter 라 import 한 쪽이 읽을 때마다 현재 값을 본다)
const mode = vi.hoisted(() => ({ value: "mock" as "mock" | "real" }));
vi.mock("@/lib/ports/mode", () => ({
  get API_MODE() {
    return mode.value;
  },
}));

const reload = vi.fn();

beforeEach(() => {
  mode.value = "mock";
  reload.mockReset();
  // jsdom 의 location.reload 는 바꿀 수 없어 location 통째로 대신한다
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { ...window.location, reload },
  });
});

afterEach(() => {
  cleanup();
  localStorage.clear();
  sessionStorage.clear();
});

const stored = () =>
  JSON.parse(localStorage.getItem(MOCK_OVERRIDES_KEY) ?? "null");

describe("MockPanel (MOCK-PANEL)", () => {
  it("g. 진짜 모드면 아무것도 그리지 않는다", () => {
    mode.value = "real";
    const { container } = render(<MockPanel />);
    expect(container).toBeEmptyDOMElement();
  });

  it("h. 가짜 모드에서 버튼 → 패널 → 라디오를 고르고 적용하면 저장값이 그 값이고 새로고침 1회", async () => {
    const user = userEvent.setup();
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "fixture-quote",
      selection: {
        productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
        personId: "66666666-6666-4666-8666-666666666666",
        counterpartPersonId: null,
      },
    });
    render(<MockPanel />);
    expect(screen.queryByRole("region")).toBeNull();
    await user.click(screen.getByRole("button", { name: "가짜" }));
    expect(screen.getByRole("region")).toBeInTheDocument();

    const session = screen.getByRole("group", { name: "세션" });
    await user.click(
      within(session).getByRole("radio", { name: /^signed_in —/ }),
    );
    const topUp = screen.getByRole("group", { name: "충전 결과" });
    await user.click(within(topUp).getByRole("radio", { name: /^rejected/ }));
    const fortune = screen.getByRole("group", { name: "운세 구매 결과" });
    await user.click(
      within(fortune).getByRole("radio", { name: /^quote_expired/ }),
    );
    const balance = screen.getByRole("group", { name: "시작 잔액" });
    await user.click(within(balance).getByRole("radio", { name: "100" }));

    await user.click(screen.getByRole("button", { name: "적용하고 처음부터" }));
    expect(stored()).toEqual({
      v: 1,
      session: "signed_in",
      topUp: "rejected",
      fortune: "quote_expired",
      balance: 100,
    });
    expect(loadPurchaseSelection()).toBeNull();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("i. 기본값으로 누르면 저장값이 없고 새로고침 1회", async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      MOCK_OVERRIDES_KEY,
      JSON.stringify({ v: 1, session: "signed_in" }),
    );
    render(<MockPanel />);
    await user.click(screen.getByRole("button", { name: "가짜" }));
    await user.click(screen.getByRole("button", { name: "기본값으로" }));
    expect(localStorage.getItem(MOCK_OVERRIDES_KEY)).toBeNull();
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
