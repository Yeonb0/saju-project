@AGENTS.md

# CLAUDE.md — frontend

수능 수험생을 핵심 사용자로 시작하는 사주 + 부적 모바일 웹서비스 **뿌기사주**.
창업 경진대회 출전작 — **2026-10-31 출시, 11/21까지의 실제 수익으로 평가.** 수능은 11/19.

> 이 파일은 FE 작업 규칙이다. 이 파일과 `docs/` 의 PHASES · PROGRESS · FRONTEND · TEAM-QUESTIONS 는 **FE 개인 작업 문서이며 팀 합의 문서가 아니다** (DOCS-FE-OWN).
> Claude Code 는 `frontend/` 에서 시작한다 — 루트에는 `CLAUDE.md` 가 없어서, 루트에서 시작하면 이 규칙이 세션 처음에 읽히지 않는다.
> 이 파일과 코드 주석 속 문서 경로(`docs/PHASES.md` 등)는 `frontend/` 기준이다. 저장소 루트의 `docs/` 는 팀 공용 문서다 (아래 "결정 기준").

## 지금 최우선 — PG 심사 트랙 (PG-FIRST)

- PG 심사 요청 전까지는 **백엔드 연결과 결제 플로우만** 만든다. 체크박스는 `docs/PHASES.md` "PG 심사 트랙" 의 PG-1 ~ PG-5. **2026-10-16 결제 구조가 등껍질 충전 · 차감에서 운세 건별 원화 결제로 바뀌었다 (DIRECT-PAY)** — 아래 "직접결제" 규칙. PG사는 아직 선정 전이다(BE 답 10/16, R-10) — FE 결제창은 토스 V2 형식으로 만들어 두었고 실제 승인 확인은 선정 · 계약 뒤. 계좌이체 대안 화면은 만들지 않는다. 호스팅은 AWS Amplify 로 옮길 예정(R-11).
- 그때까지 **디자인 요소를 추가하지 않는다**: 디자인 토큰 · 손그림 프레임 · 글꼴 · 캐릭터 · 꾸밈 스타일. 화면은 AppShell 과 기본 HTML 요소, 앱 기둥 폭(`src/lib/layout.ts` COLUMN_WIDTH) 정도의 최소 레이아웃으로만 만든다. 지시문이 시키지 않은 스타일은 넣지 않는다.

## 세션 규칙 (반드시 지킬 것)

1. **세션 시작 시** `docs/PHASES.md` · `docs/PROGRESS.md` · `docs/TEAM-QUESTIONS.md` 를 먼저 읽는다. 현재 Phase 와 다음 작업을 확인한 뒤 시작한다.
2. 작업은 체크박스 단위로 한다. 체크박스에 없는 작업이 필요하면 먼저 사용자에게 말한다.
3. **세션 종료 시** 문서를 직접 고치지 않고, 끝낸 체크박스 · 막힌 점을 보고한다. 문서 갱신은 웹 대화에서 작성한 완성본으로 한다 (아래 "문서 변경 규칙").
4. 결정이 필요한 항목에 걸린 작업은 추측으로 진행하지 않고, 그 결정 ID 를 짚어 보고하고 멈춘다.

## 결정 기준 (DOCS-GITHUB)

- 팀 문서의 원본은 **GitHub 저장소** 다 — 루트 `docs/` (`PENDING_DECISIONS.md` · `PRD.md` · `FUNCTIONAL_SPEC.md` · `API_SPEC.md` · `COMMON_RESPONSE_AND_ERROR_CODES.md` · `ERD.md`)와 `backend/docs/BACKEND_ROLE_SPLIT.md`. 다른 곳(Notion 등)의 사본은 기준이 아니다.
- 결정 원본은 `docs/PENDING_DECISIONS.md`. 결정은 그 ID(P- · F- · G- · A- · S- · T- · X- · O- · D- · I-)로 부른다. 옛 FE 결정 번호 D-01 ~ D-16 은 닫혔고(DECISION-IDS) 기록에서만 "구 D-xx" 로 부른다. 대응표는 `docs/PHASES.md` 7장.
- 상태가 `미정` · `부분 확정`의 미정 부분 · `보류` 인 것, 그리고 가격 · 재화 수량 · 상품 구성 · 문구 · 글꼴 · 사업자 정보는 정하지 않는다. 기본값도 넣지 않는다. 자리가 필요하면 `TODO(결정 ID)` 주석만 둔다 (예: `TODO(P-03A)`, `TODO(O-01)`).
- 팀 문서끼리 다르면 `PENDING_DECISIONS.md` 의 확정 항목을 기준으로 읽고, 차이는 보고한다 (웹 대화가 `docs/TEAM-QUESTIONS.md` 에 올린다).
- `docs/API_SPEC.md` 는 계약 초안이다. **백엔드 요청 · 응답 타입의 출처는 OpenAPI 생성본뿐이다.** 스펙에 없는 타입 · 응답 모양을 지어내지 않는다. 생성 타입은 `src/lib/api/adapters/` 에서만 쓴다.
- **OpenAPI 전 개발은 포트 + 가짜 구현으로 한다 (MOCK-PORT, 2026-10-04)**: 화면은 API 를 직접 부르지 않고 FE 가 정의한 포트(`src/lib/ports/`)만 부른다. 포트 모델은 화면이 쓰는 FE 모델이고 백엔드 타입이 아니다. 구현은 가짜(`src/mocks/`, 메모리 픽스처)와 진짜(`src/lib/api/adapters/`, OpenAPI 수령 후 생성 타입 → 포트 모델 변환) 둘이다. 세부 규칙은 `docs/FRONTEND.md` 1-2. **2026-10-12 부터 `src/lib/ports/index.ts` 는 모든 포트를 진짜 adapter 에 연결한다** — 일반 운세 5종 견적 · 구매도 2026-10-14 부터 운세별 경로로 연결(`2f505f7`, SUNEUNG-ADAPTER-FIRST 해제 — 요청 전 선택 검사 · 견적 응답의 옵션 · 질문 대조). 진짜 모드는 가짜로 돌아가지 않는다.
- 팀 답은 `docs/TEAM-QUESTIONS.md` 에 상태로 적혀 있다 (TQ-ANSWERS). `해결` 은 따른다. `답변 · 반영 대기` · `반영 PR 대기` · `답 엇갈림` 항목은 팀 문서(`main`)에 반영되기 전까지 그 답에 기대는 코드를 만들지 않는다 (예: `/wallet` 라우트는 `FUNCTIONAL_SPEC.md` 2장을 고친 PR `cc60ab3` 이 `main` 에 병합된 뒤).

## 문서 변경 규칙 (DOCS-WEB-ONLY · DOCS-PLACE)

- 저장소의 Markdown 문서(`frontend/` 의 문서, 루트 `README.md` · `docs/`, `backend/` 의 문서, `.github/pull_request_template.md` 등 모든 `*.md`)는 Claude Code 가 수정 · 생성 · 삭제하지 않는다.
- 문서 변경은 웹 대화(채팅 Claude)에서 작성한 완성본으로만 한다. Claude Code 는 diff 확인과 커밋을 한다.
- 예외 — 완성본 배치 (DOCS-PLACE): 지시문에 파일 목록과 SHA-256 이 적혀 있으면, Claude Code 가 완성본 압축을 풀어 지정 경로로 복사하고 지시문이 지정한 문서를 삭제 · 이동할 수 있다. 내용은 한 글자도 바꾸지 않고, 배치 후 해시가 지시문과 같은지 확인한다. 하나라도 다르면 커밋하지 않는다.
- 문서가 코드 · Figma · 다른 문서와 어긋나 보이면 문서를 고치지 말고 보고한다.
- 코드 파일 안의 주석은 문서가 아니다 (코드 수정 규칙을 따른다).

## 팀

| 약칭 | 역할 (`backend/docs/BACKEND_ROLE_SPLIT.md`) |
|---|---|
| PD | 기획&디자인 — UI 비주얼, 콘텐츠 문구, 약관, 마케팅 |
| FE | 프론트엔드 1명 — 전 화면 + 캐릭터 일러스트 |
| BE-A | 돈 · 주문 · 선물 전달 — 공통 응답 · 멱등성, 상품 · 견적, 직접결제 주문 · 승인 · 결제 뒤 생성, (옛) 등껍질 원장, PG 결제 · 환불, 선물 · 알림톡, 관리자 |
| BE-B | 사용자 · 계산 · 결과 · 미디어 — 카카오 인증 · 세션 · CSRF, 인물, 만세력 · Liner 해석 문장, 결과, 부적 · R2, 카카오톡 공유 리소스 |

## Figma 사용 규칙 (FIGMA-WEB-ONLY)

- **Claude Code 는 Figma MCP 를 호출하지 않는다.** Figma 조회는 웹 대화에서만 하고, 구현에 쓸 값은 지시문에 적어서 넘긴다. 지시문에 없는 Figma 값을 추측으로 채우지 않는다.
- 파일 키 `9zEtrqV4SoPbQzuworSNUj`, **최종 와이어프레임 페이지 `와이어프레임 최종` node `195:91`** (FIGMA-FINAL). 화면은 프레임 이름인 화면 ID(HOME- · TODAY- · MY- · TALBOX- · FORT- · MATCH- · PAY- · GIFT- · RECV- · RECV-T- · CSAT-)로 부르고, 대응은 `docs/PHASES.md` 1장 표. 옛 페이지 `17:2` 와 구 번호 #1 ~ #40 은 기록용이다 (`src/lib/screens.ts` 의 `ScreenNo` 는 아직 구 번호).
- 최종 와이어와 팀 문서가 부딪히는 곳(`docs/TEAM-QUESTIONS.md` Q-23 ~ Q-27)은 팀 문서를 따르고, 와이어에만 있는 부분은 만들지 않는다 — 예: 여러 명 선물(GIFT-06 ~ 08, GIFT-01 "수신인 당"), 발송 실패 링크 직접 공유(GIFT-05), 충전 화면 결제 수단 4칸(PAY-01, Q-25 전까지). 네이버 · 구글 로그인 버튼은 PG 심사 후 (LOGIN-3 개정) — 진짜 세션 adapter 는 KAKAO 만 받는다.
- 와이어프레임 위 메모 텍스트(프레임 밖 글상자)는 미결정 사항이다. 와이어의 회색 · 아이콘 · 글꼴은 임시값이다.

## 구현 원칙

- **재사용**: 새 화면 전에 `docs/FRONTEND.md` 3장 공통 컴포넌트를 먼저 확인한다 (`src/components`: AppShell · Button · Card · Chip · FormField · BottomSheet · Modal · LoadingScene). 결과 화면은 **ReadingViewer 하나** 를 수능 · 일반 5종 · 선물 수신에 재사용한다 (VIEWER). 결과 포트 본문은 서버 구조 그대로 `SUNEUNG_GUIDE`(서버 `suneungGuide`) 또는 `SECTIONS`(`key` · `content`, 모르는 key 는 UNKNOWN — 그 섹션만 건너뜀 + Sentry) (RESULT-GUIDE-MODEL, 2026-10-12). 카드 제목 · 교시 · 준비물 분류 이름은 `src/lib/reading/labels.ts` 한 곳(임시값 `TODO(PD 문구)`, Q-40)이고, 모르는 코드에는 이름을 지어내지 않는다.
- **라우트**: 새 페이지는 `src/lib/screens.ts` ROUTES 표에 먼저 있어야 한다 (`screens.test` 가 표 밖 `page.tsx` 를 막는다). 라우트 표의 출처는 `docs/FUNCTIONAL_SPEC.md` 2장. 결제하기 확인은 Checkout 경로 없이 앞 화면 안 Modal 이다 (CHECKOUT-POPUP — 직접결제는 `src/components/DirectCheckout.tsx`). 새 동적 경로 · API 경로가 생기면 `src/lib/observability/maskUrl.ts` 가리기 대상인지 확인한다. `/api/` 경로의 UUID 조각은 `maskUrl` 이 `[id]` 로 가린다 — UUID 가 아닌 ID 가 들어간 API 경로가 생기면 따로 확인한다.
- **API 호출**: 화면 → 포트 → 구현 순서로만 부른다 (MOCK-PORT). `/api/*` 는 `next.config.ts` rewrites 로 백엔드에 프록시한다(같은 출처, I-02). 백엔드 prefix 는 `/api/v1`. 성공 응답은 `{ data, traceId }` 로 감싸서 오고, 오류는 `{ code, message, traceId, fieldErrors, details? }` 다 (`docs/COMMON_RESPONSE_AND_ERROR_CODES.md`). 분기는 `code` 로 하고 `message` 문자열을 비교하지 않는다. 알 수 없는 `code` 는 HTTP status 로 처리한다 (같은 문서 7장). 새 enum 값은 unknown 분기를 둔다.
- **금액은 서버만**: 원화 금액은 서버 견적 · 직접결제 주문 · 상품 응답 값만 표시한다. 클라이언트에서 계산 · 합산 · 하드코딩하지 않는다 (P-09). 결제하기 팝업의 상품명은 견적 `productName`(서버 값) — 없으면 상품 줄을 그리지 않고 지어내지 않는다. `regularPrice` 는 검사만 하고 화면에 쓰지 않는다 (Q-22). 운세 상품 가격은 원화(`KRW`)만 받는다 — 등껍질 가격이 오면 계약 위반 (`274ebf8`). `/about` 의 상품 · 가격도 서버 상품 목록 그대로 (`45d2422`).
- **원화 결제 (P-08 · DIRECT-PAY, 2026-10-16)**: 원화 결제는 운세 한 건씩 바로 하는 직접결제다 — 충전 · 잔액 · 차감이 없다 (선물 · 부적 추가도 같은 방식이지만 화면은 아직 없다). 서버가 만든 주문(`POST /api/v1/direct-payment-batches` 의 `orderId` · `orderName` · `amount`)으로만 결제창을 연다. 결제창 주소: 성공 `/pay/success?flow=direct`, 실패 `/pay/fail?flow=direct&return=<safeReturnTo 를 거친 앞 화면>` — `/pay/success` · `/pay/fail` 은 `flow=direct` 가 아니면 not-found (`cf12a00`). PG 복귀 쿼리의 `paymentKey` · `orderId` · `amount` 는 그대로 서버 승인 요청에 넘기기만 한다. `/pay/success` 는 서버 승인 응답을 받은 뒤에만 성공을 표시한다. **토스 결제창** (TOSS-LAUNCHER-SWITCH · TOSS-ANONYMOUS, 2026-10-11): V2 결제창형(위젯 `renderPaymentWindow` → `requestPayment` redirect), `customerKey: ANONYMOUS`, 클라이언트 키 `NEXT_PUBLIC_TOSS_CLIENT_KEY`(`test_gck_` · `live_gck_` 만 — 시크릿 키는 FE 에 두지 않는다). 가짜 모드는 `NEXT_PUBLIC_MOCK_PAYMENT_LAUNCHER=toss` 일 때만 토스 결제창(돌아오면 가짜 메모리가 비어 `/pay/success` 가 실패 화면 — 의도), 비우거나 `fake` 는 가짜 결제창, 다른 값은 던진다. 결제창 닫기는 `PaymentCanceledError` → 결제하기 팝업에 오류 안내 없음. 결제하기 팝업에는 필수 동의 1개 · 안내 4줄(환불정책 v4 문장 그대로 — `src/lib/payment/directNotice.ts` 한 곳)이 있고 체크 전 결제하기는 비활성.
- **직접결제 완료 (DIRECT-PAY)**: 승인은 PG 복귀 값 그대로 `POST /direct-payment-batches/{orderId}/confirm`, 멱등 키 = 주문 ID (`createOrderBoundCommand`, CONFIRM-KEY). `PAYMENT_PENDING` · 결과 불명은 주문 조회(`GET /direct-payment-batches/{orderId}`) 2초 간격 최대 30초 — 지연을 실패로 표시하거나 새 결제로 유도하지 않는다. 주문이 `PAID` 일 때만 결제 뒤 생성(`POST …/{orderId}/reading-fulfillment`)을 보낸다 — 생성 키는 복원값 `fulfillmentKey`(보내기 전에 저장, 새로고침해도 같은 키). `FULFILLED` + `readingId` 일 때만 「준비가 끝났어요!」 · 「결과 보러 가기」 — 결과 이동은 버튼을 누를 때 한 번, 그때 복원값을 지운다(`06f6dca`). 그 밖의 생성 응답은 구매 상태 조회(`GET /reading-purchases/by-quote/{quoteId}`)로 본다. `REVIEW_REQUIRED` · 409 `PAYMENT_ORDER_NOT_CONFIRMABLE` · `PAYMENT_CONFIRMATION_CONFLICT` · 422 `INVALID_REQUEST` · 404 `RESOURCE_NOT_FOUND` 는 「확인이 필요한 구매예요」 + 문의하기(제목에 결제 주문번호) — 새 주문으로 덮지 않는다. **생성 실패는 「다시 시도」 없이 문의** — 서버 `nextAction` 이 `RETRY_PURCHASE` 여도 두 번 결제로 이어질 수 있다. 취소 · 환급 완료 문장은 쓰지 않는다(Q-65 — 직접결제 자동 취소 없음, BE 10/16). 팝업 다시 열기 · 복귀: 저장한 `orderId` 로 주문 조회 — `PAYMENT_PENDING` 이면 같은 `orderKey` 로 이어 가고, `FAILED` · `CANCELED` · 404 면 새 키, `PAID` · `REVIEW_REQUIRED` · 모르는 상태는 확인 필요 안내. 모르는 주문 상태는 `UNKNOWN`. 수능운의 결제 뒤 생성은 BE 수능운 전용 경로(Q-64) 전이라 진짜 adapter 가 요청 전에 던진다 — 우회 경로를 만들지 않는다. 직접결제 경로는 공유 OpenAPI 에 아직 없어 adapter 는 PR #49 컨트롤러 기준 zod 검사만 한다(TODO(OPENAPI-FE-SYNC), Q-68). 옛 충전 코드(충전 포트 · adapter · 가짜 · `confirmTopUp` · `retryProduct` · 결제창 `top_up` 경로)는 화면 없이 남아 있다 — BE 가 충전 API 를 닫을 때 한 번에 지운다. `/wallet` 은 지웠다(`cf12a00`). TOPUP-DONE · TOPUP-RETRY-URL 은 기록으로만.
- **지갑 확인 필요 (Q-41 5, 2026-10-13 — 옛 등껍질 구매 · 충전 규칙, 직접결제의 확인 필요는 위 규칙)**: `409 WALLET_REVIEW_REQUIRED`(오류 종류 `review_required`)는 운세 구매 · 충전 주문 생성에서 난다. 같은 구매 · 충전을 다시 열지 않고 문의 안내 — 구매 팝업은 "사용하기" 잠금 · 저장한 구매 선택 삭제 · 상태 조회 없음, 충전 화면은 "결제하기" 잠금. 문의 안내는 COMMON-04 · 고객센터 이메일 · 「문의하기」 메일 링크(`src/lib/support/contact.ts`, 제목 규칙 Q-47 — 기능 명세서 v0.4 13장, 직접결제는 `paymentOrder` 제목 `TODO(Q-47)`) (`3f62019` · `4e6cab1`). 고객센터 전화는 `business.ts` 의 값(`c4b1e8d`)만 — `/about` 고객센터 칸에 이메일 · 전화를 보인다(`45d2422`).
- **충전 환불 (Q-41, 2026-10-13 — 옛 충전 규칙, 화면 없음. 직접결제 취소 · 환불은 정책 v4 4 · 5조대로 고객센터 신청이고 FE 신청 화면 · 자동 환불 표시는 없다)**: 환불은 고객센터 접수 → 운영자 관리자 API 실행이다. 사용자 환불 신청 POST 는 서버에서 제거됐다 — FE 는 신청 버튼 · 신청 adapter 를 만들지 않는다. 소유자 조회 `GET /top-up-orders/{orderId}/refund`(포트 `getRefund`)만 둔다 — 완료는 `state` REFUNDED + `completedAt`, 실제 환불액은 `amount`(원 결제액 아님), 기록 없음 404 `RESOURCE_NOT_FOUND` 를 거절로 읽지 않는다. 화면 연결은 PD 배치(Q-41 6) 후. `/api/v1/admin/**` 는 FE 에서 쓰지 않는다.
- **멱등성 (I-05)**: `Idempotency-Key` 는 구매 의도 하나에 하나만 만들고, 재시도 · 새로고침 · PG 복귀 · CSRF 재시도에도 같은 키 · 같은 본문을 쓴다. 결과가 불명확하다는 이유로 새 키를 만들지 않는다. 키와 본문은 `src/lib/api/idempotency.ts` 로 만든다 (구매 의도당 `createIdempotentCommand` 한 번, 충전 승인은 `createOrderBoundCommand` — 주문 ID 키). mutation 자동 재시도는 0 (`makeQueryClient`). `409 IDEMPOTENCY_REQUEST_PROCESSING` 이면 버튼을 다시 열지 않고 상태를 조회한다.
- **CSRF (Q-02 해결)**: `GET /session` 응답의 `csrfToken` 을 변경 요청의 `X-CSRF-Token` 헤더로 보낸다. 출처는 진짜 세션 adapter(`createRealSession` → `{ port, csrf }`) 하나 — `ports/index.ts` 의 `getRealSession()` 이 탭 하나에 하나만 만들어 모든 adapter 가 같은 토큰을 본다. `403 CSRF_FAILED` 면 `GET /session` 을 한 번 불러 새 토큰으로 원래 요청을 **1회만** 재시도하고, 그래도 실패하면 로그인 만료 · 보안 오류로 처리한다. 필드 이름은 OpenAPI 생성본으로 확인한다.
- **사주 결과**: 만세력 계산과 해석 문장 생성(Liner)은 서버가 한다 (S-06). FE 는 AI · 외부 생성 호출을 추가하지 않고, 서버 결과 스냅샷만 표시한다.
- **날짜**: `src/lib/date.ts` (Asia/Seoul) 를 거친다. 기기 시간대를 쓰지 않는다. 수능운 판매 마감은 전날 23:59 KST (F-07).
- **선물**: 1차는 수능운만, 부적은 고르지 않는다(P-03A · T-01). 결제는 DIRECT-PAY 원화(BE PR #49 `direct-gift-batches` — 팀 문서 P-07 은 아직 옛 등껍질, Q-67). 전달은 **1차 직접 링크**(`/g#token=`, 전화번호 미수집) · 알림톡 2차 — 10/15 결정(Q-54 · Q-55), PD 정책 v4 같은 기준. **선물 화면은 PG 결제 확인 뒤에 만든다** — 그 전에는 선물 결제 · 위저드 · 수신 코드를 만들지 않는다(가짜 포트로도). 만들 때: 링크 복사 · 카카오톡 공유, 재발급 버튼은 서버 `canReissue` · `remainingReissues` · `nextReissueAt` 만 따르고 횟수 · 시간을 계산하지 않는다, 원문 링크 `shareUrl` 이 null 이면 조회 기간 종료로 읽는다(수신 권한 종료가 아니다), token 은 fragment 로만 받고 공개 API 에 body 로 보낸다 — 경로 · 쿼리 · 로그 · Sentry · PostHog 에 넣지 않는다(`backend/docs/GIFT_DIRECT_LINK_HANDOFF_20261014.md`). 선물 링크 OG 는 일반 브랜드 안내만(이름 · 생년정보 · 메시지 금지 — 팀 문서 PR #41). 가격 · 단위는 서버 값만. 선물 메시지는 텍스트로만 렌더하고(`dangerouslySetInnerHTML` 금지) OG · PostHog · Sentry 에 넣지 않는다 (G-08).
- **공유**: 개인 결과 · 부적은 카카오톡 공유하기(Kakao JS SDK)로, 서버가 만든 비식별 share 리소스만 쓴다 (G-11). 공유 범위는 G-11 미정.
- **브라우저 저장소**: `localStorage` 는 수능 준비물 체크 상태만 — 예외: 가짜 모드 개발 도구의 `mockOverrides`(MOCK-PANEL, `src/mocks/overrides.ts`, 진짜 모드에서는 읽지도 쓰지도 않는다) (오늘의 운세는 로그인 + 저장된 본인 정보로 바뀌어 생년정보를 기기에 저장하지 않는다, X-01 2026-10-04 변경). `sessionStorage` 는 선물 위저드의 Zustand persist 와 구매 선택 복원(PURCHASE-RESTORE, Q-07)만 — 구매 선택은 인물 ID 와 최소 선택값만(생년정보 원문 금지), 마지막 변경 후 24시간, 읽을 때 만료 검증, 구매 성공 · 로그아웃 시 삭제, 저장한 가격 · 잔액은 표시 근거로 쓰지 않는다. 구매 의도 키(PURCHASE-KEY-STORE, 2026-10-10 결정 · 2026-10-12 구현): `Idempotency-Key` 와 `quoteId` 도 같은 조건(24시간 · 읽을 때 만료 검증 · 구매 성공 · 로그아웃 삭제 · 생년정보 · 가격 · 잔액 금지)으로 `sessionStorage` 에 둔다 — 지금은 구매 선택 복원 **v5** (DIRECT-PAY, `7c02326` · `33d6a83`): `returnPath`(safeReturnTo 를 거친 앞 화면) · `quoteId` · 선택(`productOption` · `questionKey` · `relationType` 포함 — 수능운은 셋 다 null) · `orderKey`(주문 생성 키) · `orderId`(서버 주문 ID) · `fulfillmentKey`(결제 뒤 생성 키). 금액 · 결제 키 · 생년정보 · 이름 금지. 「결과 보러 가기」 를 누를 때 · 실패 · 로그아웃 때 지운다. v4 · v3 · v2 는 읽을 때 지운다. 전역 상태는 선물 위저드의 Zustand 하나뿐.
- **구매 결과 · 상태 조회 (Q-38 · PURCHASE-STATUS-UI, 2026-10-12)**: 화면 분기는 서버 `status`, 버튼 · 안내는 `nextAction`(API_SPEC 8장 상태표, 모르는 값은 UNKNOWN). `FULFILLED` + `readingId` 일 때만 결과로 이동한다. 환급 문장은 `REFUNDED` + `compensationStatus: COMPLETED` 일 때만 — 오류 code · `FAILED` 만으로 단정하지 않는다. `WAIT` 은 처리 중, `RETRY_PURCHASE` 는 다시 시도, 그 밖의 실패는 문의 안내(새 구매를 열지 않는다). `readingId` · `balance` · `charged` 의 null 을 0 · 완료로 바꾸지 않는다. **처리 중 자동 반복 조회는 하지 않는다** — 결과 불명 · 처리 중 · 생성 실패 오류 뒤 `GET /reading-purchases/by-quote/{quoteId}` 를 한 번만 자동으로 부르고, 그 뒤는 사용자가 "결과 확인" 을 누를 때. 기록이 없으면(404 `PURCHASE_NOT_FOUND` → null) 자동 재전송하지 않고, 사용자가 누를 때만 같은 키 · 같은 견적으로 다시 보낸다. 완료(`FULFILLED` + `readingId`)면 팝업 안에서 「준비가 끝났어요!」 · 「결과 보러 가기」 를 보이고, 결과 이동(`onPurchased`)은 그 버튼을 누를 때 한 번만(`b3d2051`, 기능 명세서 v0.4 8장). 처리 중 · 완료 블록에서는 팝업을 닫지 못한다(Q-40 해결). `failureCode` 는 검사 · 보관하지 않는다(`TODO(Q-38 c)`).
- **사업자 정보**(상호 · 대표자 · 사업자등록번호 · 주소 · 유선번호 · 통신판매업 신고번호 · 전자우편주소 · 호스팅서비스 제공자)는 `src/lib/business.ts` 한 파일에만 둔다. 값은 사용자가 준 것만 넣는다. 통신판매업은 신고하지 않고 「면제 (직전연도 통신판매 거래 50회 미만)」 표기(2026-10-16 사용자 결정, R-07). 호스팅서비스 제공자는 AWS Amplify 로 옮긴 뒤 법적 상호를 받아 넣는다(Q-44 · R-11).
- **정책 원고 (POLICY-MD, `7fb0658`)**: `/terms` · `/privacy` · `/refund` 는 빌드 때 `src/content/policies/{terms,privacy,refund}.md` 를 `src/lib/policy/load.ts` · `parse.ts` 로 읽는다. 원고 파일은 문서다 — Claude Code 는 내용을 쓰지 않고, 웹 대화 완성본을 DOCS-PLACE 로 배치만 한다. 파서는 정해진 문법만 받고 `[ ]` · `○` · 링크 · HTML · 인용 · 코드가 있으면 던진다 — 빌드가 실패하면 원고를 고치게 보고하고, 파서를 느슨하게 바꾸지 않는다. 지금 원고: 이용약관 · 환불정책 v4(PD 10/15, 시행 10/31 — `362d99d`). 개인정보처리방침은 국외 이전 표 빈칸으로 아직 없다(Q-44). 결제하기 팝업 · `/about` 의 안내 문장은 환불정책 v4 문장 그대로 `src/lib/payment/directNotice.ts` 에 — 바꿀 때는 그 파일 한 곳.
- **오류 화면**: 화면 오류는 `src/app/error.tsx`(AppShell 유지 + Sentry), 없는 주소 · 잘못된 토큰은 not-found. `error.message` · `digest` 를 화면에 노출하지 않는다.
- **관측**: PostHog 이벤트 이름은 O-06 목록만. 인적정보 · 휴대전화번호 · 메시지 · 토큰 · signed URL · 결과 본문 · 결제 키를 Sentry · PostHog 에 보내지 않는다.
- **고지**: 결제 버튼 위와 결과 하단에 재미 · 참고용 콘텐츠 고지, 시간 미상 결과에 해석 제한 고지 (F-08). 문구는 PD.
- **비주얼 (STYLE-HAND)**: 손그림체. 카드 · 버튼 · 칩 · 입력창 테두리는 손그림 SVG 프레임(`frame-*.svg`)을 CSS `border-image`(9-slice)로 재사용한다. 도트(픽셀) 처리(`image-rendering: pixelated` 등)는 쓰지 않는다. 적용은 PG 심사 요청 후 (PG-FIRST).
- 모든 화면은 iOS Safari · Android Chrome · **카카오톡 인앱브라우저** 에서 확인한다. 확인 결과는 사용자가 말로 알려 준 것으로 받는다 (스크린샷 필수 아님, NO-SCREENSHOT).

## 금지

- PG 키, OAuth 시크릿 등 비밀값 커밋 금지. `.env*` 는 읽기 · 출력 · 커밋하지 않는다.
- 사용자에게 보이는 문구를 지어 넣지 않는다. 기능 명세서 v0.4(`main`, PD 확정)의 문구는 그대로 쓴다(SPEC-V04). 불가피한 자리표시는 지시문이 지정한 문자열에 `TODO(PD 문구)` 를 붙인다. JSX 주석은 요소 바로 위 줄에 둔다.
- "합격 보장" 같은 단정 · 불안 조장 표현 금지 (D-05).
- PD 에셋(`frame-*.svg`, 폰트, 부적 틀)과 캐릭터 원본 수정 금지.
- 테스트 픽스처의 날짜 · 가격 · 생년월일 · 토큰에는 "픽스처일 뿐이며 실제 가격 · 규칙과 무관하다" 주석을 단다.
- 10/28 18:00 코드 프리즈 이후 기능 추가 금지 (버그 수정 · 문구 수정만). 11/16 ~ 11/19 기능 변경 금지.

## 기술 스택

### 프론트엔드 — 확정 (상세 · 이유는 `docs/FRONTEND.md` 1장)

- Next.js (App Router) + TypeScript, pnpm, AWS Amplify 배포 (`https://ppugi-saju.kr`, 2026-10-16 — Vercel 미리보기는 정리 전까지 병행, HOSTING-AMPLIFY)
- 스타일: Tailwind CSS + CSS 변수 토큰 (토큰 적용은 PG 심사 요청 후)
- 폰트: **보류 (FONT-HOLD)** — 결정 전까지 코드에서 글꼴을 지정하지 않는다. 결정되면 `next/font/local` 서브셋으로 셀프호스팅
- 오버레이: vaul(바텀시트), Radix Dialog(모달) — shadcn/ui 사용 안 함
- 데이터: TanStack Query, API 타입은 openapi-typescript 로 생성 (백엔드 타입 수동 작성 금지, 생성 파일 손수정 금지). adapter 는 openapi-fetch 대신 `src/lib/api/http.ts` 로 요청하고 응답을 zod 로 검사한다 (ADAPTER-HTTP, `docs/FRONTEND.md` 1장 표). OpenAPI 전에는 포트 + 가짜 구현 (MOCK-PORT, 새 라이브러리 없음 — MSW 쓰지 않음)
- 결제: 토스페이먼츠 SDK v2 `@tosspayments/tosspayments-sdk` 2.9.0 (TOSS-SDK 승인, 사용자가 설치 완료 — 10/11)
- 폼: React Hook Form + Zod
- 클라이언트 상태: Zustand 는 선물 위저드 동안만 (`sessionStorage` persist). 그 외 전역 상태 추가 금지
- 날짜: dayjs + timezone, 항상 `Asia/Seoul`
- 연출: CSS keyframes + 스프라이트 (Lottie · Motion 출시 전 미도입)
- OG · 공유: `generateMetadata`(+ 필요 시 `next/og`), Kakao JS SDK 카카오톡 공유하기
- 품질: Biome, Vitest(날짜 경계 · 결제 흐름 분기 · 오류 code 처리), Playwright(모바일 뷰포트, 충전 · 수능운 · 선물 구매 · 선물 수신)
- 관측: Sentry(오류), PostHog(페이지뷰 + O-06 이벤트). 화면 녹화 · 자동 수집 끔, URL 은 `src/lib/observability/maskUrl.ts` 로 가려서 보낸다

렌더링: 기본은 클라이언트 컴포넌트. 서버 렌더링은 `/g/[token]`(OG), 심사용 · 약관 페이지, 홈 첫 화면에만.
새 라이브러리는 위 목록에 없으면 추가하지 않고 멈춰서 보고한다. 라이브러리 설치(`pnpm add` · `pnpm install`)는 사용자가 직접 한다.

### 백엔드 — 확정 (팀 문서)

Spring Boot 3 + PostgreSQL, 배포(local · staging · production 분리)는 AWS Elastic Beanstalk Java SE(PR #56 `backend/docs/AWS_PRELAUNCH_DEPLOYMENT.md`, 주소 `https://api.ppugi-saju.kr` 예정 — R-11), API prefix `/api/v1`, 카카오 OAuth + 서버 세션 쿠키, 토스페이먼츠, Cloudflare R2, OpenAPI 는 springdoc 생성 (I-01 · I-02 · I-03 · I-04, `backend/README.md`).
골격은 `main` 에 있다(`a605afa`, 10/3) — 업무 API 없음, springdoc 은 `local` 프로필에서만 공개 (FE 가 받을 경로는 Q-18). → OpenAPI 파일은 `docs/openapi/api-v1.json` (BE-A PR #17, 10/9 · BE-B PR #23 재생성 `75350c6`, 10/12). 10/13 FE 가 백엔드 `exportOpenApi` 로 다시 만들었다(`78c8b10`, BE-A 위임 — `backend/docs/CUSTOMER_CENTER_REFUNDS_20261013.md`): 사용자 환불 POST 제거 · `PARTIALLY_REFUNDED` 추가, 결과가 `backend/docs/openapi/payment-operations-v1.json` 과 같다. 관리자 계약 `admin-operations-v1.json` 은 FE 가 쓰지 않는다 (OPENAPI-FE-SYNC).

## 폴더 구조

```
saju-project/
├─ frontend/          Next.js(App Router) 앱. FE 작업은 전부 여기서.
│  ├─ CLAUDE.md       이 파일 (FE 작업 규칙)
│  ├─ docs/           FE 개인 작업 문서 — FRONTEND · PHASES · PROGRESS · TEAM-QUESTIONS
│  ├─ src/app/        라우트
│  ├─ src/lib/        공용 유틸 (date.ts = Asia/Seoul 고정, screens.ts = 라우트 표)
│  ├─ src/lib/ports/  화면이 부르는 포트 (FE 모델, MOCK-PORT)
│  ├─ src/lib/api/    공통 응답 · 오류 · CSRF · 멱등, adapters/ = OpenAPI 생성 타입 → 포트 모델
│  ├─ src/mocks/      가짜 구현 (배포 단계 local · preview 전용, staging · production 금지 — DEPLOY-ENV)
│  ├─ src/types/      api.d.ts — pnpm api:types 로 생성. 손으로 고치지 않는다
│  └─ e2e/            Playwright
├─ backend/           Spring Boot — BE 담당. backend/docs/ 에 BE 역할 분담
├─ docs/              팀 공용 문서 (GitHub 원본) — 결정 · PRD · 기능 · API · 공통 응답 · ERD
└─ .github/           PR 템플릿, CI
```

## 명령어

전부 `frontend/` 에서 실행한다 (`pnpm -C frontend <script>` 로 루트에서 실행해도 된다). git 명령도 `frontend/` 에서 실행해도 저장소 전체에 적용된다. 루트 기준 경로가 보이게 하려면 `git -C .. -c status.relativePaths=false ...`.

| 명령 | 하는 일 |
|---|---|
| `pnpm install` | 의존성 설치 (버전은 `save-exact` 로 고정) — 사용자가 직접 |
| `pnpm dev` | 개발 서버 (http://localhost:3000) — 사용자가 직접 |
| `pnpm build` | 프로덕션 빌드 |
| `pnpm start` | 빌드 결과 실행 |
| `pnpm lint` | Biome 검사 (린트 + 포맷) |
| `pnpm lint:fix` | Biome 자동 수정 |
| `pnpm typecheck` | `next typegen`(라우트 타입 생성) 후 `tsc --noEmit` |
| `pnpm test` | Vitest 1회 실행 |
| `pnpm test:watch` | Vitest watch |
| `pnpm e2e` | Playwright (모바일 뷰포트 2종) — 전체 실행은 사용자가 직접. 처음이면 `pnpm e2e:install` |
| `pnpm api:types` | `docs/openapi/api-v1.json`(저장소 루트, BE 가 갱신 — BE 가 FE 에 넘긴 경우 FE 가 `backend/` 에서 `./gradlew --no-daemon exportOpenApi` 로 만들어 같은 커밋에, OPENAPI-FE-SYNC) → `src/types/api.d.ts` 타입 생성. 생성 파일은 손으로 고치지 않는다 — OpenAPI 파일이 바뀌면 다시 실행 (동기화 테스트가 실패로 알려 준다) |

- 환경 변수: `frontend/.env.example` 을 복사해 `frontend/.env.local` 로 쓴다 (사용자가 직접 작성 — Claude Code 는 `.env*` 를 읽거나 쓰지 않는다). `API_PROXY_TARGET` 에 백엔드 주소, `NEXT_PUBLIC_API_MODE`(mock · real). 가짜 모드: `NEXT_PUBLIC_MOCK_SESSION_SCENARIO` · `NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO` · `NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO` · `NEXT_PUBLIC_MOCK_PAYMENT_LAUNCHER`(값 목록은 `docs/PROGRESS.md`). 토스 클라이언트 키 `NEXT_PUBLIC_TOSS_CLIENT_KEY` — 키 원문은 문서 · Git · 스크린샷에 넣지 않는다. 바꾸면 `pnpm dev` 재시작.
- 배포: **AWS Amplify** (앱 `d3iqzbdae9iq50`, 저장소 루트 `amplify.yml` — appRoot `frontend` · Node 22 · pnpm 10.33.0, HOSTING-AMPLIFY). 기준 주소 `https://ppugi-saju.kr`(`www` → 301, SITE-DOMAIN). `main` → 운영(`NEXT_PUBLIC_DEPLOY_ENV=production` · `NEXT_PUBLIC_API_MODE=real`), `boyeon` → 미리보기 `https://boyeon.d3iqzbdae9iq50.amplifyapp.com`(`preview` · `mock`, 가짜 조작판). **배포 단계는 `NEXT_PUBLIC_DEPLOY_ENV`(local · preview · staging · production) 하나로 정한다 (DEPLOY-ENV, `src/lib/deployEnv.ts`)** — 비면 Vercel 의 `VERCEL_ENV`, Amplify 빌드(`AWS_APP_ID`)에서 비면 빌드 실패, staging · production 에서 mock 이면 빌드 · 실행 실패, local 이 아니면 Sentry/PostHog 키 3개가 비면 빌드 실패. 배포 환경 판단에 Vercel 변수를 새로 쓰지 않는다. 브라우저는 `/api` 를 Amplify 서버의 rewrites 로만 부른다 — `API_PROXY_TARGET` 은 빌드 때 굳으므로 바꾸면 다시 빌드. Amplify 환경 변수 · 도메인 · 카카오 등록은 사용자가 UI 에서 한다(키 값은 문서 · 대화에 옮기지 않는다). Vercel 은 정리 전까지 병행. 실기기 확인은 미리보기 브랜치 주소, PG 심사 대상 확인은 운영 도메인.

## 브랜치 · PR

- `main` 은 직접 푸시하지 않는다. 프론트엔드 작업은 전부 `boyeon` 브랜치에서 하고, `main` 에는 `boyeon` 에서 PR 을 연다 (BRANCH). PR 은 사용자가 세션 마무리 문서까지 `boyeon` 에 커밋한 뒤 연다 (DOCS-BEFORE-PR, 2026-10-16).
- PR 은 `.github/pull_request_template.md` 체크리스트를 채운다. 결제 · 공유가 걸린 PR 에는 카카오톡 인앱브라우저 확인 결과를 적는다 (스크린샷은 필수 아님, NO-SCREENSHOT).
- CI(`.github/workflows/ci.yml`)가 `pnpm lint` · `typecheck` · `test` · `build` 를 돌린다. 초록불이 아니면 머지하지 않는다.
