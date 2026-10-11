// 포트 구현 선택은 이 파일 한 곳에서만 한다 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 진짜 구현(src/lib/api/adapters)은 OpenAPI 수령 후 만든다. 그 전에 진짜 모드로 부르면 조용히 넘어가지 않고 던진다.
// 가짜 시나리오는 NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO (비우면 credited),
// NEXT_PUBLIC_MOCK_SESSION_SCENARIO (비우면 signed_out), NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO (비우면 fulfilled).
// 모르는 값은 기본값으로 덮지 않고 던진다.
// 가짜 모드에서는 화면 안 패널(MOCK-PANEL)이 고른 저장값이 환경 변수보다 먼저다: 저장값 → 환경 변수 → 기본값 (src/mocks/overrides.ts).
import {
  createFakeAccount,
  FAKE_SESSION_SCENARIOS,
  type FakeAccount,
  type FakeSessionScenario,
} from "@/mocks/account";
import { createFakeBasicSajuPort } from "@/mocks/basicSaju";
import {
  createFakeFortunePort,
  FAKE_FORTUNE_SCENARIOS,
  type FakeFortuneScenario,
} from "@/mocks/fortune";
import { readMockOverrides } from "@/mocks/overrides";
import { createFakePaymentLauncher } from "@/mocks/paymentLauncher";
import { createFakePersonPort } from "@/mocks/person";
import { createFakeReadings, type FakeReadings } from "@/mocks/reading";
import { createFakeSessionPort } from "@/mocks/session";
import {
  createFakeTopUpPort,
  FAKE_TOP_UP_SCENARIOS,
  type FakeTopUpScenario,
} from "@/mocks/topUp";
import { createFakeWallet, type FakeWallet } from "@/mocks/wallet";
import type { BasicSajuPort } from "./basicSaju";
import type { FortunePort } from "./fortune";
import { API_MODE, type ApiMode } from "./mode";
import type { PaymentLauncher } from "./paymentLauncher";
import type { PersonPort } from "./person";
import type { ReadingPort } from "./reading";
import type { SessionPort } from "./session";
import type { TopUpPort } from "./topUp";

export function resolveTopUpScenario(
  raw: string | undefined,
): FakeTopUpScenario {
  if (raw === undefined || raw === "") return "credited";
  if ((FAKE_TOP_UP_SCENARIOS as readonly string[]).includes(raw)) {
    return raw as FakeTopUpScenario;
  }
  throw new Error(`NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO 값이 잘못됐다: ${raw}`);
}

function noRealYet(name: string): never {
  throw new Error(
    `${name} 의 진짜 구현이 아직 없다 — OpenAPI 수령 후 adapters 에서 만든다 (MOCK-PORT)`,
  );
}

export function selectTopUpPort(
  mode: ApiMode,
  scenarioRaw: string | undefined,
  wallet?: FakeWallet,
): TopUpPort {
  if (mode !== "mock") return noRealYet("충전 포트");
  return createFakeTopUpPort({
    scenario: resolveTopUpScenario(scenarioRaw),
    wallet,
  });
}

export function selectPaymentLauncher(
  mode: ApiMode,
  navigate: (url: string) => void,
): PaymentLauncher {
  if (mode !== "mock") return noRealYet("결제창 포트");
  return createFakePaymentLauncher(navigate);
}

// 가짜 지갑은 탭 하나에 하나 — 가짜 충전과 가짜 운세 구매가 같은 잔액을 본다
let fakeWallet: FakeWallet | null = null;

function getFakeWallet(): FakeWallet {
  // 시작 잔액: 패널 저장값, 없으면 기본 픽스처 (MOCK-PANEL)
  fakeWallet ??= createFakeWallet(readMockOverrides().balance);
  return fakeWallet;
}

// 가짜 서버는 탭 하나에 하나 — 화면을 옮겨 다녀도 같은 주문 · 잔액을 본다
let topUpPort: TopUpPort | null = null;

export function getTopUpPort(): TopUpPort {
  topUpPort ??= selectTopUpPort(
    API_MODE,
    readMockOverrides().topUp ?? process.env.NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO,
    getFakeWallet(),
  );
  return topUpPort;
}

export function resolveFortuneScenario(
  raw: string | undefined,
): FakeFortuneScenario {
  if (raw === undefined || raw === "") return "fulfilled";
  if ((FAKE_FORTUNE_SCENARIOS as readonly string[]).includes(raw)) {
    return raw as FakeFortuneScenario;
  }
  throw new Error(`NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO 값이 잘못됐다: ${raw}`);
}

export function selectFortunePort(
  mode: ApiMode,
  scenarioRaw: string | undefined,
  wallet?: FakeWallet,
  readings?: FakeReadings,
): FortunePort {
  if (mode !== "mock") return noRealYet("운세 구매 포트");
  return createFakeFortunePort({
    scenario: resolveFortuneScenario(scenarioRaw),
    wallet,
    onFulfilled: readings ? (product) => readings.create(product) : undefined,
  });
}

export function selectReadingPort(
  mode: ApiMode,
  readings: FakeReadings,
): ReadingPort {
  if (mode !== "mock") return noRealYet("결과 포트");
  return readings.port;
}

// 가짜 결과는 탭 하나에 하나 — 가짜 구매가 만든 결과를 결과 화면이 다시 연다
let fakeReadings: FakeReadings | null = null;

function getFakeReadings(): FakeReadings {
  fakeReadings ??= createFakeReadings();
  return fakeReadings;
}

let fortunePort: FortunePort | null = null;

export function getFortunePort(): FortunePort {
  fortunePort ??= selectFortunePort(
    API_MODE,
    readMockOverrides().fortune ??
      process.env.NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO,
    getFakeWallet(),
    getFakeReadings(),
  );
  return fortunePort;
}

let readingPort: ReadingPort | null = null;

export function getReadingPort(): ReadingPort {
  readingPort ??= selectReadingPort(API_MODE, getFakeReadings());
  return readingPort;
}

export function getPaymentLauncher(
  navigate: (url: string) => void,
): PaymentLauncher {
  return selectPaymentLauncher(API_MODE, navigate);
}

export function resolveSessionScenario(
  raw: string | undefined,
): FakeSessionScenario {
  if (raw === undefined || raw === "") return "signed_out";
  if ((FAKE_SESSION_SCENARIOS as readonly string[]).includes(raw)) {
    return raw as FakeSessionScenario;
  }
  throw new Error(`NEXT_PUBLIC_MOCK_SESSION_SCENARIO 값이 잘못됐다: ${raw}`);
}

export function selectSessionPort(
  mode: ApiMode,
  scenarioRaw: string | undefined,
  account?: FakeAccount,
): SessionPort {
  if (mode !== "mock") return noRealYet("세션 포트");
  return createFakeSessionPort({
    scenario: resolveSessionScenario(scenarioRaw),
    account,
  });
}

export function selectPersonPort(
  mode: ApiMode,
  account: FakeAccount,
): PersonPort {
  if (mode !== "mock") return noRealYet("인물 포트");
  return createFakePersonPort(account);
}

export function selectBasicSajuPort(
  mode: ApiMode,
  account: FakeAccount,
): BasicSajuPort {
  if (mode !== "mock") return noRealYet("오행분석 포트");
  return createFakeBasicSajuPort(account);
}

// 가짜 계정은 탭 하나에 하나 — 세션 · 인물 포트가 같은 계정을 본다
let fakeAccount: FakeAccount | null = null;

function getFakeAccount(): FakeAccount {
  fakeAccount ??= createFakeAccount(
    resolveSessionScenario(
      readMockOverrides().session ??
        process.env.NEXT_PUBLIC_MOCK_SESSION_SCENARIO,
    ),
  );
  return fakeAccount;
}

// 세션도 탭 하나에 하나
let sessionPort: SessionPort | null = null;

export function getSessionPort(): SessionPort {
  sessionPort ??= selectSessionPort(
    API_MODE,
    readMockOverrides().session ??
      process.env.NEXT_PUBLIC_MOCK_SESSION_SCENARIO,
    getFakeAccount(),
  );
  return sessionPort;
}

let personPort: PersonPort | null = null;

export function getPersonPort(): PersonPort {
  personPort ??= selectPersonPort(API_MODE, getFakeAccount());
  return personPort;
}

let basicSajuPort: BasicSajuPort | null = null;

export function getBasicSajuPort(): BasicSajuPort {
  basicSajuPort ??= selectBasicSajuPort(API_MODE, getFakeAccount());
  return basicSajuPort;
}
