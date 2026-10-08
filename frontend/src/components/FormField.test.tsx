import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { FormField } from "./FormField";

afterEach(cleanup);

const setup = (props: { hint?: string; error?: string } = {}) =>
  render(
    <FormField id="name" label="이름" {...props}>
      {(field) => <input {...field} />}
    </FormField>,
  );

describe("FormField", () => {
  it("className 을 넘기면 바깥 요소에 붙는다 (LAYOUT-FIGMA)", () => {
    render(
      <FormField id="name" label="이름" className="mt-[20px]">
        {(field) => <input {...field} />}
      </FormField>,
    );
    const outer = screen.getByLabelText("이름").parentElement?.parentElement;
    expect(outer).toHaveClass("mt-[20px]");
    expect(outer?.contains(screen.getByText("이름"))).toBe(true);
  });

  it("getByLabelText 로 입력이 잡힌다", () => {
    setup();
    expect(screen.getByLabelText("이름")).toBeInTheDocument();
  });

  it('입력 감싸개에 data-frame="input"', () => {
    setup();
    expect(screen.getByLabelText("이름").parentElement).toHaveAttribute(
      "data-frame",
      "input",
    );
  });

  it("error 가 있으면 aria-invalid=true, describedby 에 error id, role=alert", () => {
    setup({ error: "필수예요" });
    const input = screen.getByLabelText("이름");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "name-error");
    expect(screen.getByRole("alert")).toHaveTextContent("필수예요");
  });

  it("error 가 없으면 aria-invalid 가 true 가 아니고 alert 가 없다", () => {
    setup();
    expect(screen.getByLabelText("이름")).not.toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("hint 와 error 가 둘 다 있으면 describedby 에 둘 다 있다", () => {
    setup({ hint: "도움말", error: "필수예요" });
    expect(screen.getByLabelText("이름")).toHaveAttribute(
      "aria-describedby",
      "name-hint name-error",
    );
  });
});
