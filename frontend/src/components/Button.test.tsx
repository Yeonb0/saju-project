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

  it('data-frame="button"', () => {
    render(<Button>확인</Button>);
    expect(screen.getByRole("button")).toHaveAttribute("data-frame", "button");
  });
});
