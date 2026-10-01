import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { BottomSheet } from "./BottomSheet";

afterEach(cleanup);

describe("BottomSheet", () => {
  it("open 이면 title 이 보인다", () => {
    render(
      <BottomSheet open onOpenChange={() => {}} title="시트 제목">
        내용
      </BottomSheet>,
    );
    expect(screen.getByText("시트 제목")).toBeVisible();
  });

  it("open=false 면 없다", () => {
    render(
      <BottomSheet open={false} onOpenChange={() => {}} title="시트 제목">
        내용
      </BottomSheet>,
    );
    expect(screen.queryByText("시트 제목")).not.toBeInTheDocument();
  });
});
