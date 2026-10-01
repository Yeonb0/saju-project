import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Card } from "./Card";

afterEach(cleanup);

describe("Card", () => {
  it('data-frame="card" 이고 div props 가 통과한다', () => {
    render(<Card data-testid="card">내용</Card>);
    expect(screen.getByTestId("card")).toHaveAttribute("data-frame", "card");
    expect(screen.getByTestId("card")).toHaveTextContent("내용");
  });
});
