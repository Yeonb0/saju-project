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
      <GenerationScene failure={{ refunded: true }} />,
    );
    expect(container.querySelector('[data-slot="generation-wait"]')).toBeNull();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결과를 만들지 못했습니다",
    );
    expect(screen.getByText("사용한 등껍질은 돌려드렸습니다")).toBeVisible();
  });

  it("실패(refunded 아님): 환급 문장이 없다", () => {
    render(<GenerationScene failure={{ refunded: false }} />);
    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.queryByText("사용한 등껍질은 돌려드렸습니다")).toBeNull();
  });
});
