# backend

백엔드는 **Spring Boot 3 + PostgreSQL**로 확정됐다. 운영 배포는 Railway, API prefix는 `/api/v1`을 사용한다.

- 인증: 카카오 OAuth + 서버 세션 쿠키
- 결제: 토스페이먼츠
- 파일: Cloudflare R2
- 외부 생성: 만세력 계산 JSON에 근거한 Liner 해석 문장만 사용

구현 기준 문서:

- [API 명세](../docs/API_SPEC.md)
- [ERD](../docs/ERD.md)
- [공통 응답·예외 코드](../docs/COMMON_RESPONSE_AND_ERROR_CODES.md)
- [BE-A/BE-B 역할 분담](docs/BACKEND_ROLE_SPLIT.md)

프로젝트 스켈레톤을 생성한 뒤 이 문서에 설치·실행·테스트 명령어, 로컬 DB 실행법, 환경 변수 이름, OpenAPI 경로를 추가한다. 비밀값은 문서나 저장소에 기록하지 않는다.
