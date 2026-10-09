import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Reading } from "@/lib/ports/reading";
import { checklistStorageKey } from "@/lib/reading/checklist";
import { ReadingViewer } from "./ReadingViewer";

afterEach(() => {
  cleanup();
  localStorage.clear();
});

// 픽스처일 뿐이며 실제 결과 문구 · 날짜 · 규칙과 무관하다
const READING: Reading = {
  id: "88888888-8888-4888-8888-888888888888",
  fortuneType: "SUNEUNG",
  productOption: "READING_WITH_TALISMAN",
  personDisplayName: "FIXTURE",
  birthTimeUnknown: false,
  event: { type: "SUNEUNG", date: "2000-01-01" },
  sections: [
    { key: "B", type: "TEXT", title: "둘째 제목", content: "본문 B" },
    { key: "NEW", type: "UNKNOWN", rawType: "FIXTURE_NEW_TYPE" },
    {
      key: "P",
      type: "PERIOD_GUIDANCE",
      title: "시간대 제목",
      items: [{ label: "가", guidance: "안내 가", focusPoint: null }],
    },
    {
      key: "M",
      type: "FOOD_RECOMMENDATION",
      title: "음식 제목",
      primary: { name: "음식 1", reason: "이유 1" },
      alternatives: [{ name: "음식 2", reason: "이유 2" }],
    },
    {
      key: "C",
      type: "CHECKLIST",
      title: "준비물 제목",
      items: [
        { id: "a", label: "준비물 a", personalized: false },
        { id: "b", label: "준비물 b", personalized: true },
      ],
    },
  ],
  disclaimers: ["FOR_ENTERTAINMENT", "BIRTH_TIME_LIMITED"],
  createdAt: "2000-01-01T00:00:00Z",
};

describe("ReadingViewer (VIEWER)", () => {
  it("섹션마다 결과 카드 모양(폭 376px)을 갖고, 모르는 타입은 카드가 없다 (LAYOUT-FIGMA)", () => {
    const { container } = render(
      <ReadingViewer reading={READING} reportUnknown={vi.fn()} />,
    );
    const cards = container.querySelectorAll('[data-slot="result-card"]');
    // 그려지는 섹션 4개 (TEXT · PERIOD_GUIDANCE · FOOD_RECOMMENDATION · CHECKLIST)
    expect(cards).toHaveLength(4);
    for (const card of cards) expect(card).toHaveClass("w-[376px]");
  });

  it("섹션을 서버 순서대로 타입별로 그린다", () => {
    render(<ReadingViewer reading={READING} reportUnknown={vi.fn()} />);
    const titles = screen
      .getAllByRole("heading", { level: 2 })
      .map((h) => h.textContent);
    expect(titles).toEqual([
      "둘째 제목",
      "시간대 제목",
      "음식 제목",
      "준비물 제목",
    ]);
    expect(screen.getByText("본문 B")).toBeInTheDocument();
    expect(screen.getByText("안내 가")).toBeInTheDocument();
    expect(screen.getByText("음식 2")).toBeInTheDocument();
    expect(screen.getAllByRole("checkbox")).toHaveLength(2);
  });

  it("모르는 섹션 타입은 그 섹션만 건너뛰고 타입 이름만 경고한다", () => {
    const reportUnknown = vi.fn();
    render(<ReadingViewer reading={READING} reportUnknown={reportUnknown} />);
    expect(screen.queryByText("FIXTURE_NEW_TYPE")).toBeNull();
    expect(reportUnknown).toHaveBeenCalledWith("FIXTURE_NEW_TYPE");
  });

  it("서버 고지 코드를 결과 하단에 모두 남긴다 (문구는 PD)", () => {
    const { container } = render(
      <ReadingViewer reading={READING} reportUnknown={vi.fn()} />,
    );
    const list = container.querySelector('[data-slot="disclaimers"]');
    expect(list).not.toBeNull();
    const items = within(list as HTMLElement).getAllByRole("listitem");
    expect(items.map((li) => li.textContent)).toEqual([
      "FOR_ENTERTAINMENT",
      "BIRTH_TIME_LIMITED",
    ]);
  });

  it("편지 자리는 넘긴 경우에만", () => {
    const { rerender, container } = render(
      <ReadingViewer reading={READING} reportUnknown={vi.fn()} />,
    );
    expect(container.querySelector('[data-slot="letter"]')).toBeNull();
    rerender(
      <ReadingViewer
        reading={READING}
        reportUnknown={vi.fn()}
        letter={<p>편지</p>}
      />,
    );
    expect(screen.getByText("편지")).toBeInTheDocument();
  });

  it("준비물 체크는 결과 ID 별로 기기에 남고, 다시 열면 그대로다", async () => {
    const user = userEvent.setup();
    const { unmount } = render(
      <ReadingViewer reading={READING} reportUnknown={vi.fn()} />,
    );
    await user.click(screen.getByLabelText("준비물 b"));
    expect(
      JSON.parse(
        localStorage.getItem(checklistStorageKey(READING.id, "C")) ?? "",
      ),
    ).toEqual(["b"]);
    unmount();

    render(<ReadingViewer reading={READING} reportUnknown={vi.fn()} />);
    expect(await screen.findByLabelText("준비물 b")).toBeChecked();
    expect(screen.getByLabelText("준비물 a")).not.toBeChecked();
  });
});
