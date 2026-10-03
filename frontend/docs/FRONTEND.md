# 프론트엔드 개발 문서 — 뿌기사주

> FE 전용 문서. 일정 · 화면 목록 · Phase 체크박스는 같은 폴더의 [`PHASES.md`](PHASES.md), 진행 상황은 [`PROGRESS.md`](PROGRESS.md), 팀에 물은 것은 [`TEAM-QUESTIONS.md`](TEAM-QUESTIONS.md)에 있다 (FE 개인 작업 문서, DOCS-FE-OWN).
> 결정 기준은 GitHub 의 팀 문서 [`docs/PENDING_DECISIONS.md`](../../docs/PENDING_DECISIONS.md) (DOCS-GITHUB). API 모양은 [`docs/API_SPEC.md`](../../docs/API_SPEC.md) · [`docs/COMMON_RESPONSE_AND_ERROR_CODES.md`](../../docs/COMMON_RESPONSE_AND_ERROR_CODES.md) 를 참고하되, 타입은 OpenAPI 생성본만 쓴다.
> 이 문서는 원래 `PHASES.md` 의 3-1 · 4 · 5장이었고, 2026-09-26 에 여기로 옮겼다. PHASES.md 의 "4장" · "5장" 표현은 이 문서의 2장 · 3장을 가리킨다.
> 변경: 2026-10-03 — 공용 문서(PR #5) 반영: 토스 SDK(TOSS-SDK), 공통 응답 · 오류 처리, 카카오톡 공유하기만, 결과 뷰어 ReadingViewer 하나(VIEWER), 선물 단건 · 알림톡, Checkout 두 종류 · 사업자 정보 푸터. 손그림체 재확인(STYLE-HAND), 디자인 항목은 PG 심사 요청 후(PG-FIRST)

---

## 1. 프론트엔드 스택 — ✅ 확정 (2026-09-26, 10-03 보완)

판단 기준: FE 1명이 5주에 화면 40개 · 카카오톡 인앱브라우저에서 결제 · 공유 동작 · 선물 링크 OG · 손그림 톤.

| 영역 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | **Next.js (App Router) + TypeScript** | 선물 링크 `/g/[token]` OG 는 서버 렌더링이 필요. `/api` 를 rewrites 로 백엔드(`/api/v1`)에 프록시해 같은 출처 쿠키 · CORS 문제 제거 (I-02) |
| 배포 | **Vercel** | PR 마다 미리보기 URL → 폰으로 바로 확인 |
| 패키지 매니저 | **pnpm** | |
| 스타일 | **Tailwind CSS + CSS 변수 토큰** | 색 · 오행 5색은 CSS 변수에만 정의하고 Tailwind 에서 참조 (D-01 값 있음, 적용은 PG 심사 요청 후) |
| 손그림 테두리 | **손그림 SVG 프레임 + CSS `border-image`(9-slice)** | 카드 · 버튼 · 칩 · 입력창 프레임 3~4장으로 모든 박스 재사용 (D-03 · D-05). rough.js 미채택, 도트(픽셀) 처리 안 함 (STYLE-HAND) |
| 폰트 | **보류 (FONT-HOLD, D-02 미정)** | 결정 전까지 코드에서 글꼴을 지정하지 않는다. 결정되면 `next/font/local` 서브셋 셀프호스팅 |
| 오버레이 UI | **vaul**(바텀시트) + **Radix Dialog**(모달) | headless 라 손그림 스타일 입히기 쉬움. shadcn/ui 미채택 |
| 서버 데이터 | **TanStack Query** | mutation 자동 재시도 0 (`makeQueryClient`) — 결제 · 구매 명령 중복 방지 |
| API 타입 | **openapi-typescript + openapi-fetch** | BE springdoc OpenAPI 에서 생성 (API_SPEC 15장). 명세 변경이 컴파일 에러로 드러남 |
| 결제 | **토스페이먼츠 SDK v2** `@tosspayments/tosspayments-sdk` (2026-10-01 TOSS-SDK) | P-08 토스 기준. 결제창 열기만 FE, 금액 · 승인은 서버. 설치는 PG-3 에서 |
| 폼 | **React Hook Form + Zod** | PersonForm 하나를 여러 곳에서 재사용, "시간 모름" 조건부 검증 (A-04 · A-05) |
| 클라이언트 상태 | **Zustand** (선물 위저드 한정, `sessionStorage` persist) | 결제창 리다이렉트 후 복귀해도 선물 입력값 유지 |
| 날짜 | **dayjs + timezone(Asia/Seoul)** | D-day · 수능 판매 마감(F-07)이 기기 시간대에 흔들리지 않게 |
| 연출 | **CSS keyframes + 손그림 스프라이트** | 등껍질 · 분석 중은 2~4프레임. Lottie · Motion 은 출시 전 미도입 |
| OG · 공유 | **`generateMetadata`**(+ 필요 시 `next/og`), **Kakao JS SDK 카카오톡 공유하기** | 개인 결과 · 부적 공유는 서버가 만든 비식별 share 리소스만 사용 (G-11). Web Share API 는 팀 문서에 없어 쓰지 않는다. 선물 전달은 서버 알림톡 (G-10) |
| 품질 | **Biome**, **Vitest**(날짜 경계 · 결제 흐름 분기 · 오류 code 처리), **Playwright**(모바일 뷰포트: 충전 · 수능운 · 선물 구매 · 선물 수신) | 돈이 걸린 흐름에만 E2E |
| 관측 | **Sentry**(오류) + **PostHog**(페이지뷰 + O-06 이벤트) | URL 은 `maskUrl` 로 가림 (OBS-PRIVACY) |

- 버전: 설치 시점 최신 안정 판을 `package.json` 에 고정.
- 렌더링 원칙: 기본은 클라이언트 컴포넌트. 서버 렌더링은 `/g/[token]`(OG 메타데이터), 심사용 · 약관 페이지, 홈 첫 화면에만.
- 뷰포트: `100dvh` + safe-area, 앱 기둥 최대 402px 가운데 정렬 (`FUNCTIONAL_SPEC.md` 1장, `src/lib/layout.ts` COLUMN_WIDTH). 결제 · 공유가 걸린 PR 에는 카카오톡 인앱브라우저 스크린샷 첨부.

### 1-1. API 연결 규칙 (COMMON_RESPONSE_AND_ERROR_CODES)

- 요청은 같은 출처 `/api/v1/...` → `next.config.ts` rewrites → 백엔드(`API_PROXY_TARGET`).
- 성공: `{ data, traceId }`. `204` 는 본문 없음. 목록은 `data.items` + `data.nextCursor`.
- 오류: `{ code, message, traceId, fieldErrors, details? }`. 분기는 `code` 로만. 알 수 없는 `code` 는 HTTP status 로 처리.
- 프론트 처리 계약 (같은 문서 7장): `401` → 로그인 후 원 경로 복귀, `403 CSRF_FAILED` → 갱신 후 1회 재시도(방식 Q-02), `409 INSUFFICIENT_BALANCE` → 부족분 + 추천 충전 상품 모달, `409 IDEMPOTENCY_REQUEST_PROCESSING` → 버튼 잠금 유지 + 상태 조회, `410 GIFT_*` → 상세 없이 만료 · 폐기 화면, `422` → 해당 입력 · 상품 화면으로, `429` → `Retry-After` 동안 CTA 비활성, `5xx` → 일반 오류 안내 + `traceId` + 안전한 재시도.
- 멱등 명령(충전 주문 · 승인 · 운세 구매 · 부적 추가 구매 · 선물 주문 · 환불)은 `Idempotency-Key` 필수. 구매 의도 하나에 키 하나, 재시도에도 같은 키 (I-05).
- 화면에 `traceId` 는 보여도 되지만 `error.message` · `digest` · 서버 원문은 보이지 않는다.

---

## 2. 손그림 톤 구현 방침 — **보류 (PG-FIRST, PG 심사 요청 후 재개)**

앱 전체를 손그림체로 간다 (STYLE-HAND, 팀 문서 D-01 · D-05 반영 2026-10-03). **FE 가 그림을 다 그리지 않아도 손그림처럼 보이게** 만드는 게 원칙.

| 요소 | 방법 | 담당 |
|---|---|---|
| 캐릭터 (뿌기 거북이, 12지 부적 동물) | 직접 그림. 레이어 분리 벡터 우선. 서버 합성 입력 형식(SVG / PNG)은 Q-14 | FE(캐릭터) |
| 카드 · 버튼 · 칩 · 입력창 테두리 | 손그림 SVG 프레임 3~4장을 CSS `border-image`(9-slice)로 모든 박스에 재사용. `border-image` 는 `border-radius` 를 테두리에 적용하지 않으므로 모서리 모양은 프레임 그림이 정한다 (모서리 · 그림자 토큰의 쓰임 Q-15) | PD 그림 / FE 적용 |
| 부적 틀 · 배경 · 아이콘 | 손그림 에셋 소수 세트로 재사용. 아이콘 24×24 (D-03) | PD |
| 색 | D-01 토큰(배경 `#FFF8E8` · 텍스트 `#2B2622` · 주홍 `#D95745` · 금색 `#C99A3D` · 오행 5색)을 CSS 변수로 | PD 값 / FE 적용 |
| 글꼴 | 보류 (FONT-HOLD, D-02). 결정되면 상업 이용 라이선스 확인 필수, `next/font/local` 서브셋 | PD 선정 / FE 적용 |
| 연출 (등껍질, 분석 중) | 2~4프레임 손그림 스프라이트 + CSS keyframes | FE |

**프레임 SVG 규격 (9-slice용)**: 네 모서리를 정사각형 영역 안에 그리고, 가운데 변은 반복 · 늘림해도 자연스러운 선으로 그린다. 선 두께 · 색은 토큰과 맞춘다. 파일명 `frame-<용도>.svg` (`frame-card` · `frame-button` · `frame-chip` · `frame-input`). 에셋 일정: 1차 10/10 · 확정 10/15 (D-03).

**캐릭터 에셋 규격 (보류 해제 후 문서화)**: 파일 포맷, 캔버스 크기, 레이어 이름 규칙(부적 합성 시 색을 바꿀 레이어 지정), 파일명 규칙. 서버 합성 입력 형식(Q-14)을 받은 뒤 정한다.

**캐릭터 범위**
- 뿌기(거북이): 기본 1, 분석 중 1~2, 등껍질(엎드린) 1, 일어난 1 (`FUNCTIONAL_SPEC.md` 6장 연출)
- 부적 동물: 띠 기준 · **12지 전체 출시** (T-02 · T-03, "기본 몸체 · 포즈 템플릿 + 표정 · 소품 · 부적 조합"). FE 혼자 그리는 양이라 출시 범위를 팀에 확인 중 (R-02). 띠 경계(입춘 / 음력 설)는 T-02 미정.
- 부적 이미지: 원본 1080×1920 PNG, 썸네일 360×640 WebP, 이름 · 생년월일 · 성별 미표시 (T-04). 합성은 BE-B.

---

## 3. 공통 컴포넌트 재사용 맵

| 컴포넌트 · 모듈 | 쓰이는 화면 | 만드는 단계 |
|---|---|---|
| AppShell (헤더: 뒤로/제목/메뉴, 사이드 메뉴, 하단 고정 CTA) | 거의 전부 | Phase 1 (완료) |
| API 클라이언트 (`src/lib/api` — openapi-fetch + 공통 응답 · 오류 code 처리 + Idempotency-Key) | 서버를 부르는 모든 화면 | PG-1 |
| 로그인 가드 (세션 확인 · `returnTo` 복귀) | 로그인 필요 화면 전부 (A-02) | PG-2 |
| Checkout — PG 결제 (서버 주문 요약 · 동의 · 토스 결제창 · `/pay/success` 승인 · `/pay/fail`) | 충전(경로 Q-01), 28 선물 결제 | PG-3 / Phase 5 |
| BusinessFooter (사업자 정보 + 약관 3종 링크, 값은 한 파일) | 전부 (AppShell 하단) | PG-4 |
| PersonForm (이름 · 생년월일 · 양력/음력 · 윤달 · 시간 · 시간 모름 · 성별 · 관계 · 타인 정보 권한 확인) | 2, 3, 31 (+ 오늘의 운세 비로그인 입력, 궁합 상대) | Phase 3 |
| PersonCard (프로필 카드 + 수정 + "저장된 다른 사용자 불러오기") | 8, 15, 37 | Phase 3 |
| Checkout — 등껍질 차감 (서버 견적: 상품 · 대상 · 옵션 · 차감량 · 구매 후 잔액, 단일 구매 명령) + 잔액 부족 모달 (P-06) | 10, 16, 부적 추가 구매 | Phase 4 |
| LoadingScene (캐릭터 + 문구) | 6, 11, 13, 17 | Phase 4 |
| ReadingViewer (API 섹션 타입별: `TEXT` · `PERIOD_GUIDANCE` · `FOOD_RECOMMENDATION` · `CHECKLIST`, 고지, 메시지 · 편지 슬롯) — SuneungResultViewer 대체 (VIEWER) | 12, 18~22, 32~36 | Phase 4 |
| TalismanViewer (부적 이미지 + 설명 + 저장 · 카카오톡 공유하기) | 14, 22, 36, 39 | Phase 4 |
| GiftWizard (Zustand + `sessionStorage`) — 운세 유형 → 옵션 → 수신자 이름 · 전화번호 → 부적 → 편지 → 검토 | 23, 25~28 (24 는 Q-09) | Phase 5 |
| Modal (Radix Dialog) — "부적이 저장되었어요!", 잔액 부족 등 | 40 외 | Phase 1 (껍데기) / 4 |
| BottomSheet (vaul) | 39 (+ 충전 상품 선택이 시트면 Q-01) | Phase 1 (껍데기) / 6 |
