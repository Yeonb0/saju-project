@AGENTS.md

# CLAUDE.md — frontend

수능 수험생을 핵심 사용자로 시작하는 사주 + 부적 모바일 웹서비스 **뿌기사주**.
창업 경진대회 출전작 — **2026-10-31 출시, 11/21까지의 실제 수익으로 평가.** 수능은 11/19.

> 이 파일은 FE 작업 규칙이다. 이 파일과 `docs/` 의 PHASES · PROGRESS · FRONTEND · TEAM-QUESTIONS 는 **FE 개인 작업 문서이며 팀 합의 문서가 아니다** (DOCS-FE-OWN).
> Claude Code 는 `frontend/` 에서 시작한다 — 루트에는 `CLAUDE.md` 가 없어서, 루트에서 시작하면 이 규칙이 세션 처음에 읽히지 않는다.
> 이 파일과 코드 주석 속 문서 경로(`docs/PHASES.md` 등)는 `frontend/` 기준이다. 저장소 루트의 `docs/` 는 팀 공용 문서다 (아래 "결정 기준").

## 지금 최우선 — PG 심사 트랙 (PG-FIRST)

- PG 심사 요청(10/14 전후) 전까지는 **백엔드 연결과 결제 플로우만** 만든다. 체크박스는 `docs/PHASES.md` "PG 심사 트랙" 의 PG-1 ~ PG-5.
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
- `docs/API_SPEC.md` 는 계약 초안이다. **타입의 출처는 OpenAPI 생성본뿐이다.** 스펙에 없는 타입 · 응답 모양을 지어내지 않는다.
- 팀 답은 `docs/TEAM-QUESTIONS.md` 에 상태로 적혀 있다 (TQ-ANSWERS). `해결` 은 따른다. `답변 · 반영 대기` · `답 엇갈림` 항목은 팀 문서에 반영되기 전까지 그 답에 기대는 코드를 만들지 않는다 (예: `/wallet` 라우트는 `FUNCTIONAL_SPEC.md` 2장에 들어간 뒤).

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
| BE-A | 돈 · 주문 · 선물 전달 — 공통 응답 · 멱등성, 상품 · 견적, 등껍질 원장, 토스 결제 · 환불, 선물 · 알림톡, 관리자 |
| BE-B | 사용자 · 계산 · 결과 · 미디어 — 카카오 인증 · 세션 · CSRF, 인물, 만세력 · Liner 해석 문장, 결과, 부적 · R2, 카카오톡 공유 리소스 |

## Figma 사용 규칙 (FIGMA-WEB-ONLY)

- **Claude Code 는 Figma MCP 를 호출하지 않는다.** Figma 조회는 웹 대화에서만 하고, 구현에 쓸 값은 지시문에 적어서 넘긴다. 지시문에 없는 Figma 값을 추측으로 채우지 않는다.
- 파일 키 `9zEtrqV4SoPbQzuworSNUj`, 와이어프레임 페이지 node `17:2`. 화면 ↔ 프레임 대응은 `docs/PHASES.md` 1장 표.
- 와이어프레임 위 메모 텍스트(프레임 밖 글상자)는 미결정 사항이다. 와이어의 회색 · 아이콘 · 글꼴은 임시값이다.

## 구현 원칙

- **재사용**: 새 화면 전에 `docs/FRONTEND.md` 3장 공통 컴포넌트를 먼저 확인한다 (`src/components`: AppShell · Button · Card · Chip · FormField · BottomSheet · Modal · LoadingScene). 결과 화면은 **ReadingViewer 하나** 를 API 섹션 타입별로 수능 · 일반 5종 · 선물 수신에 재사용한다 (VIEWER).
- **라우트**: 새 페이지는 `src/lib/screens.ts` ROUTES 표에 먼저 있어야 한다 (`screens.test` 가 표 밖 `page.tsx` 를 막는다). 라우트 표의 출처는 `docs/FUNCTIONAL_SPEC.md` 2장. 새 동적 경로 · API 경로가 생기면 `src/lib/observability/maskUrl.ts` 가리기 대상인지 확인한다.
- **API 호출**: `/api/*` 는 `next.config.ts` rewrites 로 백엔드에 프록시한다(같은 출처, I-02). 백엔드 prefix 는 `/api/v1`. 성공 응답은 `{ data, traceId }` 로 감싸서 오고, 오류는 `{ code, message, traceId, fieldErrors, details? }` 다 (`docs/COMMON_RESPONSE_AND_ERROR_CODES.md`). 분기는 `code` 로 하고 `message` 문자열을 비교하지 않는다. 알 수 없는 `code` 는 HTTP status 로 처리한다 (같은 문서 7장). 새 enum 값은 unknown 분기를 둔다.
- **금액은 서버만**: 원화 금액 · 등껍질 차감량 · 구매 후 잔액은 서버 견적 · 주문 · 구매 응답 값만 표시한다. 클라이언트에서 계산 · 합산 · 하드코딩하지 않는다 (P-03, P-09).
- **원화 결제 (토스페이먼츠, P-08)**: 서버가 만든 주문(`orderId` · `amount`)으로만 결제창을 연다. PG 복귀 쿼리의 `paymentKey` · `orderId` · `amount` 는 그대로 서버 승인 요청에 넘기기만 한다. `/pay/success` 는 서버 승인 응답을 받은 뒤에만 성공을 표시한다.
- **충전 완료 (TOPUP-DONE)**: 충전 주문이 `CREDITED` 일 때만 완료와 잔액(서버 값)을 표시한다. `PAID` · `processing: true` 는 처리 중이다. 승인 결과가 불명확하면 주문 조회(`GET /top-up-orders/{orderId}`)를 2초 간격 최대 30초 하고, 그래도 미확정이면 확인 중 안내 + 주문 확인 버튼을 보인다. 지연을 실패로 표시하거나 새 결제로 유도하지 않는다. `processing: false` 만으로 성공 판단하지 않는다.
- **멱등성 (I-05)**: `Idempotency-Key` 는 구매 의도 하나에 하나만 만들고, 재시도 · 새로고침 · PG 복귀 · CSRF 재시도에도 같은 키 · 같은 본문을 쓴다. 결과가 불명확하다는 이유로 새 키를 만들지 않는다. mutation 자동 재시도는 0 (`makeQueryClient`). `409 IDEMPOTENCY_REQUEST_PROCESSING` 이면 버튼을 다시 열지 않고 상태를 조회한다.
- **CSRF (Q-02 해결)**: `GET /session` 응답의 `csrfToken` 을 변경 요청의 `X-CSRF-Token` 헤더로 보낸다. `403 CSRF_FAILED` 면 `GET /session` 을 한 번 불러 새 토큰으로 원래 요청을 **1회만** 재시도하고, 그래도 실패하면 로그인 만료 · 보안 오류로 처리한다. 필드 이름은 OpenAPI 생성본으로 확인한다.
- **사주 결과**: 만세력 계산과 해석 문장 생성(Liner)은 서버가 한다 (S-06). FE 는 AI · 외부 생성 호출을 추가하지 않고, 서버 결과 스냅샷만 표시한다.
- **날짜**: `src/lib/date.ts` (Asia/Seoul) 를 거친다. 기기 시간대를 쓰지 않는다. 수능운 판매 마감은 전날 23:59 KST (F-07).
- **선물**: 결제 재화는 P-07(원화) 확정이지만 BE-A 가 재검토 중이다(Q-16) — 원화 / 등껍질 결제 경로를 정하지 않고, 가격 · 단위는 서버 `price.currency` · `price.amount` 만 쓴다. 재발송 버튼은 서버의 `delivery.canResend` 만 따른다 (횟수 · 시간을 FE 가 계산하지 않는다). 선물 링크 OG 에는 수신자 · 보낸 사람 이름을 넣을 수 있다(Q-13), 생년정보 · 메시지는 넣지 않는다. 수신자에게는 서버가 알림톡으로 링크를 보낸다(G-10) — FE 가 선물 링크를 만들거나 공유로 전달하지 않는다. 수신자 휴대전화번호는 입력 칸 외에는 서버가 준 마스킹 값만 표시하고, 로그 · Sentry · PostHog 에 넣지 않는다. 선물 메시지는 텍스트로만 렌더하고(`dangerouslySetInnerHTML` 금지) OG · PostHog · Sentry 에 넣지 않는다 (G-08).
- **공유**: 개인 결과 · 부적은 카카오톡 공유하기(Kakao JS SDK)로, 서버가 만든 비식별 share 리소스만 쓴다 (G-11). 공유 범위는 G-11 미정.
- **브라우저 저장소**: `localStorage` 는 오늘의 운세 생년정보(`expiresAt` 30일, X-01)와 수능 준비물 체크 상태만. `sessionStorage` 는 선물 위저드의 Zustand persist 와 구매 선택 복원(PURCHASE-RESTORE, Q-07)만 — 구매 선택은 인물 ID 와 최소 선택값만(생년정보 원문 금지), 마지막 변경 후 24시간, 읽을 때 만료 검증, 구매 성공 · 로그아웃 시 삭제, 저장한 가격 · 잔액은 표시 근거로 쓰지 않는다. 전역 상태는 선물 위저드의 Zustand 하나뿐.
- **사업자 정보**(상호 · 대표자 · 사업자등록번호 · 주소 · 유선번호 · 통신판매업 신고번호 · 전자우편주소 · 호스팅서비스 제공자)는 `src/lib/business.ts` 한 파일에만 둔다. 값은 사용자가 준 것만 넣는다.
- **오류 화면**: 화면 오류는 `src/app/error.tsx`(AppShell 유지 + Sentry), 없는 주소 · 잘못된 토큰은 not-found. `error.message` · `digest` 를 화면에 노출하지 않는다.
- **관측**: PostHog 이벤트 이름은 O-06 목록만. 인적정보 · 휴대전화번호 · 메시지 · 토큰 · signed URL · 결과 본문 · 결제 키를 Sentry · PostHog 에 보내지 않는다.
- **고지**: 결제 버튼 위와 결과 하단에 재미 · 참고용 콘텐츠 고지, 시간 미상 결과에 해석 제한 고지 (F-08). 문구는 PD.
- **비주얼 (STYLE-HAND)**: 손그림체. 카드 · 버튼 · 칩 · 입력창 테두리는 손그림 SVG 프레임(`frame-*.svg`)을 CSS `border-image`(9-slice)로 재사용한다. 도트(픽셀) 처리(`image-rendering: pixelated` 등)는 쓰지 않는다. 적용은 PG 심사 요청 후 (PG-FIRST).
- 모든 화면은 iOS Safari · Android Chrome · **카카오톡 인앱브라우저** 에서 확인한다. 확인 결과는 사용자가 말로 알려 준 것으로 받는다 (스크린샷 필수 아님, NO-SCREENSHOT).

## 금지

- PG 키, OAuth 시크릿 등 비밀값 커밋 금지. `.env*` 는 읽기 · 출력 · 커밋하지 않는다.
- 사용자에게 보이는 문구를 지어 넣지 않는다. 불가피한 자리표시는 지시문이 지정한 문자열에 `TODO(PD 문구)` 를 붙인다. JSX 주석은 요소 바로 위 줄에 둔다.
- "합격 보장" 같은 단정 · 불안 조장 표현 금지 (D-05).
- PD 에셋(`frame-*.svg`, 폰트, 부적 틀)과 캐릭터 원본 수정 금지.
- 테스트 픽스처의 날짜 · 가격 · 생년월일 · 토큰에는 "픽스처일 뿐이며 실제 가격 · 규칙과 무관하다" 주석을 단다.
- 10/28 18:00 코드 프리즈 이후 기능 추가 금지 (버그 수정 · 문구 수정만). 11/16 ~ 11/19 기능 변경 금지.

## 기술 스택

### 프론트엔드 — 확정 (상세 · 이유는 `docs/FRONTEND.md` 1장)

- Next.js (App Router) + TypeScript, pnpm, Vercel 배포
- 스타일: Tailwind CSS + CSS 변수 토큰 (토큰 적용은 PG 심사 요청 후)
- 폰트: **보류 (FONT-HOLD)** — 결정 전까지 코드에서 글꼴을 지정하지 않는다. 결정되면 `next/font/local` 서브셋으로 셀프호스팅
- 오버레이: vaul(바텀시트), Radix Dialog(모달) — shadcn/ui 사용 안 함
- 데이터: TanStack Query, API 타입은 openapi-typescript + openapi-fetch 로 생성 (수동 타입 작성 금지, 생성 파일 손수정 금지)
- 결제: 토스페이먼츠 SDK v2 `@tosspayments/tosspayments-sdk` (TOSS-SDK 승인, 설치는 PG-3 에서 사용자가 직접)
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

Spring Boot 3 + PostgreSQL, Railway 배포(local · staging · production 분리), API prefix `/api/v1`, 카카오 OAuth + 서버 세션 쿠키, 토스페이먼츠, Cloudflare R2, OpenAPI 는 springdoc 생성 (I-01 · I-02 · I-03 · I-04, `backend/README.md`).
골격은 `main` 에 있다(`a605afa`, 10/3) — 업무 API 없음, springdoc 은 `local` 프로필에서만 공개 (FE 가 받을 경로는 Q-18).

## 폴더 구조

```
saju-project/
├─ frontend/          Next.js(App Router) 앱. FE 작업은 전부 여기서.
│  ├─ CLAUDE.md       이 파일 (FE 작업 규칙)
│  ├─ docs/           FE 개인 작업 문서 — FRONTEND · PHASES · PROGRESS · TEAM-QUESTIONS
│  ├─ src/app/        라우트
│  ├─ src/lib/        공용 유틸 (date.ts = Asia/Seoul 고정, screens.ts = 라우트 표)
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
| `pnpm api:types` | `frontend/openapi.json` → `src/types/api.d.ts` 타입 생성 (BE OpenAPI 스펙 수령 후) |

- 환경 변수: `frontend/.env.example` 을 복사해 `frontend/.env.local` 로 쓴다 (사용자가 직접 작성). `API_PROXY_TARGET` 에 백엔드 주소.
- 배포: Vercel. `main` → 스테이징, PR → 미리보기 URL. 실기기 확인은 미리보기의 브랜치 주소, PG 심사 대상 확인은 운영 도메인.

## 브랜치 · PR

- `main` 은 직접 푸시하지 않는다. 프론트엔드 작업은 전부 `boyeon` 브랜치에서 하고, `main` 에는 `boyeon` 에서 PR 을 연다 (BRANCH).
- PR 은 `.github/pull_request_template.md` 체크리스트를 채운다. 결제 · 공유가 걸린 PR 에는 카카오톡 인앱브라우저 확인 결과를 적는다 (스크린샷은 필수 아님, NO-SCREENSHOT).
- CI(`.github/workflows/ci.yml`)가 `pnpm lint` · `typecheck` · `test` · `build` 를 돌린다. 초록불이 아니면 머지하지 않는다.
