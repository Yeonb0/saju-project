# PROGRESS

> 모든 세션은 시작할 때 `docs/PHASES.md`와 이 파일을 읽고, 끝날 때 이 파일을 갱신한다.
> 갱신할 때는 "현재 상태"를 덮어쓰고, "세션 로그"에는 맨 위에 한 줄씩 추가한다.

## 현재 상태

- **현재 Phase**: Phase 0 — 결정 · 확정 착수 · 초기 세팅 (9/26 ~ 9/30)
- **마지막으로 끝낸 작업**: FE 저장소 생성 + 라이브러리 설치·버전 고정 (Next.js 16.3.6 / React 19.2.8 / Tailwind 4.3.3 / Biome 2.4.2, pnpm 10.33.0). 저장소에 `CLAUDE.md`, `docs/`, PR 템플릿, CI 워크플로 배치
- **다음 작업**
  1. 결정 회의 → PHASES.md 7장 D-01 ~ D-16 확정 (D-11 서비스명, D-12 사업자 명의·대회 규정, D-14 백엔드 언어는 9/29까지)
  2. (BE-A) PG 2곳 이상 상담 문의 + 사업자등록 신청 (9/28 일)
  3. (FE) Vercel 연결 (`main` → 스테이징, PR → 미리보기), GitHub 원격 연결
  4. (BE-B) BE 저장소·스테이징 세팅 — 단, D-14 확정 전까지 `backend/`는 비워 둔다
  5. (PD) 손그림 프레임 SVG 4장 + 손글씨체 후보 2종 (Phase 1 선행)
- **막힌 점**
  - 결정 항목 16개 전부 미정 → Phase 1 착수 전 최소 D-11, D-12, D-14 필요
  - PG 심사 기간 미확인 (상담 후 기입)
  - GitHub 원격 미연결: 이 PC에 `gh` CLI가 없다. 설치하거나 웹에서 저장소를 만든 뒤 `git remote add origin` 필요
  - Vercel 연결은 계정 로그인이 필요해 사람이 직접 해야 함

## 마감 체크

| 날짜 | 마감 | 상태 |
|---|---|---|
| 9/29 (월) | D-01, D-02, D-11, D-12, D-14 확정 | ⬜ |
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
| FE-STACK | 프론트엔드 스택 확정: Next.js(App Router)+TS, Tailwind, `border-image` 손그림 프레임, vaul/Radix, TanStack Query, openapi-fetch, RHF+Zod, Zustand(위자드 한정), dayjs, next/og, Biome/Vitest/Playwright, Sentry/PostHog | 2026-09-26 | FE |
| REPO | 저장소 1개(`saju-project`) 안에 `frontend/`와 `backend/`를 폴더로 분리. `docs/`는 루트. 백엔드 스택(D-14) 확정 전까지 `backend/`는 비워 둔다 | 2026-09-26 | FE |

## 실험 로그 (Phase 8)

| 실험 | 가설 | 기간 | 판정 기준 | 결과 |
|---|---|---|---|---|
| (아직 없음) | | | | |

## 세션 로그

- 2026-09-26 · 저장소 초기 세팅. `saju-project/` 생성, git init, Next.js 16(App Router·TS·Tailwind 4·Biome) 스캐폴드, 계획 라이브러리 전부 설치·버전 고정, Vitest/Playwright 설정, `/api` rewrites 골격, PR 템플릿·CI·문서 배치. 화면 코드는 아직 없음.
- 2026-09-26 · 프론트엔드 스택 확정 및 문서 반영. 코드 없음.
- 2026-09-26 · 계획 문서 초안 작성 (PHASES/PROGRESS/CLAUDE). 코드 없음.
