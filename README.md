# 뿌기사주

수능 수험생 대상 사주 + 부적 모바일 웹서비스. 창업 경진대회 출품작 (출시 2026-10-31, 평가 ~11/21).

```
frontend/   Next.js(App Router) + TypeScript 모바일 웹 — FE 담당
backend/    백엔드 — 스택이 정해지기 전까지 비어 있다
docs/       팀이 함께 쓰는 문서 자리 (API 명세 등). 지금은 없다
.github/    PR 템플릿, CI (frontend 검사)
```

파트별 문서는 각 폴더 안에 둔다.

| 문서 | 내용 |
|---|---|
| [frontend/README.md](frontend/README.md) | 프론트엔드 실행 방법, 명령어, FE 문서 목록 |
| [backend/README.md](backend/README.md) | 백엔드 폴더 안내 |

## 저장소 공통으로 걸려 있는 것

- `main` 은 보호돼 있어 직접 푸시할 수 없다. 작업 브랜치 → PR → CI 초록불 → 머지.
- CI(`.github/workflows/ci.yml`)는 `frontend/` 의 lint · typecheck · test · build 를 돌린다.
- 비밀값(PG 키, OAuth 시크릿 등)은 커밋하지 않는다 (`.env*` 는 git 제외).
