// MOCK-PANEL — 가짜 모드(API_MODE === "mock") 개발 도구. 팀원이 화면 안 패널로 고른 가짜 시나리오 · 시작 잔액을 localStorage 에 둔다.
// 브라우저 저장소 규칙(frontend/CLAUDE.md — 준비물 체크만)의 예외다: 진짜 모드 · 운영에서는 읽지도 쓰지도 않는다.
// 출처 우선순위는 ports/index.ts 가 정한다: 저장값 → 환경 변수 → 기존 기본값.
import { z } from "zod";
import { API_MODE, type ApiMode } from "@/lib/ports/mode";
import { FAKE_SESSION_SCENARIOS, type FakeSessionScenario } from "./account";
import { FAKE_FORTUNE_SCENARIOS, type FakeFortuneScenario } from "./fortune";
import { FAKE_TOP_UP_SCENARIOS, type FakeTopUpScenario } from "./topUp";

export const MOCK_OVERRIDES_KEY = "mockOverrides";

// 시작 잔액 선택지 — 픽스처일 뿐이며 실제 잔액 · 규칙과 무관하다 (7 은 src/mocks/wallet.ts 의 기본 픽스처)
export const MOCK_BALANCES = [0, 7, 100] as const;
export type MockBalance = (typeof MOCK_BALANCES)[number];

export type MockOverrides = {
  session?: FakeSessionScenario;
  topUp?: FakeTopUpScenario;
  fortune?: FakeFortuneScenario;
  balance?: MockBalance;
};

const storedSchema = z.object({
  v: z.literal(1),
  session: z.enum(FAKE_SESSION_SCENARIOS).optional(),
  topUp: z.enum(FAKE_TOP_UP_SCENARIOS).optional(),
  fortune: z.enum(FAKE_FORTUNE_SCENARIOS).optional(),
  balance: z.union([z.literal(0), z.literal(7), z.literal(100)]).optional(),
});

function storage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    return null;
  }
}

function dropBroken(store: Storage) {
  console.warn("mockOverrides 저장값이 올바르지 않아 지웠습니다");
  try {
    store.removeItem(MOCK_OVERRIDES_KEY);
  } catch {
    // 지우지 못해도 다음 읽기에서 같은 검사를 거친다
  }
}

// 모드를 인자로 받는 내부 함수 — 테스트에서 모드를 바꿔 넣는다
export function readOverridesFor(mode: ApiMode): MockOverrides {
  if (mode !== "mock") return {};
  const store = storage();
  if (!store) return {};
  let raw: string | null;
  try {
    raw = store.getItem(MOCK_OVERRIDES_KEY);
  } catch {
    return {};
  }
  if (raw === null) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    dropBroken(store);
    return {};
  }
  const result = storedSchema.safeParse(parsed);
  if (!result.success) {
    dropBroken(store);
    return {};
  }
  const { v: _v, ...overrides } = result.data;
  return overrides;
}

export function writeOverridesFor(mode: ApiMode, value: MockOverrides): void {
  if (mode !== "mock") return;
  const store = storage();
  if (!store) return;
  const result = storedSchema.safeParse({ v: 1, ...value });
  if (!result.success) {
    console.warn("mockOverrides 값이 올바르지 않아 저장하지 않았습니다");
    return;
  }
  try {
    store.setItem(MOCK_OVERRIDES_KEY, JSON.stringify(result.data));
  } catch {
    // 저장소가 막혔다 — 개발 도구라 조용히 넘어간다
  }
}

export function clearOverridesFor(mode: ApiMode): void {
  if (mode !== "mock") return;
  try {
    storage()?.removeItem(MOCK_OVERRIDES_KEY);
  } catch {
    // 지우지 못해도 다음 읽기에서 같은 검사를 거친다
  }
}

export const readMockOverrides = () => readOverridesFor(API_MODE);
export const writeMockOverrides = (value: MockOverrides) =>
  writeOverridesFor(API_MODE, value);
export const clearMockOverrides = () => clearOverridesFor(API_MODE);
