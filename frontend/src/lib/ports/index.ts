// 포트 구현 선택은 이 파일 한 곳에서만 한다 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 진짜 구현(src/lib/api/adapters)은 OpenAPI 수령 후 만든다. 그 전에 진짜 모드로 부르면 조용히 넘어가지 않고 던진다.
// 가짜 시나리오는 NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO (비우면 credited),
// NEXT_PUBLIC_MOCK_SESSION_SCENARIO (비우면 signed_out), NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO (비우면 fulfilled).
// 모르는 값은 기본값으로 덮지 않고 던진다.
import {
  createFakeAccount,
  FAKE_SESSION_SCENARIOS,
  type FakeAccount,
  type FakeSessionScenario,
} from "@/mocks/account";
import {
  createFakeFortunePort,
  FAKE_FORTUNE_SCENARIOS,
  type FakeFortuneScenario,
} from "@/mocks/fortune";
import { createFakePaymentLauncher } from "@/mocks/paymentLauncher";
import { createFakePersonPort } from "@/mocks/person";
import { createFakeSessionPort } from "@/mocks/session";
import {
  createFakeTopUpPort,
  FAKE_TOP_UP_SCENARIOS,
  type FakeTopUpScenario,
} from "@/mocks/topUp";
import { createFakeWallet, type FakeWallet } from "@/mocks/wallet";
import type { FortunePort } from "./fortune";
import { API_MODE, type ApiMode } from "./mode";
import type { PaymentLauncher } from "./paymentLauncher";
import type { PersonPort } from "./person";
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
  fakeWallet ??= createFakeWallet();
  return fakeWallet;
}

// 가짜 서버는 탭 하나에 하나 — 화면을 옮겨 다녀도 같은 주문 · 잔액을 본다
let topUpPort: TopUpPort | null = null;

export function getTopUpPort(): TopUpPort {
  topUpPort ??= selectTopUpPort(
    API_MODE,
    process.env.NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO,
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
): FortunePort {
  if (mode !== "mock") return noRealYet("운세 구매 포트");
  return createFakeFortunePort({
    scenario: resolveFortuneScenario(scenarioRaw),
    wallet,
  });
}

let fortunePort: FortunePort | null = null;

export function getFortunePort(): FortunePort {
  fortunePort ??= selectFortunePort(
    API_MODE,
    process.env.NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO,
    getFakeWallet(),
  );
  return fortunePort;
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

// 가짜 계정은 탭 하나에 하나 — 세션 · 인물 포트가 같은 계정을 본다
let fakeAccount: FakeAccount | null = null;

function getFakeAccount(): FakeAccount {
  fakeAccount ??= createFakeAccount(
    resolveSessionScenario(process.env.NEXT_PUBLIC_MOCK_SESSION_SCENARIO),
  );
  return fakeAccount;
}

// 세션도 탭 하나에 하나
let sessionPort: SessionPort | null = null;

export function getSessionPort(): SessionPort {
  sessionPort ??= selectSessionPort(
    API_MODE,
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
