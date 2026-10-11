"use client";

// "use client" 이유: 패널 열림 · 고른 값은 브라우저 상태이고, 저장값(localStorage)은 마운트 뒤에 읽는다 (서버 렌더와 어긋나지 않게).
// MOCK-PANEL — 가짜 모드(API_MODE === "mock") 개발 도구. 팀원이 Vercel 미리보기에서 버튼으로 가짜 시나리오를 바꿔 흐름을 확인한다.
// 진짜 모드 · 운영에서는 아무것도 그리지 않는다. PostHog · Sentry 로 아무것도 보내지 않는다.
// 이 패널의 글자는 개발용이라 PD 문구 대상이 아니다. 앱 Modal(Radix) · vaul 과 섞이지 않게 단순 div 로 그린다.
import { useEffect, useState } from "react";
import { API_MODE } from "@/lib/ports/mode";
import { clearPurchaseSelection } from "@/lib/purchase/restore";
import { FAKE_SESSION_SCENARIOS, type FakeSessionScenario } from "./account";
import { FAKE_FORTUNE_SCENARIOS, type FakeFortuneScenario } from "./fortune";
import {
  clearMockOverrides,
  MOCK_BALANCES,
  type MockOverrides,
  readMockOverrides,
  writeMockOverrides,
} from "./overrides";
import { FAKE_TOP_UP_SCENARIOS, type FakeTopUpScenario } from "./topUp";

const SESSION_HELP: Record<FakeSessionScenario, string> = {
  signed_out: "로그아웃 상태",
  new_user: "로그인하면 본인 정보 없음",
  signed_in: "로그인 · 본인 정보 있음",
  signed_in_without_person: "로그인 · 본인 정보 없음",
  signed_in_with_other: "로그인 · 본인 + 저장된 타인 1명",
};
const TOP_UP_HELP: Record<FakeTopUpScenario, string> = {
  credited: "바로 완료",
  paid_then_credited: "잠시 뒤 완료",
  stuck_paid: "결제됨에서 멈춤",
  confirm_lost: "승인 응답 끊김",
  processing_409: "처리 중 409",
  rejected: "거절",
};
const FORTUNE_HELP: Record<FakeFortuneScenario, string> = {
  fulfilled: "성공",
  generation_failed: "생성 실패(환급)",
  processing_409: "처리 중 409",
  quote_expired: "견적 만료",
};

const SHORTCUTS = [
  "/login",
  "/onboarding",
  "/suneung",
  "/wallet",
  "/me",
  "/about",
] as const;

// 환경 변수는 빌드 때 글자 그대로 치환되므로 이 형태로 직접 읽는다
const ENV = {
  session: process.env.NEXT_PUBLIC_MOCK_SESSION_SCENARIO,
  topUp: process.env.NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO,
  fortune: process.env.NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO,
} as const;
const DEFAULTS = {
  session: "signed_out",
  topUp: "credited",
  fortune: "fulfilled",
  balance: 7,
} as const;

type Key = keyof MockOverrides;

function effective(
  stored: MockOverrides,
  key: Key,
): { value: string; source: string } {
  const fromStore = stored[key];
  if (fromStore !== undefined)
    return { value: String(fromStore), source: "저장값" };
  if (key !== "balance") {
    const fromEnv = ENV[key];
    if (fromEnv !== undefined && fromEnv !== "") {
      return { value: fromEnv, source: "환경 변수" };
    }
  }
  return { value: String(DEFAULTS[key]), source: "기본값" };
}

export function MockPanel() {
  if (API_MODE !== "mock") return null;
  return <Panel />;
}

function Panel() {
  const [open, setOpen] = useState(false);
  const [stored, setStored] = useState<MockOverrides>({});
  const [choice, setChoice] = useState<MockOverrides>({});

  useEffect(() => {
    setStored(readMockOverrides());
  }, []);

  function apply() {
    writeMockOverrides({ ...stored, ...choice });
    clearPurchaseSelection();
    window.location.reload();
  }

  function reset() {
    clearMockOverrides();
    clearPurchaseSelection();
    window.location.reload();
  }

  function group(
    key: Key,
    title: string,
    options: readonly (string | number)[],
    help?: Record<string, string>,
  ) {
    const now = effective({ ...stored, ...choice }, key);
    return (
      <fieldset className="mt-[8px] border border-black p-[8px]">
        <legend>{title}</legend>
        <p>
          현재: {now.value} ({now.source})
        </p>
        {options.map((option) => (
          <label key={option} className="block">
            <input
              type="radio"
              name={`mock-${key}`}
              value={String(option)}
              checked={now.value === String(option)}
              onChange={() =>
                setChoice((c) => ({ ...c, [key]: option }) as MockOverrides)
              }
            />{" "}
            {option}
            {help ? ` — ${help[String(option)]}` : null}
          </label>
        ))}
      </fieldset>
    );
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="fixed bottom-[calc(8px+env(safe-area-inset-bottom))] left-[8px] z-[2147483647] border border-black bg-white px-[6px] py-[2px] text-[12px] text-black"
      >
        가짜
      </button>
      {open ? (
        <section
          aria-label="가짜 데이터 패널"
          className="fixed bottom-[calc(40px+env(safe-area-inset-bottom))] left-[8px] z-[2147483647] max-h-[80dvh] w-[min(360px,calc(100%-16px))] overflow-y-auto border border-black bg-white p-[12px] text-[12px] text-black"
        >
          <p>개발용 가짜 데이터 — 새로고침하면 상태가 처음으로 돌아갑니다</p>
          {group("session", "세션", FAKE_SESSION_SCENARIOS, SESSION_HELP)}
          {group("topUp", "충전 결과", FAKE_TOP_UP_SCENARIOS, TOP_UP_HELP)}
          {group(
            "fortune",
            "운세 구매 결과",
            FAKE_FORTUNE_SCENARIOS,
            FORTUNE_HELP,
          )}
          {group("balance", "시작 잔액", MOCK_BALANCES)}
          <nav
            aria-label="바로 가기"
            className="mt-[8px] flex flex-wrap gap-x-[12px]"
          >
            {SHORTCUTS.map((path) => (
              // 전체 새로고침으로 이동한다 (일반 a)
              <a key={path} href={path} className="underline">
                {path}
              </a>
            ))}
          </nav>
          <div className="mt-[8px] flex gap-x-[8px]">
            <button
              type="button"
              onClick={apply}
              className="border border-black px-[6px] py-[2px]"
            >
              적용하고 처음부터
            </button>
            <button
              type="button"
              onClick={reset}
              className="border border-black px-[6px] py-[2px]"
            >
              기본값으로
            </button>
          </div>
        </section>
      ) : null}
    </>
  );
}
