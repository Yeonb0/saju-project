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

카카오 OAuth, DB 기반 opaque 세션, `GET /session`의 CSRF 토큰 발급, Origin 검사와 로그아웃이
구현돼 있다. 폼/Basic 로그인과 기본 개발 사용자 로그인은 제공하지 않는다. 실제 카카오
로그인은 고정 프론트 도메인·프록시·카카오 Developers callback을 맞춘 staging E2E가 남아 있다.

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
| `KAKAO_REST_API_KEY` | 카카오 REST API 키. 서버 환경에만 설정 |
| `KAKAO_CLIENT_SECRET` | 카카오 클라이언트 시크릿. 서버 환경에만 설정 |
| `KAKAO_REDIRECT_URI` | 카카오 Developers에 등록한 정확한 콜백 URI |
| `FRONTEND_BASE_URL` | 로그인 완료 이동과 CSRF Origin 검증에 쓸 프론트엔드 고정 origin |
| `KAKAO_SESSION_TTL` | 서버 세션 TTL, 기본 `30d` |
| `KAKAO_STATE_TTL` | OAuth state TTL, 기본 `10m` |
| `KAKAO_MAX_ACTIVE_SESSIONS` | 사용자별 활성 세션 한도, 기본 `5`. 새 로그인은 유지하고 가장 오래 사용하지 않은 기존 세션부터 폐기 |
| `PERSON_ENCRYPTION_KEY` | 인물 정보용 Base64 32-byte 키. 기존 키를 임의 교체하지 않는다 |
| `GENERATION_CONTEXT_ENCRYPTION_KEY` | 생성 복구 context 전용 Base64 32-byte 키. 인물 키와 다른 값을 사용한다 |
| `GENERATION_CONTEXT_REENCRYPTION_ENABLED` | 기존 평문/`enc:v1` context의 `enc:v2` 전환 실행. 기본 `false` |
| `GENERATION_CONTEXT_REENCRYPTION_BATCH_SIZE` | 전환 트랜잭션당 최대 행 수, 기본 100, 허용 1~1000 |
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

### 스테이징: 프론트 프록시·카카오 로그인 연결

스테이징 주소는 바뀌지 않는 프론트 production 도메인 하나를 먼저 정한다. Vercel의
preview URL처럼 브랜치마다 달라지는 주소는 카카오 Redirect URI로 쓰지 않는다. 예를 들어
프론트 origin이 `https://staging.example.com`이면 다음 두 값은 각각 정확히 설정한다.

```text
FRONTEND_BASE_URL=https://staging.example.com
KAKAO_REDIRECT_URI=https://staging.example.com/api/v1/auth/kakao/callback
```

카카오 Developers에도 **같은** `KAKAO_REDIRECT_URI`를 등록한다. Vercel은
`/api/v1/*`를 스테이징 백엔드의 `API_PROXY_TARGET`으로 프록시해야 한다. 이 구성에서
OAuth 콜백의 `Set-Cookie`는 프론트 origin에 발급되어 브라우저의 이후 `/api/v1/*` 요청에
함께 전송된다. 백엔드 Railway 주소를 Redirect URI로 등록하면 프록시 경로와 쿠키 출처가
분리되므로 사용하지 않는다.

배포 전에 Railway와 Vercel의 값을 서로 대조하되, 키 원문은 채팅·로그·화면 공유에 적지
않는다. `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `TOSS_SECRET_KEY`, `LINER_API_KEY`,
DB 비밀번호와 암호화 키는 Railway 비밀 환경변수만 사용한다. FE에는 Toss의 대응되는
테스트 client key만 제공하며 server secret은 제공하지 않는다. 이 저장소의 `.env`는
Git ignore 대상이지만, 과거 대화나 외부 콘솔에 노출된 키의 회전은 각 공급자 콘솔에서
별도로 완료해야 한다.

스테이징 E2E 전에는 `TOSS_ADAPTER`, `WALLET_PURCHASES_ENABLED`,
`WALLET_RECOVERY_ENABLED`, `READING_GENERATION_RECOVERY_ENABLED`의 실제 값을 두
담당자가 함께 확인한다. 등껍질 지급·구매 기능은 기본 비활성이므로, PostgreSQL migration과
복구 설정을 확인하기 전에 켜지지 않는다. 실제 토스 테스트 결제는 보안 설정을 사용자와
확인한 뒤에만 수행한다.

### 로컬 E2E: 활성 상품과 실제 모드의 구분

기본 local DB는 충전 6종과 수능운 상품이 비활성이고 `TOSS_ADAPTER=disabled`라서
목록이 비거나 견적/충전이 막히는 것이 정상이다. 일상 개발 DB를 켜지 말고, 아래처럼
별도 PostgreSQL DB를 만든 뒤에만 활성화 상태를 바꾼다.

```sh
DB_NAME=sajuppugi_e2e DB_PORT=55433 docker compose -p sajuppugi-e2e up -d --wait db
```

해당 셸 또는 별도 Git-ignore `backend/.env`에서 `DB_URL`을 55433의
`sajuppugi_e2e`로 설정한 뒤 `./gradlew bootRun`을 한 번 실행해 Flyway migration을
적용한다. 그 DB 안에서만 다음 SQL로 필요한 카탈로그를 활성화한다. shared/staging DB나
기존 개발 DB에 이 SQL을 적용하지 않는다.

```sql
BEGIN;
UPDATE products
SET active = TRUE
WHERE category = 'TOP_UP'
   OR code = 'SUNEUNG_READING_WITH_TALISMAN';
COMMIT;
```

생성·구매 상태 머신과 PostgreSQL 원장 흐름만 확인할 때는
`LINER_ADAPTER=fake`, `WALLET_PURCHASES_ENABLED=true`,
`WALLET_RECOVERY_ENABLED=true`, `READING_GENERATION_RECOVERY_ENABLED=true`를
명시한다. `TOSS_ADAPTER=disabled` 상태에서는 실제 충전 승인 흐름을 검증할 수 없다.

실제 토스 **테스트** 승인까지 확인하려면 먼저 사용자와 안전한 로컬 설정을 확인한다.
그 뒤에만 서버 비밀 환경변수의 `TOSS_ADAPTER=api`, 테스트용 `TOSS_SECRET_KEY`,
`TOSS_RECOVERY_ENABLED=true`와 대응되는 FE test client key를 같은 테스트 상점으로
설정한다. secret key는 FE/채팅/로그에 넣지 않는다. 승인 결과 불명은 새 주문을 만들지
말고 기존 주문 조회·복구로 확인한다. 이 절차는 실제 결제/지갑 상태를 바꾸므로, 실행
전후 주문·원장·lot·잔액·구매 상태를 함께 대사한다.

기존 generation context를 전환할 때는 먼저 모든 새 인스턴스에 기존 `PERSON_ENCRYPTION_KEY`와 별도의
`GENERATION_CONTEXT_ENCRYPTION_KEY`를 함께 설정한다. 새 버전 배포 후 한 인스턴스에서만
`GENERATION_CONTEXT_REENCRYPTION_ENABLED=true`로 기동한다. 작업은 최대 100행씩 독립 트랜잭션으로
평문과 `enc:v1`만 `enc:v2`로 조건부 갱신하며 재실행해도 안전하다. 아래 조회 결과가 0인지 확인한 뒤
옵션을 다시 `false`로 내린다. 키나 context 원문은 조회·로그에 출력하지 않는다.

```sql
SELECT COUNT(*) AS legacy_generation_contexts
FROM reading_purchases
WHERE generation_context IS NOT NULL
  AND generation_context NOT LIKE 'enc:v2:%';
```

전환과 generation lease 복구 회귀는 개발 DB가 아닌 분리 테스트 DB에서 다음 전용 task로 실행한다.

```bash
TEST_DATABASE_URL=jdbc:postgresql://localhost:55432/sajuppugi_test \
TEST_DATABASE_USERNAME=sajuppugi_test \
TEST_DATABASE_PASSWORD=local-test-only \
./gradlew generationRecoveryPostgresTest
```

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
지갑 내부 차감·원장·원거래 복구 adapter, 확정 FAILED 구매의 복구 재처리, DEBITED/GENERATING
생성 재개 scheduler가 있다. 모두 기본 비활성이다. OAuth·인물 저장소·구매 HTTP 멱등 계약은
연결되어 있다. `WALLET_PURCHASES_ENABLED`는 기본 false이며, 실제 PG/브라우저 E2E와
운영 검토가 끝나기 전 staging/production에서 켜지 않는다.
상세 범위는 `docs/WALLET_PURCHASE_IMPLEMENTATION.md`를 참고한다.
실제 PostgreSQL 자동 테스트 및 SQL 실습은 `docs/POSTGRESQL_TEST_GUIDE.md`를 참고한다.
자동 복구 scheduler의 `WALLET_RECOVERY_ENABLED`도 기본 false다.
경로의 인증/CSRF와 서버 소유권 검사를 유지한다. 지갑 구매·생성 복구 기능은 연결돼 있지만
운영 feature flag는 검증 전까지 기본 비활성이다.
미정 정책을 임의로 확정하지 않는다. FE 질문별 구현 범위는 `docs/FE_REQUESTS_20261009.md`를 참고한다.
