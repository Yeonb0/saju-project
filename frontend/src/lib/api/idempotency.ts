// 멱등 명령의 Idempotency-Key 와 본문을 한 쌍으로 묶는다.
// 근거: I-05 · P-09, BE-A 결정 28 (docs/TEAM-QUESTIONS.md Q-17) — 같은 논리적 명령의 재요청
// (재시도 · 새로고침 · PG 복귀 · CSRF 재시도)은 같은 키 · 같은 본문으로 보낸다. 결과가 불명확하다는 이유로 새 키를 만들지 않는다.
// 같은 키에 다른 본문이면 서버가 409 IDEMPOTENCY_KEY_REUSED 를 준다. 키 형식은 API_SPEC 1장 "UUID 권장".

export type IdempotentCommand = Readonly<{
  key: string;
  // 생성 시점에 직렬화한 본문. 재요청 때 다시 직렬화하지 않고 이 문자열을 그대로 보낸다.
  body: string;
}>;

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// 사용자의 구매 의도 하나(버튼을 눌러 시작한 명령 하나)에 한 번만 부른다.
export function createIdempotentCommand(payload: object): IdempotentCommand {
  return Object.freeze({
    key: crypto.randomUUID(),
    body: JSON.stringify(payload),
  });
}

// 서버가 준 주문 ID 에 묶인 명령(충전 승인). PG 복귀 페이지를 새로고침해도 같은 키가 나오도록
// 저장소 없이 주문 ID 를 키로 쓴다 (TOPUP-DONE, BE-A 결정 17 "승인 재호출은 동일 요청의 멱등키").
// 주문 ID 가 UUID 가 아니면 조용히 넘어가지 않고 던진다.
export function createOrderBoundCommand(
  orderId: string,
  payload: object,
): IdempotentCommand {
  if (!UUID_PATTERN.test(orderId)) {
    throw new Error("주문 ID 가 UUID 형식이 아니다");
  }
  return Object.freeze({ key: orderId, body: JSON.stringify(payload) });
}

// 포트(MOCK-PORT) 호출용 키. 구매 의도 하나(버튼을 눌러 시작한 명령 하나)에 한 번만 만들고,
// 재시도 · 새로고침에도 같은 키를 넘긴다. 요청 본문은 포트 구현이 같은 입력에서 같은 모양으로 만든다.
export function createIdempotencyKey(): string {
  return crypto.randomUUID();
}
