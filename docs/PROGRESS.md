# PROGRESS

> 모든 세션은 시작할 때 `docs/PHASES.md`와 이 파일을 읽고, 끝날 때 이 파일을 갱신한다.
> 갱신할 때는 "현재 상태"를 덮어쓰고, "세션 로그"에는 맨 위에 한 줄씩 추가한다.

## 현재 상태

- **현재 Phase**: Phase 0 — 결정 · 확정 착수 · 초기 세팅 (9/26 ~ 9/30)
- **마지막으로 끝낸 작업**: 서비스명 **뿌기사주** 확정(D-11) 문서 반영, FE 전용 문서를 `frontend/docs/FRONTEND.md`로 분리, 루트 README를 팀 공용으로 정리 + `frontend/README.md` 신설. 그 전: FE 저장소 생성 + 라이브러리 설치·버전 고정 (Next.js 16.3.6 / React 19.2.8 / Tailwind 4.3.3 / Biome 2.4.2, pnpm 10.33.0). 저장소에 `CLAUDE.md`, `docs/`, PR 템플릿, CI 워크플로 배치. GitHub 원격 연결 완료 ([Yeonb0/saju-project](https://github.com/Yeonb0/saju-project))
- **다음 작업**
  1. 결정 회의 → PHASES.md 7장 D-01 ~ D-16 확정 (D-11 확정됨. D-12 사업자 명의·대회 규정, D-14 백엔드 언어는 9/29까지)
  2. (BE-A) `뿌기사주` 도메인 구매 + PG 2곳 이상 상담 문의 + 사업자등록 신청 (9/28 일)
  3. (FE) Vercel 연결 (`main` → 스테이징, PR → 미리보기) + GitHub `main` 브랜치 보호 규칙 켜기
  4. (BE-B) BE 저장소·스테이징 세팅 — 단, D-14 확정 전까지 `backend/`는 비워 둔다
  5. (PD) 손그림 프레임 SVG 4장 + 손글씨체 후보 2종 (Phase 1 선행)
- **막힌 점**
  - 결정 항목 15개 미정 (D-11만 확정) → Phase 1 착수 전 최소 D-12, D-14 필요
  - PG 심사 기간 미확인 (상담 후 기입)
  - Vercel 연결과 GitHub 브랜치 보호 설정은 계정 로그인이 필요해 사람이 직접 해야 함
  - Playwright 브라우저 미설치 — E2E를 처음 돌리기 전에 `pnpm -C frontend e2e:install` 필요
  - 계획 문서는 기존 초안을 옮겨 적은 것이다. 원본 파일이 따로 있으면 그걸로 덮어쓴다
  - 캐릭터 이름을 서비스명에 맞춰 `뿡기` → `뿌기`로 같이 바꿨다. 캐릭터 이름을 따로 가려면 되돌려야 한다

## 마감 체크

| 날짜 | 마감 | 상태 |
|---|---|---|
| 9/29 (월) | D-01, D-02, D-12, D-14 확정 (D-11 완료) | ⬜ |
| 10/2 (금) | 사업자등록 완료 · PG 신청 접수 | ⬜ |
| 10/4 (일) | Phase 1 완료 (골격 + 심사용 페이지 배포) | ⬜ |
| 10/10 (토) | Phase 2 완료 (결제 테스트 E2E) | ⬜ |
| 10/17 (토) | Phase 4 완료 (수능이 흐름) | ⬜ |
| 10/20 (화) | PG 승인 여부 → 미승인 시 Plan B 가동 | ⬜ |
| 10/23 (금) | Phase 5 완료 (선물하기) | ⬜ |
| 10/28 (수) 18:00 | 코드 프리즈 | ⬜ |
| 10/29 (목) | 운영 환경 실결제 + 환불 확인 | ⬜ |
| 10/31 (토) | 출시 | ⬜ |

## 결정 로그

| ID | 결정 | 날짜 | 결정자 |
|---|---|---|---|
| NAME | 서비스명 **뿌기사주** 확정 (D-11). 도메인·OG·심사용 페이지에 이 이름을 쓴다. 캐릭터 이름도 `뿌기`로 통일 | 2026-09-26 | PD |
| DOCS-SPLIT | `docs/`는 팀 공용 문서만(PHASES·PROGRESS). FE 전용 내용(스택 상세·손그림 톤·컴포넌트 맵)은 `frontend/docs/FRONTEND.md`로 분리. README도 루트=공용 / `frontend/README.md`=FE 전용 | 2026-09-26 | FE |
| FE-STACK | 프론트엔드 스택 확정: Next.js(App Router)+TS, Tailwind, `border-image` 손그림 프레임, vaul/Radix, TanStack Query, openapi-fetch, RHF+Zod, Zustand(위자드 한정), dayjs, next/og, Biome/Vitest/Playwright, Sentry/PostHog | 2026-09-26 | FE |
| REPO | 저장소 1개(`saju-project`) 안에 `frontend/`와 `backend/`를 폴더로 분리. `docs/`는 루트. 백엔드 스택(D-14) 확정 전까지 `backend/`는 비워 둔다 | 2026-09-26 | FE |

## 실험 로그 (Phase 8)

| 실험 | 가설 | 기간 | 판정 기준 | 결과 |
|---|---|---|---|---|
| (아직 없음) | | | | |

## 세션 로그

- 2026-09-26 · 서비스명 `뿌기사주` 확정 반영(D-11), FE 문서 분리(`frontend/docs/FRONTEND.md`), README 공용/FE 분리. 화면 코드 없음.
- 2026-09-26 · 저장소 초기 세팅. `saju-project/` 생성, git init, Next.js 16(App Router·TS·Tailwind 4·Biome) 스캐폴드, 계획 라이브러리 전부 설치·버전 고정, Vitest/Playwright 설정, `/api` rewrites 골격, PR 템플릿·CI·문서 배치, GitHub 원격 연결·첫 푸시. 화면 코드는 아직 없음.
- 2026-09-26 · 프론트엔드 스택 확정 및 문서 반영. 코드 없음.
- 2026-09-26 · 계획 문서 초안 작성 (PHASES/PROGRESS/CLAUDE). 코드 없음.
