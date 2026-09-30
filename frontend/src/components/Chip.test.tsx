import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Chip } from "./Chip";

afterEach(cleanup);

describe("Chip", () => {
  it('data-frame="chip"', () => {
    render(
      <Chip selected={false} onClick={() => {}}>
        칩
      </Chip>,
    );
    expect(screen.getByRole("button")).toHaveAttribute("data-frame", "chip");
  });

  it("aria-pressed 가 selected 를 따른다", () => {
    const { rerender } = render(
      <Chip selected={false} onClick={() => {}}>
        칩
      </Chip>,
    );
    expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "false");
    rerender(
      <Chip selected onClick={() => {}}>
        칩
      </Chip>,
    );
    expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "true");
  });

  it("클릭하면 onClick 이 불린다", async () => {
    const onClick = vi.fn();
    render(
      <Chip selected={false} onClick={onClick}>
        칩
      </Chip>,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("disabled 면 onClick 이 안 불린다", async () => {
    const onClick = vi.fn();
    render(
      <Chip selected={false} onClick={onClick} disabled>
        칩
      </Chip>,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(onClick).not.toHaveBeenCalled();
  });
});
