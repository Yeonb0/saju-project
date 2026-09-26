# saju-project

수능 수험생 대상 사주 + 부적 모바일 웹서비스. 창업 경진대회 출품작 (출시 2026-10-31, 평가 ~11/21).
서비스명은 아직 미정이다 (docs/PHASES.md 7장 D-11).

```
frontend/   Next.js(App Router) + TypeScript — 지금 개발은 전부 여기서
backend/    비어 있음. 백엔드 스택(D-14) 확정 전까지 코드를 넣지 않는다
docs/       PHASES.md(계획·결정) · PROGRESS.md(진행 상황)
```

## 시작하기

```bash
cd frontend
pnpm install
cp .env.example .env.local   # 값은 각자 채운다. .env* 는 커밋하지 않는다
pnpm dev                     # http://localhost:3000
```

나머지 명령어와 작업 규칙은 [CLAUDE.md](CLAUDE.md), 일정과 결정 사항은 [docs/PHASES.md](docs/PHASES.md)를 본다.
작업을 시작하기 전에 [docs/PROGRESS.md](docs/PROGRESS.md)에서 현재 Phase와 다음 작업을 확인한다.

## 규칙 요약

- `main` 직접 푸시 금지. 기능 브랜치 → PR → CI 초록불 → 머지.
- 결제 금액은 서버만 계산한다.
- PG 키·OAuth 시크릿 등 비밀값은 커밋하지 않는다.
- 결정 대기(D-xx) 항목에 걸린 작업은 추측으로 진행하지 않는다.
