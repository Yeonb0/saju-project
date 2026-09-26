# 뿌기사주

수능 수험생 대상 사주 + 부적 모바일 웹서비스. 창업 경진대회 출품작 (출시 2026-10-31, 평가 ~11/21).
서비스명은 2026-09-26에 **뿌기사주**로 확정됐다 (docs/PHASES.md 7장 D-11).

```
frontend/   Next.js(App Router) + TypeScript — 지금 개발은 전부 여기서
backend/    비어 있음. 백엔드 스택(D-14) 확정 전까지 코드를 넣지 않는다
docs/       팀 공용 문서 — PHASES.md(계획·결정) · PROGRESS.md(진행 상황)
```

파트별 문서는 각 폴더 안에 둔다. `docs/`에는 팀 전체가 공유하는 문서만 올린다.

| 문서 | 내용 |
|---|---|
| [docs/PHASES.md](docs/PHASES.md) | 일정, 화면 목록, Phase별 작업, 결정 필요 항목(D-xx), 리스크, 컷 라인 |
| [docs/PROGRESS.md](docs/PROGRESS.md) | 현재 Phase, 다음 작업, 막힌 점, 결정 로그, 세션 로그 |
| [CLAUDE.md](CLAUDE.md) | 공통 작업 규칙 (세션 규칙, 금지 사항, 브랜치·PR) |
| [frontend/README.md](frontend/README.md) | 프론트엔드 실행 방법과 명령어 |
| [frontend/docs/FRONTEND.md](frontend/docs/FRONTEND.md) | FE 스택 선택 이유, 손그림 톤 구현 방침, 공통 컴포넌트 맵 |

## 시작하기

작업을 시작하기 전에 [docs/PROGRESS.md](docs/PROGRESS.md)에서 현재 Phase와 다음 작업을 확인한다.
프론트엔드 개발은 [frontend/README.md](frontend/README.md)를 본다.

## 규칙 요약

- `main` 직접 푸시 금지. 기능 브랜치 → PR → CI 초록불 → 머지.
- 결제 금액은 서버만 계산한다. 클라이언트가 보낸 금액을 믿지 않는다.
- PG 키·OAuth 시크릿 등 비밀값은 커밋하지 않는다 (`.env*`는 git 제외).
- 결정 대기(D-xx) 항목에 걸린 작업은 추측으로 진행하지 않는다.
- "합격 보장" 같은 단정적 효과 표현 금지. 결과 화면에는 재미로 보는 콘텐츠라는 고지를 둔다.
- 10/28 18:00 코드 프리즈 이후 기능 추가 금지. 11/16 ~ 11/19은 기능 변경 금지.
