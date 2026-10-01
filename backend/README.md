# backend

**아직 비어 있다. D-14(백엔드 2명의 주 언어 → 스택 A/B 확정)가 정해지기 전까지 코드를 넣지 않는다.**

- A안: Spring Boot 3 + Spring Security OAuth2 Client(카카오/구글) + JPA + PostgreSQL
- B안: Next.js 풀스택 + Supabase (이 경우 이 폴더는 없어지고 `frontend/`로 합쳐진다)

위 A/B안은 FE 가 계획을 잡으며 적은 제안이다 (`frontend/docs/PHASES.md` 3장·3-2). 백엔드 팀 결정이 아니며, 확정되면 백엔드 담당이 이 파일을 고쳐 쓴다.

## 확정되면 여기 적을 것

- 설치 / 실행 / 테스트 명령어
- 로컬 DB 띄우는 법
- 환경 변수 목록 (값은 적지 않는다)
- OpenAPI 스펙 경로 → 프론트가 `pnpm api:types`로 타입을 뽑아 쓴다
