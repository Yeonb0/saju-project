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

## 실행 환경

- Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3
- PostgreSQL 17 (로컬 Docker Compose)
- 별도 Gradle 설치는 필요 없다. 첫 실행 시 Wrapper와 Maven 의존성을 다운로드한다.
- 한글 Windows 경로의 Java 실행 인자 파일을 위해 Gradle JVM은 네이티브 호환 인코딩을 사용하고, 소스 컴파일/테스트 인코딩은 UTF-8로 고정한다.

## 로컬 실행

저장소의 `backend/`에서 실행한다. Docker Desktop이 실행 중이어야 한다.

```powershell
docker compose up -d --wait db
.\gradlew.bat bootRun
```

macOS/Linux:

```sh
docker compose up -d --wait db
bash ./gradlew bootRun
```

- 기본 프로필: `local`, 포트: `8080`
- 기본 DB: `jdbc:postgresql://localhost:5432/sajuppugi`
- 기본 로컬 계정: `sajuppugi` / `local-development-only` (개발용)
- 프로세스 종료: 터미널에서 Ctrl+C
- 로컬 DB 중지: `docker compose stop db` (데이터 유지)
- `.env.example`은 설정 예시다. Spring Boot가 `.env` 파일을 자동으로 읽지 않는다.
- Compose에 사용할 `.env`를 별도로 만들었다면 DB 계정/포트 변경값을 Spring Boot 프로세스 환경변수에도 동일하게 설정한다.

## 기동 확인과 API 문서

| 경로 | 용도 |
|---|---|
| `GET /actuator/health` | 애플리케이션과 DB 상태 |
| `GET /actuator/health/liveness` | 프로세스 생존 확인 |
| `GET /actuator/health/readiness` | DB를 포함한 서비스 준비 상태 |
| `/v3/api-docs` | OpenAPI JSON, local 프로필에서만 공개 |
| `/v3/api-docs/v1` | 프론트 타입 생성용 `/api/v1/**` OpenAPI JSON |
| `/swagger-ui/index.html` | Swagger UI, local 프로필에서만 공개 |

헬스 응답은 Actuator 기본 형식인 `{ "status": "UP" }`이며 업무 API의 응답 envelope와 구분한다.

Swagger UI는 API 명세에 맞춰 `JSESSIONID` 세션 쿠키와 상태 변경 요청용
`X-CSRF-Token` 보안 스키마를 표시한다. 브라우저가 `HttpOnly` 세션 쿠키를 자동으로 전송하므로
Swagger UI에서 쿠키 값을 직접 입력하지 않는다. 인증 및 CSRF 발급 API가 구현되기 전까지
보호된 API의 `Try it out` 요청은 인증 오류가 정상이다.

FE 타입 생성용 고정 OpenAPI 파일은 `docs/openapi/api-v1.json`이며 backend 폴더에서
`./gradlew exportOpenApi` (Windows: `.\gradlew.bat exportOpenApi`)로 재생성한다.
테스트 컨텍스트에서 실제 controller 문서를 내보내며 외부 Liner/PG를 호출하지 않는다.

인증 구현 전에는 health와 local API 문서만 허용하고 나머지 요청은 거절한다. 폼/Basic 로그인과 기본 개발 사용자 로그인을 제공하지 않는다. CSRF 보호는 유지한다. 카카오 로그인, 세션, B가 결정한 CSRF 발급 계약은 후속 구현 대상이다.

## 테스트와 빌드

```powershell
.\gradlew.bat clean check bootJar
```

```sh
bash ./gradlew clean check bootJar
```

- 테스트 기본 DB는 테스트 범위에만 포함한 H2다. Docker 없이 기동/응답/보안 테스트를 실행할 수 있다.
- PostgreSQL 검증은 `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD`를 설정하고 같은 테스트를 실행한다.
- GitHub Backend CI는 PostgreSQL 서비스에서 테스트하고 실행 JAR를 빌드한다.
- 테스트용 컨트롤러는 `src/test/`에만 있으며 실행 JAR에 포함되지 않는다.
- 산출물: `build/libs/sajuppugi-backend-0.0.1-SNAPSHOT.jar`

### 로컬 PostgreSQL 테스트 DB

테스트에는 데이터 삭제가 포함된다. 개발/운영 DB에 테스트를 연결하지 않는다.
`compose.test.yml`은 개발 DB와 별도 컨테이너/계정으로 `sajuppugi_test`를 만들고,
localhost:55432에만 공개한다. tmpfs를 사용하므로 컨테이너 중지 시 테스트 데이터는 사라진다.
개발 DB의 5432 포트와 postgres-data 볼륨은 변경하지 않는다.

backend 폴더의 별도 PowerShell에서 실행한다. 테스트 환경변수는 이 터미널에만 적용된다.

```powershell
docker compose -p saju-tests -f compose.test.yml up -d --wait test-db
$env:TEST_DATABASE_URL = 'jdbc:postgresql://localhost:55432/sajuppugi_test'
$env:TEST_DATABASE_USERNAME = 'sajuppugi_test'
$env:TEST_DATABASE_PASSWORD = 'local-test-only'
Remove-Item Env:LINER_LIVE_TEST -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon check bootJar --rerun-tasks
```

Flyway는 빈 테스트 DB에 프로젝트 migration을 적용한다. 기본 테스트의 Liner는 fake이며
위 명령은 실제 Liner/결제 API를 호출하지 않는다. 종료와 환경변수 해제:

```powershell
docker compose -p saju-tests -f compose.test.yml down
Remove-Item Env:TEST_DATABASE_URL, Env:TEST_DATABASE_USERNAME, Env:TEST_DATABASE_PASSWORD -ErrorAction SilentlyContinue
```

실제 Liner 응답을 확인하는 스모크 테스트는 기본 테스트에서 비활성화되어 있다. API 사용량이 발생하므로
`LINER_API_KEY`를 환경변수나 `backend/.env`에 설정한 뒤 명시적으로 실행한다.

```sh
LINER_LIVE_TEST=true ./gradlew test --rerun-tasks \
  --tests 'com.sajuppugi.fortune.generation.LinerLiveSmokeTest'
jq . build/liner-live-response.json
```

`--rerun-tasks`를 생략하면 이전 실행 결과가 `UP-TO-DATE`로 재사용되어 실제 API를 호출하지 않을 수 있다.

## 환경 설정

| 변수 | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `staging`, `production` |
| `DB_URL` | JDBC URL (`jdbc:postgresql://host:port/database`) |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `PORT` | HTTP 포트, 기본 8080 |
| `LINER_API_KEY` | Liner 서버 API 키. 클라이언트·Git·로그에 노출 금지 |
| `LINER_MODEL` | Liner 모델 ID, 기본 `liner-mark` |
| `LINER_BASE_URL` | 기본 `https://platform.liner.com/api/v1` |
| `LINER_CONNECT_TIMEOUT` | 연결 제한시간, 기본 `3s` |
| `LINER_READ_TIMEOUT` | 응답 제한시간, 기본 `30s` |
| `LINER_MAX_OUTPUT_TOKENS` | 구조화 결과 최대 토큰, 기본 `4000` |
| `LINER_REASONING_EFFORT` | `none|low|medium|high|max`, 기본 `low` |

`staging`과 `production`에는 개발 DB 기본값이 없다. 세 DB 환경변수를 배포 환경에서 제공해야 한다. `postgresql://...` 연결 문자열을 그대로 `DB_URL`에 넣지 말고 JDBC 형식과 분리된 계정값을 사용한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'staging'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/sajuppugi'
$env:DB_USERNAME = 'sajuppugi'
$env:DB_PASSWORD = '<configured-password>'
.\gradlew.bat bootRun
```

로컬 프로필은 프로젝트의 `backend/.env`를 선택적으로 읽는다. 이 파일은 Git에서 제외되며 `LINER_API_KEY=...` 형식으로 저장한다. staging·production은 실제 Liner 어댑터가 기본이고 키가 없으면 시작에 실패한다. 운영 키는 반드시 배포 플랫폼의 비밀 환경변수로 등록하며 토스/OAuth/R2 키도 소스나 로그에 기록하지 않는다.

## DB 마이그레이션과 패키지

- Flyway 경로: `src/main/resources/db/migration`
- 상품·기본 견적과 지갑·지급분·원장 조회용 도메인 테이블을 Flyway migration으로 추가했다. 후속 기능은 시간 기반 버전의 새 migration으로 확장한다.
- Hibernate는 `ddl-auto: validate`이며 테이블을 자동 생성/수정하지 않는다.
- 이미 공유한 마이그레이션은 수정하지 않고 새 마이그레이션을 추가한다.
- 패키지: `common`, `auth`, `member`, `person`, `catalog`, `wallet`, `payment`, `gift`, `fortune`, `talisman`, `share`, `admin`, `infrastructure`.
- `common`의 API 응답/오류, 요청 traceId, 최소 보안을 구현했다. A 영역에는 도메인 타입과 내부 호출 계약, 일부 확정 정책을 추가했다. 상세 범위는 [백엔드 A 골격](docs/BACKEND_A_SCAFFOLD.md)을 참고한다.

## 컨테이너 및 Railway

`backend/`를 빌드 컨텍스트로 사용한다.

```sh
docker build -t sajuppugi-backend .
```

- Railway Root Directory를 `backend`로 설정하고 Dockerfile 빌드를 사용한다.
- 컨테이너 기본 프로필은 `production`이다. 스테이징은 `SPRING_PROFILES_ACTIVE=staging`으로 지정한다.
- DB 환경변수를 등록하고 준비 상태 확인 경로를 `/actuator/health/readiness`로 설정한다.
- 실행 컨테이너는 비루트 사용자이며 플랫폼의 `PORT`를 사용한다.
- 실제 배포 주소/계정은 아직 등록하지 않았다.

운세 견적·구매·결과 및 오행분석 HTTP API와 Liner adapter가 있다. 견적 응답/재조회에는
현재 잔액·부족분·추천을 포함하고 지갑 HTTP 조회는 유료·보너스 사용 가능 잔액을 반환한다.
지갑 내부 차감·원장·원거래 복구 adapter를 추가했지만 기본 비활성이다.
구매 중단/복구 worker, HTTP 멱등 계약, 실제 충전 지급, OAuth와 인물 저장소 adapter는 아직 미연결이다.
`WALLET_PURCHASES_ENABLED`는 기본 false이며 구매 복구 경로와 A/B 리뷰 완료 전 운영에서 켜지 않는다.
상세 범위는 `docs/WALLET_PURCHASE_IMPLEMENTATION.md`를 참고한다.
경로의 인증/CSRF 요구는 유지하며 인물·지갑 구매 dependency 미연결은 실패로 응답한다.
미정 정책을 임의로 확정하지 않는다. FE 질문별 구현 범위는 `docs/FE_REQUESTS_20261009.md`를 참고한다.
