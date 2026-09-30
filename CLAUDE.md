# CLAUDE.md

수능 수험생 대상 사주 + 부적 모바일 웹서비스 **뿌기사주** (D-11 확정, 2026-09-26).
창업 경진대회 출전작 — **2026-10-31 출시, 11/21까지의 실제 수익으로 평가.** 수능은 11/19.

## 세션 규칙 (반드시 지킬 것)

1. **세션 시작 시** `docs/PHASES.md`와 `docs/PROGRESS.md`를 먼저 읽는다. 현재 Phase와 다음 작업을 확인한 뒤 시작한다.
2. 작업은 현재 Phase의 체크박스 단위로 한다. 다른 Phase 작업이 필요하면 먼저 사용자에게 말한다.
3. **세션 종료 시** 문서를 직접 고치지 않고, 끝낸 체크박스 · 내려진 결정 · 막힌 점을 보고한다. `docs/PROGRESS.md`("현재 상태" 덮어쓰기, "세션 로그" 맨 위 한 줄)와 `docs/PHASES.md`(끝낸 체크박스 `[x]`, 7장 상태 열)는 웹 대화에서 작성한 완성본으로 교체된다 (아래 "문서 변경 규칙").
4. 결정이 내려지면 `docs/PHASES.md` 7장의 상태 열과 `docs/PROGRESS.md` 결정 로그에 함께 적힌다 — 이것도 웹 대화에서 작성한다.
5. "결정 필요" 항목(D-xx)에 걸린 작업은 추측으로 진행하지 않고, 보고하고 멈춘다.

## 문서 변경 규칙 (2026-09-30 결정 DOCS-WEB-ONLY)

- 저장소의 Markdown 문서(`docs/`, `CLAUDE.md`, `README.md`, `frontend/README.md`, `frontend/docs/`, `.github/pull_request_template.md` 등 모든 `*.md`)는 Claude Code가 수정 · 생성 · 삭제하지 않는다.
- 문서 변경은 웹 대화(채팅 Claude)에서 작성한 완성본을 사용자가 교체하는 방식으로만 한다. Claude Code는 교체된 문서의 diff 확인과 커밋만 한다.
- 문서가 코드 · Figma · 다른 문서와 어긋나 보이면 문서를 고치지 말고 보고한다.
- 코드 파일 안의 주석은 문서가 아니다 (코드 수정 규칙을 따른다).

## 팀

| 약칭 | 역할 |
|---|---|
| PD | 기획&디자인 — UI 비주얼, 콘텐츠 문구, 약관, 마케팅 |
| FE | 프론트엔드 1명 — 전 화면 + 캐릭터 일러스트 |
| BE-A | 결제 · 주문 · 선물 · 사업자/PG 행정 |
| BE-B | 인증 · 프로필 · 사주 엔진 · 콘텐츠 조합 · 부적/OG 이미지 |

## Figma 사용 규칙 (2026-09-30 결정 FIGMA-WEB-ONLY)

- **Claude Code는 Figma MCP를 호출하지 않는다.** Figma 조회는 웹 대화(채팅 Claude)에서만 하고, 구현에 쓸 값(크기 · 간격 · 항목 순서 등)은 지시문에 적어서 넘긴다. 지시문에 없는 Figma 값을 추측으로 채우지 않는다.
- 파일 키 `9zEtrqV4SoPbQzuworSNUj`, 와이어프레임 페이지 node `17:2`.
- (웹 대화 쪽 규칙) 먼저 `get_metadata`로 구조만 보고, 구현할 **프레임 하나 단위로만** `get_design_context`를 호출한다. 페이지 전체를 한 번에 읽지 않는다.
- 화면 ↔ 프레임 대응은 `docs/PHASES.md` 1장 표를 기준으로 한다.
- 와이어프레임 위 메모 텍스트(프레임 밖 글상자)는 미결정 사항이다. 구현 기준으로 쓰지 않는다.
- 와이어프레임의 회색 · 아이콘 · 글꼴은 임시값이다. 색은 PD 토큰, 아이콘은 PD 에셋, 글꼴은 FONT-HOLD 결정 후에 정한다.

## 구현 원칙

- 같은 모양의 화면은 공통 컴포넌트 하나로 만든다 (`frontend/docs/FRONTEND.md` 3장). 새 화면을 만들기 전에 재사용할 컴포넌트가 있는지 먼저 확인한다.
- **결제 금액은 서버만 계산한다.** 클라이언트가 보낸 금액을 믿지 않는다. 승인은 멱등하게.
- 사주 계산은 규칙 기반(만세력 라이브러리), 풀이는 템플릿 문구 조합. AI 호출을 추가하지 않는다.
- 같은 사람 · 같은 상품이면 항상 같은 결과가 나와야 한다 (결정적 선택).
- 모든 화면은 iOS Safari · Android Chrome · **카카오톡 인앱브라우저**에서 확인한다.
- 손그림 톤: 캐릭터만 직접 그린 에셋. 카드·버튼·칩·입력창 테두리는 손그림 SVG 프레임(`frame-*.svg`)을 CSS `border-image`(9-slice)로 재사용한다. 박스마다 새로 그리거나 rough.js를 쓰지 않는다 (`frontend/docs/FRONTEND.md` 2장).

## 금지

- PG 키, OAuth 시크릿 등 비밀값 커밋 금지 (`.env*`는 git 제외).
- "합격 보장" 같은 단정적 효과 표현 금지. 결과 화면에는 재미로 보는 콘텐츠라는 고지를 둔다.
- 10/28 18:00 코드 프리즈 이후 기능 추가 금지 (버그 수정·문구 수정만).
- 11/16 ~ 11/19 수능 직전 기간 기능 변경 금지.

## 기술 스택

### 프론트엔드 — 확정 (상세·이유는 `frontend/docs/FRONTEND.md` 1장)

- Next.js (App Router) + TypeScript, pnpm, Vercel 배포
- 스타일: Tailwind CSS + CSS 변수 토큰 (색·오행 5색은 CSS 변수에만 정의하고 Tailwind에서 참조)
- 폰트: **보류 (2026-09-30 FONT-HOLD)** — 본문 · 제목 글꼴 모두 미정. 결정 전까지 코드에서 글꼴을 지정하지 않는다 (브라우저 기본 글꼴). 결정되면 `next/font/local` 서브셋으로 셀프호스팅
- 오버레이: vaul(바텀시트), Radix Dialog(모달) — shadcn/ui 사용 안 함
- 데이터: TanStack Query, API 타입은 openapi-typescript + openapi-fetch로 생성 (수동 타입 작성 금지)
- 폼: React Hook Form + Zod
- 클라이언트 상태: Zustand는 선물 위저드 동안만 (`sessionStorage` persist). 그 외 전역 상태 추가 금지
- 날짜: dayjs + timezone, 항상 `Asia/Seoul` 기준으로 계산
- 연출: CSS keyframes + 스프라이트 (Lottie·Motion 출시 전 미도입)
- OG: `next/og`(ImageResponse) + `generateMetadata`, 공유: Web Share API + Kakao JS SDK
- 품질: Biome, Vitest(D-day·가격 계산), Playwright(모바일 뷰포트, 수능운·선물 구매·선물 수신 3개 흐름)
- 관측: Sentry(오류), PostHog(페이지뷰). 화면 녹화 · 자동 수집 끔, URL 은 `src/lib/observability/maskUrl.ts`로 가려서 보낸다 (선물 토큰 · 결제 쿼리). 새 동적 경로 · API 경로가 생기면 가리기 대상인지 확인한다

렌더링: 기본은 클라이언트 컴포넌트. 서버 렌더링은 `/g/[token]`, 심사용·약관 페이지, 홈 첫 화면에만.
새 라이브러리는 위 목록에 없으면 추가 전에 사용자에게 먼저 묻는다.

### 백엔드 — 미확정

`docs/PHASES.md` 3-2 참고 (D-14에서 확정).

## 폴더 구조

```
saju-project/
├─ frontend/          Next.js(App Router) 앱. 지금 개발은 전부 여기서.
│  ├─ src/app/        라우트
│  ├─ src/lib/        공용 유틸 (date.ts = Asia/Seoul 고정)
│  └─ e2e/            Playwright
├─ backend/           비어 있음. D-14 확정 전까지 코드 넣지 않는다.
├─ docs/              팀 공용 — PHASES.md(계획·결정) · PROGRESS.md(진행 상황)
└─ .github/           PR 템플릿, CI
```

## 명령어

전부 `frontend/`에서 실행한다 (`pnpm -C frontend <script>`로 루트에서 실행해도 된다).

| 명령 | 하는 일 |
|---|---|
| `pnpm install` | 의존성 설치 (버전은 `save-exact`로 고정되어 있다) |
| `pnpm dev` | 개발 서버 (http://localhost:3000) |
| `pnpm build` | 프로덕션 빌드 |
| `pnpm start` | 빌드 결과 실행 |
| `pnpm lint` | Biome 검사 (린트 + 포맷) |
| `pnpm lint:fix` | Biome 자동 수정 |
| `pnpm typecheck` | `next typegen`(라우트 타입 생성) 후 `tsc --noEmit`. `.next/`가 없는 새 checkout·CI에서도 통과하도록 타입 생성을 포함한다 |
| `pnpm test` | Vitest 1회 실행 |
| `pnpm test:watch` | Vitest watch |
| `pnpm e2e` | Playwright (모바일 뷰포트 2종). 처음이면 `pnpm e2e:install`로 브라우저부터 받는다 |
| `pnpm api:types` | `frontend/openapi.json` → `src/types/api.d.ts` 타입 생성. 백엔드 스펙이 나온 뒤에 쓴다 |

- 환경 변수: `frontend/.env.example`을 복사해 `frontend/.env.local`로 쓴다. `.env*`는 커밋하지 않는다.
- 배포: Vercel. `main` → 스테이징, PR → 미리보기 URL. (연결은 아직 안 됨 — Phase 0 남은 작업)

## 브랜치 · PR

- `main`은 직접 푸시하지 않는다. 프론트엔드 코드 작업은 전부 `boyeon` 브랜치에서 하고, `main`에는 `boyeon`에서 PR을 연다.
- PR은 `.github/pull_request_template.md`의 체크리스트를 채운다. 결제·공유가 걸린 PR에는 카카오톡 인앱브라우저 스크린샷을 붙인다.
- CI(`.github/workflows/ci.yml`)가 `pnpm lint` · `typecheck` · `test` · `build`를 돌린다. 초록불이 아니면 머지하지 않는다.
