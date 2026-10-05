import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { kst } from "@/lib/date";
import { PersonForm } from "./PersonForm";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 사용자 · 규칙과 무관하다. 날짜 · 이름은 검증 경계를 보기 위한 임의 값이다.
const TODAY = kst("2026-10-04T12:00:00+09:00").startOf("day");

function setup(onSubmit = vi.fn()) {
  const user = userEvent.setup();
  render(<PersonForm onSubmit={onSubmit} today={TODAY} />);
  return { user, onSubmit };
}

const field = {
  name: () => screen.getByLabelText("이름"),
  birthDate: () => screen.getByLabelText("생년월일"),
  solar: () => screen.getByRole("radio", { name: "양력" }),
  lunar: () => screen.getByRole("radio", { name: "음력" }),
  time: () => screen.getByLabelText("태어난 시간"),
  timeUnknown: () => screen.getByRole("checkbox", { name: "시간 모름" }),
  gender: () => screen.getByLabelText("성별"),
  submit: () => screen.getByRole("button", { name: "저장하기" }),
};

describe("PersonForm (본인)", () => {
  it("이름 → 생년월일 → 양력 · 음력 → 태어난 시간 → 시간 모름 → 성별 → 저장하기 순서로 그린다", () => {
    setup();
    const order = [
      field.name(),
      field.birthDate(),
      field.solar(),
      field.lunar(),
      field.time(),
      field.timeUnknown(),
      field.gender(),
      field.submit(),
    ];
    for (let i = 1; i < order.length; i++) {
      expect(
        order[i - 1].compareDocumentPosition(order[i]) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    }
    expect(screen.queryByRole("checkbox", { name: "윤달" })).toBeNull();
  });

  it("음력을 고르면 윤달이 나타나고, 양력으로 바꾸면 사라진다", async () => {
    const { user } = setup();
    await user.click(field.lunar());
    expect(screen.getByRole("checkbox", { name: "윤달" })).toBeInTheDocument();
    await user.click(field.solar());
    expect(screen.queryByRole("checkbox", { name: "윤달" })).toBeNull();
  });

  it("시간 모름을 체크하면 시간 입력이 비활성", async () => {
    const { user } = setup();
    expect(field.time()).toBeEnabled();
    await user.click(field.timeUnknown());
    expect(field.time()).toBeDisabled();
  });

  it("빈 채로 제출하면 호출 없이 필수 항목 오류가 보인다", async () => {
    const { user, onSubmit } = setup();
    await user.click(field.submit());
    await waitFor(() =>
      expect(screen.getAllByText("필수 항목입니다")).toHaveLength(5),
    );
    // 이름 · 생년월일 · 달력 · 태어난 시간 · 성별
    expect(field.name()).toHaveAttribute("aria-invalid", "true");
    expect(field.birthDate()).toHaveAttribute("aria-invalid", "true");
    expect(field.time()).toHaveAttribute("aria-invalid", "true");
    expect(field.gender()).toHaveAttribute("aria-invalid", "true");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("오늘 이후 생년월일은 막는다", async () => {
    const { user, onSubmit } = setup();
    await user.type(field.birthDate(), "2026-10-05");
    await user.click(field.submit());
    expect(
      await screen.findByText("오늘 이후 날짜는 입력할 수 없습니다"),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("정상 입력은 변환된 값으로 한 번 제출한다", async () => {
    const { user, onSubmit } = setup();
    await user.type(field.name(), "  픽스처  ");
    await user.type(field.birthDate(), "2008-03-15");
    await user.click(field.solar());
    await user.click(field.timeUnknown());
    await user.selectOptions(field.gender(), "여성");
    await user.click(field.submit());
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toEqual({
      name: "픽스처",
      calendar: "solar",
      isLeapMonth: false,
      birthDate: "2008-03-15",
      birthTime: null,
      gender: "female",
      relation: null,
    });
  });

  it("음력 윤달 · 시간 입력 · 선택하지 않음", async () => {
    const { user, onSubmit } = setup();
    await user.type(field.name(), "픽스처");
    await user.type(field.birthDate(), "1990-02-30");
    await user.click(field.lunar());
    await user.click(screen.getByRole("checkbox", { name: "윤달" }));
    await user.type(field.time(), "07:30");
    await user.selectOptions(field.gender(), "선택하지 않음");
    await user.click(field.submit());
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      calendar: "lunar",
      isLeapMonth: true,
      birthDate: "1990-02-30",
      birthTime: "07:30",
      gender: "unspecified",
    });
  });

  it("같은 틱에 두 번 제출해도 onSubmit 은 한 번", async () => {
    let finish: () => void = () => {};
    const onSubmit = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    const { user } = setup(onSubmit);
    await user.type(field.name(), "픽스처");
    await user.type(field.birthDate(), "2008-03-15");
    await user.click(field.solar());
    await user.click(field.timeUnknown());
    await user.selectOptions(field.gender(), "여성");
    const form = field.submit().closest("form") as HTMLFormElement;
    form.requestSubmit();
    form.requestSubmit();
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    finish();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });
});
