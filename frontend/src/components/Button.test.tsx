import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Button } from "./Button";

afterEach(cleanup);

describe("Button", () => {
  it("type 기본값은 button", () => {
    render(<Button>확인</Button>);
    expect(screen.getByRole("button")).toHaveAttribute("type", "button");
  });

  it('type="submit" 을 넘기면 submit', () => {
    render(<Button type="submit">확인</Button>);
    expect(screen.getByRole("button")).toHaveAttribute("type", "submit");
  });

  it("disabled 가 전달된다", () => {
    render(<Button disabled>확인</Button>);
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it('variant="cta" 는 폭 327px · 높이 69px 클래스를 갖고, 기본 variant 는 지금 클래스 그대로 (LAYOUT-FIGMA)', () => {
    render(
      <>
        <Button variant="cta">진행</Button>
        <Button>확인</Button>
      </>,
    );
    const cta = screen.getByRole("button", { name: "진행" });
    expect(cta).toHaveClass("w-[327px]", "h-[69px]");
    // variant 는 DOM 속성으로 새지 않는다
    expect(cta).not.toHaveAttribute("variant");
    const plain = screen.getByRole("button", { name: "확인" });
    expect(plain).toHaveClass(
      "rounded-lg",
      "border",
      "border-neutral-900",
      "bg-white",
      "px-4",
      "py-3",
    );
    expect(plain).not.toHaveClass("w-[327px]");
  });

  it('data-frame="button"', () => {
    render(<Button>확인</Button>);
    expect(screen.getByRole("button")).toHaveAttribute("data-frame", "button");
  });
});
