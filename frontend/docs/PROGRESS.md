# PROGRESS

> **FE 개인 작업 문서다. 팀 합의 문서가 아니다** (2026-09-30 DOCS-FE-OWN).
> 모든 세션은 시작할 때 같은 폴더의 `PHASES.md`와 이 파일을 읽고, 끝날 때 이 파일을 갱신한다. 갱신은 웹 대화에서 작성한 완성본으로만 한다 (`frontend/CLAUDE.md` "문서 변경 규칙").
> 갱신할 때는 "현재 상태"를 덮어쓰고, "세션 로그"에는 맨 위에 한 줄씩 추가한다.

## 현재 상태

- **현재 Phase**: Phase 1 — 화면 골격 + PG 심사용 페이지 (9/29 ~ 10/4)
- **마지막으로 끝낸 작업**: 문서 구조 — `PHASES.md` · `PROGRESS.md` 를 `frontend/docs/` 로, 루트 `CLAUDE.md` 를 `frontend/CLAUDE.md` 로 옮기고 FE 개인 문서로 전환(DOCS-FE-OWN). Phase 1 FE — 라우트 오류 · 404 화면(`f6dedfa`, `error.tsx` AppShell 유지 + Sentry · `not-found.tsx`) + `global-error.tsx` `retry` 통일(`a2d6fda`). 그 전: 라우트 26개 자리표시(`e265b25`), AppShell(`22f76cc`), 공통 컴포넌트 껍데기 7종(`7b1b488`), TanStack Query 기본 설정(`a25f1eb`, mutations 재시도 0), Sentry · PostHog 연결 + URL 가리기(`0a7da6e`, 미리보기에서 수신 확인). Phase 0 FE — Vercel 연결 · `main` 보호. 그 밖에 CI typecheck 버그 수정(`0e9c33a`, `next typegen` 선행), 페이지 제목 뿌기사주(`09cb687`)
- **다음 작업**
  1. (FE/캐릭터) 캐릭터 에셋 규격 문서(`FRONTEND.md` 2장) + 뿌기 기본 포즈 1장 — Figma 말 캐릭터 원본(119:60) 레이어 구조 조회부터
  2. (PD) 손그림 프레임 SVG 4장 · 디자인 토큰 v0(기본 색 + 오행 5색) · 심사용 페이지 문구 · 약관 · 환불정책 초안 — FE 토큰 · 프레임 · 심사용 페이지 체크박스의 선행
  3. (전원) D-12 · D-14 결정 (마감 9/29 초과)
  4. (BE-A) `뿌기사주` 도메인 구매 + PG 상담 + 사업자등록
  5. (BE-B) BE 저장소 · 스테이징 — D-14 확정 전까지 `backend/`는 비워 둔다
- **보류 (재개 조건)**
  - AppShell · 404 화면 실기기 iOS Safari 확인 — iOS 기기 확보 후 (Android Chrome · 카카오톡 인앱은 통과)
  - 폰트 적용 — 글꼴 결정 통보 후 (FONT-HOLD)
  - openapi-fetch 클라이언트 · API 타입 — BE OpenAPI 스펙 수령 후
  - 관측 API 경로 가리기(Sentry fetch 기록의 `/api/...` 속 토큰) — BE OpenAPI 스펙 수령 후
  - 사이드 메뉴 애정운 · 취업운 — D-10 확정 후
  - 토큰 CSS 변수 · 프레임 `border-image` — PD 토큰 v0 · 프레임 SVG 수령 후
  - 심사용 페이지 — PD 문구 · D-12(사업자 정보) · 도메인 후
  - Sentry 소스맵 업로드(`SENTRY_AUTH_TOKEN`) — Phase 7
- **막힌 점 · 전달할 것**
  - D-12 · D-14 마감(9/29) 초과. D-12 → 심사용 페이지 사업자 footer, D-14 → BE 스켈레톤 · OpenAPI 스펙이 막힌다
  - (PD 전달) 개인정보처리방침에 국외 이전 고지 필요 — PostHog(US) · Sentry(지역 확인 필요), 오류 정보 · 접속 기록
  - (PD 전달) 카카오톡 인앱브라우저의 떠 있는 버튼이 화면 오른쪽 가운데를 가린다 — 누르는 요소 배치 참고
  - PG 심사 기간 미확인 (상담 후 기입)
  - Claude Code 는 `frontend/` 에서 시작한다 — 루트에 `CLAUDE.md` 가 없으므로 루트에서 시작하면 규칙이 세션 처음에 읽히지 않는다
  - Playwright 브라우저 미설치 — E2E를 처음 돌리기 전에 `pnpm -C frontend e2e:install` 필요
  - 캐릭터 이름을 서비스명에 맞춰 `뿡기` → `뿌기`로 같이 바꿨다. 캐릭터 이름을 따로 가려면 되돌려야 한다
  - (PD 전달) 오류 화면 · 404 화면 문구 필요 — 지금은 자리표시 "오류가 발생했습니다" · "다시 시도" · "페이지를 찾을 수 없습니다" · "홈으로" (TODO(PD 문구))
  - `app/error.tsx` 는 일부러 오류를 던지는 화면이 없어 실기기에서 띄워 보지 못했다 — 단위 테스트 4건으로 확인. 첫 실제 API 연결 화면에서 오류 경로를 실기기로 확인한다
  - 알려진 작은 문제: PC 에서 스크롤바가 있는 긴 페이지는 사이드 메뉴 패널이 앱 기둥보다 몇 px 오른쪽으로 나갈 수 있다 (휴대폰 영향 없음)

## 마감 체크

| 날짜 | 마감 | 상태 |
|---|---|---|
| 9/29 (화) | D-01, D-02, D-12, D-14 확정 (D-11 완료) | ⬜ 초과 |
| 10/2 (금) | 사업자등록 완료 · PG 신청 접수 | ⬜ |
| 10/4 (일) | Phase 1 완료 (골격 + 심사용 페이지 배포) | ⬜ |
| 10/10 (토) | Phase 2 완료 (결제 테스트 E2E) | ⬜ |
| 10/17 (토) | Phase 4 완료 (수능운 흐름) | ⬜ |
| 10/20 (화) | PG 승인 여부 → 미승인 시 Plan B 가동 | ⬜ |
| 10/23 (금) | Phase 5 완료 (선물하기) | ⬜ |
| 10/28 (수) 18:00 | 코드 프리즈 | ⬜ |
| 10/29 (목) | 운영 환경 실결제 + 환불 확인 | ⬜ |
| 10/31 (토) | 출시 | ⬜ |

## 결정 로그

| ID | 결정 | 날짜 | 결정자 |
|---|---|---|---|
| DOCS-PLACE | DOCS-WEB-ONLY 보완: 지시문에 파일 목록과 SHA-256 이 있으면 Claude Code 가 웹 대화 완성본을 압축 해제 · 지정 경로로 복사 · 지정 문서 삭제/이동까지 한다. 내용 변경 금지, 배치 후 해시 확인, 불일치 시 커밋 금지 | 2026-09-30 | FE |
| DOCS-FE-OWN | `PHASES.md` · `PROGRESS.md` 를 루트 `docs/` 에서 `frontend/docs/` 로, 루트 `CLAUDE.md` 를 `frontend/CLAUDE.md` 로 옮긴다. 셋 다 FE 개인 작업 문서이며 팀 합의 문서가 아니다. 루트 `docs/` 는 API 명세처럼 팀이 함께 쓰는 문서 자리로 비워 둔다. DOCS-SPLIT 대체 | 2026-09-30 | FE |
| OBS-PRIVACY | 관측 수집 범위: Sentry 오류 · PostHog 페이지뷰만. 화면 녹화 · 자동 수집 끔, `sendDefaultPii` 끔. URL 은 `maskUrl` 로 동적 경로를 패턴으로 · 쿼리 · 해시 제거 후 전송(선물 토큰 · 결제 파라미터 보호). PostHog 지역 US, Sentry 지역은 조직 설정에서 확인 후 기입. Vercel 빌드는 관측 키 누락 시 실패 | 2026-09-30 | FE |
| FONT-HOLD | 본문 · 제목 글꼴 모두 보류(Pretendard 포함). 결정 전까지 코드에서 글꼴을 지정하지 않는다. 결정되면 FE 가 통보받아 적용 | 2026-09-30 | FE |
| FIGMA-WEB-ONLY | Figma 조회는 웹 대화에서만 한다. Claude Code 는 Figma MCP 를 호출하지 않고, 지시문에 적힌 Figma 값만 쓴다 | 2026-09-30 | FE |
| DOCS-WEB-ONLY | 저장소 문서(`*.md`)는 Claude Code가 수정하지 않는다. 문서 변경(PROGRESS·PHASES 갱신 포함)은 웹 대화에서 작성한 완성본으로만 하고, Claude Code는 diff 확인·커밋만 한다 | 2026-09-30 | FE |
| BRANCH | FE 코드 작업은 전부 `boyeon` 브랜치에서 한다. `main` 직접 푸시 금지, `boyeon` → `main` PR. 기존 `feat/`·`fix/`·`chore/` 규칙 폐기 | 2026-09-30 | FE |
| DOCS-RESTORE | 저장소 문서의 용어·문구를 원본 초안 기준으로 복원 (오행 5색, 수능운, 인앱브라우저, 행정, 요일 등). D-11 확정·문서 분리·REPO 결정은 유지 | 2026-09-30 | FE |
| NAME | 서비스명 **뿌기사주** 확정 (D-11). 도메인·OG·심사용 페이지에 이 이름을 쓴다. 캐릭터 이름도 `뿌기`로 통일 | 2026-09-26 | PD |
| DOCS-SPLIT | ~~`docs/`는 팀 공용 문서만(PHASES·PROGRESS).~~ → DOCS-FE-OWN 으로 대체. FE 전용 내용(스택 상세·손그림 톤·컴포넌트 맵)은 `frontend/docs/FRONTEND.md`로 분리. README도 루트=공용 / `frontend/README.md`=FE 전용 | 2026-09-26 | FE |
| FE-STACK | 프론트엔드 스택 확정: Next.js(App Router)+TS, Tailwind, `border-image` 손그림 프레임, vaul/Radix, TanStack Query, openapi-fetch, RHF+Zod, Zustand(위저드 한정), dayjs, next/og, Biome/Vitest/Playwright, Sentry/PostHog | 2026-09-26 | FE |
| REPO | 저장소 1개(`saju-project`) 안에 `frontend/`와 `backend/`를 폴더로 분리. `docs/`는 루트. 백엔드 스택(D-14) 확정 전까지 `backend/`는 비워 둔다 | 2026-09-26 | FE |

## 실험 로그 (Phase 8)

| 실험 | 가설 | 기간 | 판정 기준 | 결과 |
|---|---|---|---|---|
| (아직 없음) | | | | |

## 세션 로그

- 2026-09-30 · 문서 구조 변경(DOCS-FE-OWN): PHASES · PROGRESS · CLAUDE 를 `frontend/` 로 옮겨 FE 개인 문서로 전환, 루트 README · PR 템플릿 · backend README 정리. 완성본 배치를 Claude Code 에 허용(DOCS-PLACE). 코드 변경 없음.
- 2026-09-30 · Phase 1 FE: 라우트 오류 화면 `error.tsx`(AppShell 유지 · Sentry · `retry`) · 404 `not-found.tsx` · `global-error.tsx` `retry` 통일. 실기기 Android Chrome · 카카오톡 인앱 통과, iOS 보류. 문서: Phase 1 체크박스 추가.
- 2026-09-30 · Phase 1 FE: 라우트 26개 · AppShell · 공통 컴포넌트 7종 · TanStack Query 기본 설정 · Sentry/PostHog(URL 가리기). Phase 0: Vercel 연결 · main 보호. CI typecheck 수정. 결정 OBS-PRIVACY · FONT-HOLD · FIGMA-WEB-ONLY.
- 2026-09-30 · 문서 원본 복원·확정(DOCS-RESTORE), 브랜치 규칙 `boyeon`(BRANCH), 문서 변경 웹 대화 경유 규칙(DOCS-WEB-ONLY). 화면 코드 없음.
- 2026-09-26 · 서비스명 `뿌기사주` 확정 반영(D-11), FE 문서 분리(`frontend/docs/FRONTEND.md`), README 공용/FE 분리. 화면 코드 없음.
- 2026-09-26 · 저장소 초기 세팅. `saju-project/` 생성, git init, Next.js 16(App Router·TS·Tailwind 4·Biome) 스캐폴드, 계획 라이브러리 전부 설치·버전 고정, Vitest/Playwright 설정, `/api` rewrites 골격, PR 템플릿·CI·문서 배치, GitHub 원격 연결·첫 푸시. 화면 코드는 아직 없음.
- 2026-09-26 · 프론트엔드 스택 확정 및 문서 반영. 코드 없음.
- 2026-09-26 · 계획 문서 초안 작성 (PHASES/PROGRESS/CLAUDE). 코드 없음.
