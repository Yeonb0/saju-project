import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { ReadingPort } from "@/lib/ports/reading";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeReadings } from "@/mocks/reading";
import { ReadingScreen } from "./ReadingScreen";

afterEach(cleanup);

function renderScreen(readingId: string, port: ReadingPort) {
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <ReadingScreen readingId={readingId} title="결과" port={port} />
    </QueryClientProvider>,
  );
}

describe("ReadingScreen (재열람)", () => {
  it("결과를 받아 뷰어로 그린다", async () => {
    const readings = createFakeReadings();
    // 픽스처일 뿐이며 실제 상품 · 가격과 무관하다
    const id = readings.create({
      code: "FIXTURE",
      fortuneType: "SUNEUNG",
      option: "READING_WITH_TALISMAN",
      price: { currency: "TURTLE_SHELL", amount: 13 },
      active: true,
      saleEndsAt: null,
    });
    renderScreen(id, readings.port);
    expect(await screen.findByText("FIXTURE 요약")).toBeInTheDocument();
  });

  it("없는 결과 · 남의 결과는 상세 없이 not-found", async () => {
    renderScreen("missing", createFakeReadings().port);
    expect(
      await screen.findByText("페이지를 찾을 수 없습니다"),
    ).toBeInTheDocument();

    cleanup();
    renderScreen("other", {
      getReading: async () => {
        throw new ApiError({ status: 403, code: "FORBIDDEN", traceId: null });
      },
    });
    expect(
      await screen.findByText("페이지를 찾을 수 없습니다"),
    ).toBeInTheDocument();
  });
});
