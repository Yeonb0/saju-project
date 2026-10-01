import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Modal } from "./Modal";

afterEach(cleanup);

describe("Modal", () => {
  it("open 이면 title 이름의 dialog 가 보인다", () => {
    render(
      <Modal open onOpenChange={() => {}} title="제목">
        내용
      </Modal>,
    );
    expect(screen.getByRole("dialog", { name: "제목" })).toBeVisible();
  });

  it("Esc 에 onOpenChange(false)", async () => {
    const onOpenChange = vi.fn();
    render(
      <Modal open onOpenChange={onOpenChange} title="제목">
        내용
      </Modal>,
    );
    await userEvent.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("open=false 면 없다", () => {
    render(
      <Modal open={false} onOpenChange={() => {}} title="제목">
        내용
      </Modal>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
