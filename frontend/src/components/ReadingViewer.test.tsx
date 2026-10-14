import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Reading, ReadingBody, SuneungGuide } from "@/lib/ports/reading";
import { checklistStorageKey } from "@/lib/reading/checklist";
import { ReadingViewer } from "./ReadingViewer";

afterEach(() => {
  cleanup();
  localStorage.clear();
});

// 픽스처일 뿐이며 실제 결과 문구 · 날짜 · 규칙과 무관하다
const READING_ID = "88888888-8888-4888-8888-888888888888";

const GUIDE: SuneungGuide = {
  summary: "FIXTURE 요약 글",
  examDayGuide: "FIXTURE 당일 글",
  examPeriods: [
    { period: "KOREAN", guidance: "FIXTURE 국어 안내" },
    { period: "FIXTURE_PERIOD", guidance: "FIXTURE 모르는 교시 안내" },
  ],
  focusTips: ["FIXTURE 팁 1", "FIXTURE 팁 2"],
  foodRecommendations: [
    { item: "FIXTURE 음식 1", reason: "FIXTURE 이유 1" },
    { item: "FIXTURE 음식 2", reason: "FIXTURE 이유 2" },
  ],
  // 서버 순서는 일부러 분류 순서와 다르게 둔다 (UNKNOWN · WRITING_TOOL · DOCUMENT)
  preparationChecklist: [
    { id: "fixture-c", label: "FIXTURE 준비물 c", category: "UNKNOWN" },
    { id: "fixture-b", label: "FIXTURE 준비물 b", category: "WRITING_TOOL" },
    { id: "fixture-a", label: "FIXTURE 준비물 a", category: "DOCUMENT" },
  ],
  anxietyManagement: ["FIXTURE 마음 관리 1", "FIXTURE 마음 관리 2"],
  missingElementAdvice: "FIXTURE 부족한 오행 글",
};

function reading(body: ReadingBody, disclaimers?: string[]): Reading {
  return {
    id: READING_ID,
    fortuneType: "SUNEUNG",
    productOption: "READING_WITH_TALISMAN",
    personDisplayName: "FIXTURE",
    event: { type: "SUNEUNG", date: "2000-01-01" },
    body,
    disclaimers: disclaimers ?? ["FOR_ENTERTAINMENT", "BIRTH_TIME_LIMITED"],
    createdAt: "2000-01-01T00:00:00Z",
  };
}

const guideReading = (guide: Partial<SuneungGuide> = {}) =>
  reading({ kind: "SUNEUNG_GUIDE", guide: { ...GUIDE, ...guide } });

const h2s = () =>
  screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent);

describe("ReadingViewer — 수능 가이드 (RESULT-GUIDE-MODEL)", () => {
  it("카드 제목이 FUNCTIONAL_SPEC 8장 순서와 같고, 카드마다 폭 376px", () => {
    const { container } = render(
      <ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />,
    );
    expect(h2s()).toEqual([
      "FIXTURE님의 수능 운세",
      "시험 날 하루 흐름",
      "교시별 응원",
      "집중 꿀팁",
      "수능 도시락",
      "준비물 체크리스트",
      "마음 다스리기",
      "부족한 기운 & 수능 부적",
    ]);
    const cards = container.querySelectorAll('[data-slot="result-card"]');
    expect(cards).toHaveLength(8);
    for (const card of cards) expect(card).toHaveClass("w-[376px]");
  });

  it("빈 배열 목록은 카드를 그리지 않는다", () => {
    render(
      <ReadingViewer
        reading={guideReading({
          examPeriods: [],
          focusTips: [],
          foodRecommendations: [],
          preparationChecklist: [],
          anxietyManagement: [],
        })}
        reportUnknown={vi.fn()}
      />,
    );
    expect(h2s()).toEqual([
      "FIXTURE님의 수능 운세",
      "시험 날 하루 흐름",
      "부족한 기운 & 수능 부적",
    ]);
  });

  it("요약 카드 제목은 personDisplayName 을 쓴다", () => {
    // 픽스처일 뿐이며 실제 이름과 무관하다
    render(
      <ReadingViewer
        reading={{ ...guideReading(), personDisplayName: "FIXTURE_OTHER" }}
        reportUnknown={vi.fn()}
      />,
    );
    expect(h2s()[0]).toBe("FIXTURE_OTHER님의 수능 운세");
  });

  it("아는 교시 코드는 이름과 안내, 모르는 코드는 안내만 보이고 코드 글자는 없다", () => {
    render(<ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />);
    expect(
      screen.getByRole("heading", { name: "1교시 국어" }),
    ).toBeInTheDocument();
    expect(screen.getByText("FIXTURE 국어 안내")).toBeInTheDocument();
    expect(screen.getByText("FIXTURE 모르는 교시 안내")).toBeInTheDocument();
    expect(screen.queryByText(/FIXTURE_PERIOD/)).toBeNull();
    expect(screen.queryByText("KOREAN")).toBeNull();
  });

  it("음식 · 집중 팁 · 마음 관리 항목이 서버 순서로 보인다", () => {
    const { container } = render(
      <ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />,
    );
    const text = container.textContent ?? "";
    for (const order of [
      ["FIXTURE 팁 1", "FIXTURE 팁 2"],
      ["FIXTURE 음식 1", "FIXTURE 이유 1", "FIXTURE 음식 2", "FIXTURE 이유 2"],
      ["FIXTURE 마음 관리 1", "FIXTURE 마음 관리 2"],
    ]) {
      const positions = order.map((s) => text.indexOf(s));
      expect(positions.every((p) => p >= 0)).toBe(true);
      expect(positions).toEqual([...positions].sort((a, b) => a - b));
    }
    expect(container.querySelector('[data-slot="food-image"]')).not.toBeNull();
  });

  it("준비물은 분류 순서로 묶이고 UNKNOWN 항목은 제목 없는 마지막 묶음이다", () => {
    render(<ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />);
    const h3s = screen
      .getAllByRole("heading", { level: 3 })
      .map((h) => h.textContent);
    // h3 는 교시 이름 · 음식 이름 · 묶음 이름이 섞여 있다 — 묶음 이름의 상대 순서만 본다
    expect(h3s.indexOf("꼭 챙기기")).toBeGreaterThanOrEqual(0);
    expect(h3s.indexOf("꼭 챙기기")).toBeLessThan(h3s.indexOf("시험장 소지품"));
    expect(h3s).not.toContain("그 밖에 챙길 것");
    const labels = screen
      .getAllByRole("checkbox")
      .map((c) => c.parentElement?.textContent);
    expect(labels).toEqual([
      "FIXTURE 준비물 a",
      "FIXTURE 준비물 b",
      "FIXTURE 준비물 c",
    ]);
  });

  it("시계 · 필기구는 한 묶음(시험장 소지품)에 서버 순서대로 들어가고, 항목 없는 묶음은 없다", () => {
    // 픽스처일 뿐이며 실제 준비물과 무관하다. 서버 순서는 일부러 TIMEPIECE 가 먼저
    render(
      <ReadingViewer
        reading={guideReading({
          preparationChecklist: [
            { id: "fixture-t", label: "FIXTURE 시계", category: "TIMEPIECE" },
            {
              id: "fixture-w",
              label: "FIXTURE 필기",
              category: "WRITING_TOOL",
            },
            { id: "fixture-d", label: "FIXTURE 서류", category: "DOCUMENT" },
          ],
        })}
        reportUnknown={vi.fn()}
      />,
    );
    const h3s = screen
      .getAllByRole("heading", { level: 3 })
      .map((h) => h.textContent);
    expect(h3s.filter((text) => text === "시험장 소지품")).toHaveLength(1);
    for (const absent of ["도시락 · 간식", "옷차림", "그 밖에 챙길 것"]) {
      expect(h3s).not.toContain(absent);
    }
    const kitGroup = screen.getByRole("heading", { name: "시험장 소지품" })
      .parentElement as HTMLElement;
    expect(
      within(kitGroup)
        .getAllByRole("checkbox")
        .map((c) => c.parentElement?.textContent),
    ).toEqual(["FIXTURE 시계", "FIXTURE 필기"]);
  });

  it("체크하면 PREPARATION 키에 ID 만 저장하고, 다시 그리면 복원한다", async () => {
    const user = userEvent.setup();
    const { unmount } = render(
      <ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />,
    );
    await user.click(screen.getByLabelText("FIXTURE 준비물 b"));
    expect(
      JSON.parse(
        localStorage.getItem(checklistStorageKey(READING_ID, "PREPARATION")) ??
          "",
      ),
    ).toEqual(["fixture-b"]);
    unmount();

    render(<ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />);
    expect(await screen.findByLabelText("FIXTURE 준비물 b")).toBeChecked();
    expect(screen.getByLabelText("FIXTURE 준비물 a")).not.toBeChecked();
  });

  it("저장값에 지금 없는 ID 가 있어도 무시하고, 다음 저장에도 남기지 않는다", async () => {
    const user = userEvent.setup();
    localStorage.setItem(
      checklistStorageKey(READING_ID, "PREPARATION"),
      JSON.stringify(["fixture-a", "fixture-gone"]),
    );
    render(<ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />);
    expect(await screen.findByLabelText("FIXTURE 준비물 a")).toBeChecked();
    expect(screen.getAllByRole("checkbox")).toHaveLength(3);
    await user.click(screen.getByLabelText("FIXTURE 준비물 c"));
    expect(
      JSON.parse(
        localStorage.getItem(checklistStorageKey(READING_ID, "PREPARATION")) ??
          "",
      ),
    ).toEqual(["fixture-c", "fixture-a"]);
    expect(
      localStorage.getItem(checklistStorageKey(READING_ID, "PREPARATION")),
    ).not.toContain("fixture-gone");
  });

  it("서버 문장의 태그는 해석하지 않고 글자 그대로 보인다", () => {
    const { container } = render(
      <ReadingViewer
        reading={guideReading({ summary: "<b>x</b>" })}
        reportUnknown={vi.fn()}
      />,
    );
    expect(screen.getByText("<b>x</b>")).toBeInTheDocument();
    expect(container.querySelector("b")).toBeNull();
  });
});

describe("ReadingViewer — 섹션 (예전 결과 · 일반 운세)", () => {
  const sections = reading({
    kind: "SECTIONS",
    sections: [
      { type: "TEXT", key: "SUMMARY", content: "FIXTURE 요약 본문" },
      { type: "UNKNOWN", rawKey: "FIXTURE_NEW_KEY" },
      { type: "TEXT", key: "CURRENT_FLOW", content: "FIXTURE 제목 없는 본문" },
    ],
  });

  it("제목이 있는 key 는 h2 제목, 없는 key 는 제목 없이 본문만", () => {
    const { container } = render(
      <ReadingViewer reading={sections} reportUnknown={vi.fn()} />,
    );
    expect(h2s()).toEqual(["요약"]);
    expect(screen.getByText("FIXTURE 요약 본문")).toBeInTheDocument();
    expect(screen.getByText("FIXTURE 제목 없는 본문")).toBeInTheDocument();
    const cards = container.querySelectorAll('[data-slot="result-card"]');
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveAttribute("aria-labelledby");
    expect(cards[1]).not.toHaveAttribute("aria-labelledby");
  });

  it("UNKNOWN 은 보이지 않고 reportUnknown 이 rawKey 로 한 번 불린다", () => {
    const reportUnknown = vi.fn();
    render(<ReadingViewer reading={sections} reportUnknown={reportUnknown} />);
    expect(screen.queryByText(/FIXTURE_NEW_KEY/)).toBeNull();
    expect(reportUnknown).toHaveBeenCalledTimes(1);
    expect(reportUnknown).toHaveBeenCalledWith("FIXTURE_NEW_KEY");
  });

  it("서버 문장의 태그는 글자 그대로 보인다", () => {
    const { container } = render(
      <ReadingViewer
        reading={reading({
          kind: "SECTIONS",
          sections: [{ type: "TEXT", key: "SUMMARY", content: "<b>x</b>" }],
        })}
        reportUnknown={vi.fn()}
      />,
    );
    expect(screen.getByText("<b>x</b>")).toBeInTheDocument();
    expect(container.querySelector("b")).toBeNull();
  });
});

describe("ReadingViewer — 공통", () => {
  it("서버 고지 코드를 결과 하단에 모두 남긴다 (문구는 PD)", () => {
    const { container } = render(
      <ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />,
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
      <ReadingViewer reading={guideReading()} reportUnknown={vi.fn()} />,
    );
    expect(container.querySelector('[data-slot="letter"]')).toBeNull();
    rerender(
      <ReadingViewer
        reading={guideReading()}
        reportUnknown={vi.fn()}
        letter={<p>편지</p>}
      />,
    );
    expect(screen.getByText("편지")).toBeInTheDocument();
  });
});
