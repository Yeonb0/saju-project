import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { LoadingScene } from "./LoadingScene";

afterEach(cleanup);

describe("LoadingScene", () => {
  it("role=status 에 message 가 보인다", () => {
    render(<LoadingScene message="기다려 주세요" />);
    expect(screen.getByRole("status")).toHaveTextContent("기다려 주세요");
  });
});
