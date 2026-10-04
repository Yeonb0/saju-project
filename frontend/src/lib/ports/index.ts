// 포트 구현 선택은 이 파일 한 곳에서만 한다 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 진짜 구현(src/lib/api/adapters)은 OpenAPI 수령 후 만든다. 그 전에 진짜 모드로 부르면 조용히 넘어가지 않고 던진다.
// 가짜 시나리오는 NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO (비우면 credited). 모르는 값은 기본값으로 덮지 않고 던진다.
import { createFakePaymentLauncher } from "@/mocks/paymentLauncher";
import {
  createFakeTopUpPort,
  FAKE_TOP_UP_SCENARIOS,
  type FakeTopUpScenario,
} from "@/mocks/topUp";
import { API_MODE, type ApiMode } from "./mode";
import type { PaymentLauncher } from "./paymentLauncher";
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
): TopUpPort {
  if (mode !== "mock") return noRealYet("충전 포트");
  return createFakeTopUpPort({ scenario: resolveTopUpScenario(scenarioRaw) });
}

export function selectPaymentLauncher(
  mode: ApiMode,
  navigate: (url: string) => void,
): PaymentLauncher {
  if (mode !== "mock") return noRealYet("결제창 포트");
  return createFakePaymentLauncher(navigate);
}

// 가짜 서버는 탭 하나에 하나 — 화면을 옮겨 다녀도 같은 주문 · 잔액을 본다
let topUpPort: TopUpPort | null = null;

export function getTopUpPort(): TopUpPort {
  topUpPort ??= selectTopUpPort(
    API_MODE,
    process.env.NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO,
  );
  return topUpPort;
}

export function getPaymentLauncher(
  navigate: (url: string) => void,
): PaymentLauncher {
  return selectPaymentLauncher(API_MODE, navigate);
}
