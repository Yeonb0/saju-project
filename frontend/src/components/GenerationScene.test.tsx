import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { GenerationScene } from "./GenerationScene";

afterEach(cleanup);

describe("GenerationScene", () => {
  it("대기: 상태 문구, 캐릭터 자리(aria-hidden)가 문구보다 먼저", () => {
    const { container } = render(<GenerationScene failure={null} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "결과를 만들고 있습니다",
    );
    const wait = container.querySelector('[data-slot="generation-wait"]');
    expect(wait).not.toBeNull();
    const character = wait?.querySelector('[data-slot="character"]');
    if (!character) throw new Error("캐릭터 자리가 없다");
    expect(character).toHaveAttribute("aria-hidden", "true");
    const text = screen.getByText("결과를 만들고 있습니다");
    expect(
      character.compareDocumentPosition(text) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("실패(refunded): 대기 블록 없이 alert 와 환급 문장", () => {
    const { container } = render(
      <GenerationScene failure={{ refunded: true, next: "retry" }} />,
    );
    expect(container.querySelector('[data-slot="generation-wait"]')).toBeNull();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결과를 만들지 못했습니다",
    );
    expect(screen.getByText("사용한 등껍질은 돌려드렸습니다")).toBeVisible();
  });

  it("실패(refunded 아님): 환급 문장이 없다", () => {
    render(<GenerationScene failure={{ refunded: false, next: "retry" }} />);
    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.queryByText("사용한 등껍질은 돌려드렸습니다")).toBeNull();
  });

  it("next 가 contact_support 면 문의 문장, retry 면 없다", () => {
    const { rerender } = render(
      <GenerationScene
        failure={{ refunded: false, next: "contact_support" }}
      />,
    );
    expect(screen.getByText("고객센터로 문의해 주세요")).toBeVisible();
    rerender(<GenerationScene failure={{ refunded: false, next: "retry" }} />);
    expect(screen.queryByText("고객센터로 문의해 주세요")).toBeNull();
  });

  it("문의 안내와 환급 문장은 서로 독립 — 환급됐어도 문의일 수 있다", () => {
    render(
      <GenerationScene failure={{ refunded: true, next: "contact_support" }} />,
    );
    expect(screen.getByText("사용한 등껍질은 돌려드렸습니다")).toBeVisible();
    expect(screen.getByText("고객센터로 문의해 주세요")).toBeVisible();
  });
});
