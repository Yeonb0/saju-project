import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BUSINESS_INFO } from "@/lib/business";
import { GenerationDone, GenerationScene } from "./GenerationScene";

afterEach(cleanup);

const REFUND_TEXT = "사용한 등껍질은 돌려드렸어요. 다시 시도해 주세요.";

describe("GenerationScene", () => {
  it("대기: 두 줄 문구(COMMON-02), 캐릭터 자리(aria-hidden)가 문구보다 먼저", () => {
    const { container } = render(<GenerationScene failure={null} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "구매를 처리하고 있어요",
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "잠시만 기다려 주세요. 창을 닫지 말아 주세요.",
    );
    const wait = container.querySelector('[data-slot="generation-wait"]');
    expect(wait).not.toBeNull();
    const character = wait?.querySelector('[data-slot="character"]');
    if (!character) throw new Error("캐릭터 자리가 없다");
    expect(character).toHaveAttribute("aria-hidden", "true");
    const first = screen.getByText("구매를 처리하고 있어요");
    const second = screen.getByText(
      "잠시만 기다려 주세요. 창을 닫지 말아 주세요.",
    );
    expect(
      character.compareDocumentPosition(first) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    // 둘째 줄은 첫 줄 아래 새 줄
    expect(second).toHaveClass("block");
    expect(
      first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("실패 retry + refunded: 대기 블록 없이 alert 제목과 환급 문장", () => {
    const { container } = render(
      <GenerationScene failure={{ refunded: true, next: "retry" }} />,
    );
    expect(container.querySelector('[data-slot="generation-wait"]')).toBeNull();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결과를 만들지 못했어요",
    );
    expect(screen.getByText(REFUND_TEXT)).toBeVisible();
  });

  it("실패 retry + 환급 아님: 환급 문장이 없다", () => {
    render(<GenerationScene failure={{ refunded: false, next: "retry" }} />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결과를 만들지 못했어요",
    );
    expect(screen.queryByText(REFUND_TEXT)).toBeNull();
    expect(
      screen.queryByText("고객센터로 문의해 주시면 빠르게 도와드릴게요."),
    ).toBeNull();
  });

  it.each([
    true,
    false,
  ])("contact_support (refunded %s): COMMON-04 문구와 email, 실패 제목 · 환급 문장은 없다", (refunded) => {
    render(<GenerationScene failure={{ refunded, next: "contact_support" }} />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "확인이 필요한 구매예요",
    );
    expect(
      screen.getByText("고객센터로 문의해 주시면 빠르게 도와드릴게요."),
    ).toBeVisible();
    expect(screen.getByText(BUSINESS_INFO.email ?? "")).toBeVisible();
    expect(screen.queryByText("결과를 만들지 못했어요")).toBeNull();
    expect(screen.queryByText(REFUND_TEXT)).toBeNull();
  });
});

describe("GenerationDone (구매 완료 — FUNCTIONAL_SPEC 8장 구매 상태 화면)", () => {
  it("f. 완료 블록에 data-slot, 문구, 결과 보러 가기 버튼이 있고 클릭하면 onView 가 불린다", async () => {
    const onView = vi.fn();
    const { container } = render(<GenerationDone onView={onView} />);
    expect(
      container.querySelector('[data-slot="generation-done"]'),
    ).not.toBeNull();
    expect(screen.getByText("준비가 끝났어요!")).toBeVisible();
    // 대기 · 실패 문구는 없다
    expect(screen.queryByText("구매를 처리하고 있어요")).toBeNull();
    await userEvent.click(
      screen.getByRole("button", { name: "결과 보러 가기" }),
    );
    expect(onView).toHaveBeenCalledTimes(1);
  });
});
