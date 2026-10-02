# 뿌기사주

수능 수험생 대상 사주 + 부적 모바일 웹서비스. 창업 경진대회 출품작 (출시 2026-10-31, 평가 ~11/21).

```
frontend/   Next.js(App Router) + TypeScript 모바일 웹 — FE 담당
backend/    Spring Boot 3 + PostgreSQL — BE 담당
docs/       팀 공용 제품·기능·API·데이터 문서
.github/    PR 템플릿, CI (frontend 검사)
```

파트별 문서는 각 폴더 안에 둔다.

| 문서 | 내용 |
|---|---|
| [frontend/README.md](frontend/README.md) | 프론트엔드 실행 방법, 명령어, FE 문서 목록 |
| [backend/README.md](backend/README.md) | 백엔드 폴더 안내 |
| [docs/PRD.md](docs/PRD.md) | 제품 목표, 사용자, 범위, 정책, 성공 지표 |
| [docs/FUNCTIONAL_SPEC.md](docs/FUNCTIONAL_SPEC.md) | 화면·기능별 동작과 예외 |
| [docs/API_SPEC.md](docs/API_SPEC.md) | `/api/v1` endpoint·요청·응답 계약 |
| [docs/ERD.md](docs/ERD.md) | PostgreSQL 데이터 모델·제약·트랜잭션 경계 |
| [docs/COMMON_RESPONSE_AND_ERROR_CODES.md](docs/COMMON_RESPONSE_AND_ERROR_CODES.md) | 공통 응답 envelope와 예외 코드 |
| [docs/PENDING_DECISIONS.md](docs/PENDING_DECISIONS.md) | 확정 정책과 구현 전 남은 결정 |
| [backend/docs/BACKEND_ROLE_SPLIT.md](backend/docs/BACKEND_ROLE_SPLIT.md) | BE-A/BE-B 책임·API·테이블·일정·리뷰 경계 |

## 저장소 공통으로 걸려 있는 것

- `main` 은 보호돼 있어 직접 푸시할 수 없다. 작업 브랜치 → PR → CI 초록불 → 머지.
- CI(`.github/workflows/ci.yml`)는 `frontend/` 의 lint · typecheck · test · build 를 돌린다.
- 비밀값(PG 키, OAuth 시크릿 등)은 커밋하지 않는다 (`.env*` 는 git 제외).
