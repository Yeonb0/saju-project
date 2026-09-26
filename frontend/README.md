# 뿌기사주 — frontend

Next.js(App Router) + TypeScript 모바일 웹. 이 폴더가 FE 작업 공간 전체다.

- 스택 선택 이유 · 손그림 톤 구현 방침 · 공통 컴포넌트 맵 → [docs/FRONTEND.md](docs/FRONTEND.md)
- 일정 · 화면 목록 · Phase 체크박스 → [../docs/PHASES.md](../docs/PHASES.md)
- 현재 Phase · 다음 작업 → [../docs/PROGRESS.md](../docs/PROGRESS.md)

## 시작하기

```bash
pnpm install                 # 버전은 save-exact로 고정되어 있다
cp .env.example .env.local   # 값은 각자 채운다. .env* 는 커밋하지 않는다
pnpm dev                     # http://localhost:3000
```

## 명령어

| 명령 | 하는 일 |
|---|---|
| `pnpm dev` | 개발 서버 |
| `pnpm build` / `pnpm start` | 프로덕션 빌드 / 빌드 결과 실행 |
| `pnpm lint` / `pnpm lint:fix` | Biome 검사 / 자동 수정 |
| `pnpm typecheck` | `tsc --noEmit` |
| `pnpm test` / `pnpm test:watch` | Vitest 1회 / watch |
| `pnpm e2e` | Playwright (모바일 뷰포트 2종). 처음이면 `pnpm e2e:install` 먼저 |
| `pnpm api:types` | `openapi.json` → `src/types/api.d.ts`. 백엔드 스펙이 나온 뒤에 쓴다 |

루트에서 실행하려면 `pnpm -C frontend <script>`.

## 폴더

```
src/app/     라우트 (App Router)
src/lib/     공용 유틸 — date.ts 는 Asia/Seoul 고정
src/types/   api.d.ts 는 pnpm api:types 로 생성. 손으로 고치지 않는다
e2e/         Playwright — 수능이 · 선물 구매 · 선물 수신 3개 흐름
docs/        FE 전용 문서
```

## 작업할 때

- 새 화면 만들기 전에 [docs/FRONTEND.md](docs/FRONTEND.md) 3장 컴포넌트 맵에서 재사용할 게 있는지 먼저 본다.
- API 타입은 손으로 쓰지 않는다. openapi-typescript로 생성한다.
- 기본은 클라이언트 컴포넌트. 서버 렌더링은 `/g/[token]`, 심사용·약관 페이지, 홈 첫 화면에만.
- 날짜 계산은 `src/lib/date.ts`를 거친다. 기기 시간대를 쓰지 않는다.
- 테두리는 `frame-*.svg` + CSS `border-image`. 박스마다 새로 그리지 않는다.
- 전역 상태는 선물 위자드용 Zustand 하나뿐. 그 외 추가 금지.
- 새 라이브러리는 [docs/FRONTEND.md](docs/FRONTEND.md) 1장 표에 없으면 추가 전에 팀에 먼저 묻는다.

## 확인 환경

iOS Safari · Android Chrome · **카카오톡 인챗브라우저** 3곳에서 본다. 결제·공유가 걸린 PR에는 인챗브라우저 스크린샷을 붙인다.

## 브랜치 · PR

`feat/<화면 또는 기능>`, `fix/<증상>`, `chore/<작업>`. `main` 직접 푸시 금지.
CI가 `lint` · `typecheck` · `test` · `build`를 돌린다. 초록불 아니면 머지하지 않는다.

## 배포

Vercel. `main` → 스테이징, PR → 미리보기 URL. (연결 아직 안 됨 — Phase 0 남은 작업)
