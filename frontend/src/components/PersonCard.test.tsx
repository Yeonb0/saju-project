import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PersonSummary } from "@/lib/ports/person";
import { PersonCard } from "./PersonCard";

afterEach(cleanup);

// vaul 은 시트 안을 누를 때 setPointerCapture 를 부르는데 jsdom 에는 없다 — 이 파일에서만 빈 함수로 채운다
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
  Element.prototype.releasePointerCapture = () => {};
}

// 픽스처일 뿐이며 실제 사용자와 무관하다
const SELF: PersonSummary = {
  personId: "55555555-5555-4555-8555-555555555555",
  isSelf: true,
  name: "본인 픽스처",
};
const OTHER: PersonSummary = {
  personId: "99999999-9999-4999-8999-999999999999",
  isSelf: false,
  name: "타인 픽스처",
};

describe("PersonCard", () => {
  it("이름과 본인 표시, 수정 링크를 보인다", () => {
    render(<PersonCard person={SELF} people={[SELF]} onSelect={vi.fn()} />);
    expect(screen.getByText("본인 픽스처")).toBeInTheDocument();
    expect(screen.getByText("(본인)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "수정" })).toHaveAttribute(
      "href",
      `/me/people/${SELF.personId}`,
    );
  });

  it("한 명뿐이면 불러오기가 없다", () => {
    render(<PersonCard person={SELF} people={[SELF]} onSelect={vi.fn()} />);
    expect(
      screen.queryByRole("button", { name: "저장된 다른 사용자 불러오기" }),
    ).toBeNull();
  });

  it("불러오기에서 고르면 그 인물을 넘기고 시트를 닫는다", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(
      <PersonCard person={SELF} people={[SELF, OTHER]} onSelect={onSelect} />,
    );
    await user.click(
      screen.getByRole("button", { name: "저장된 다른 사용자 불러오기" }),
    );
    expect(screen.getByRole("button", { name: /본인 픽스처/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await user.click(screen.getByRole("button", { name: "타인 픽스처" }));
    expect(onSelect).toHaveBeenCalledWith(OTHER);
    expect(screen.queryByText("저장된 사용자")).toBeNull();
  });
});
