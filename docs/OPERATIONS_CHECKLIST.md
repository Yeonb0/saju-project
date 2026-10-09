# 뿌기사주 개발 현황과 운영 구조 점검

확인일: 2026-10-09. 기준: GitHub main `381787b98bd536200865f4d54c342fd1271d563e`, BE-A PR #14, BE-B PR #15, FE PR #16, FE PROGRESS, 전달된 Q-33~36.

이 문서는 점검 결과와 제안이다. 팀 정책이나 확정 일정의 원본을 대체하지 않는다. 운영 서버 주소, 플랫폼 설정, 실제 DB, 트래픽 지표와 장애 로그는 확인하지 않았다. 현재 사용자 수와 운영 중인 배포 버전은 이 자료만으로 확인할 수 없다. 이번에는 코드 변경이나 테스트 재실행, 배포를 하지 않았다.

## 0. PostgreSQL, MySQL, Postman부터 구분하기

이 프로젝트는 PostgreSQL을 사용하도록 이미 구성돼 있다. 이유는 실사용자가 많으면 반드시 PostgreSQL이어야 해서가 아니라, 팀이 PostgreSQL을 선택했고 현재 DB 드라이버, Flyway migration, 로컬 실행, CI가 그 선택에 맞춰져 있기 때문이다. MySQL도 실제 서비스용 DB이지만 현재 코드에서 바꾸려면 드라이버뿐 아니라 SQL, 타입, migration, 동시성 테스트를 다시 확인해야 한다.

| 이름 | 역할 | 여기서 확인하는 것 |
|---|---|---|
| PostgreSQL / MySQL | 실제 데이터를 저장하고 조회하는 DB 서버 | 회원, 주문, 지급분, 잔액, 거래 내역 |
| Spring Boot | 요청을 받아 업무 규칙을 실행하는 백엔드 | 권한 검사, 금액 결정, 결제 승인, DB 읽기/쓰기 |
| Postman | 백엔드에 요청을 보내는 API 클라이언트 | URL·본문·헤더·쿠키, HTTP 상태와 JSON 응답 |
| pgAdmin / psql | PostgreSQL에 연결하는 조회·관리 도구 | 실제 테이블, 저장된 행, SQL 결과 |
| Flyway | DB 구조 변경을 순서대로 적용하는 도구 | 어떤 테이블·제약·인덱스가 언제 추가됐는지 |
| Docker Compose | 로컬 PostgreSQL 등을 정해진 설정으로 실행하는 도구 | DB 프로세스, 포트, 계정, 데이터 보관 볼륨 |

MySQL을 사용한 프로젝트에서도 Postman은 API 확인에 사용한다. Postman을 썼다는 사실만으로 그 프로젝트가 MySQL을 썼는지는 알 수 없다. DB 연결 설정의 `jdbc:mysql:` 또는 `jdbc:postgresql:`로 구분할 수 있다.

```text
Postman --HTTP 요청--> Spring Boot --SQL/JDBC--> PostgreSQL
Postman <--JSON 응답-- Spring Boot <--조회 결과-- PostgreSQL

pgAdmin 또는 psql --------SQL로 직접 조회--------> PostgreSQL
```

Postman의 200 응답만으로 DB 저장까지 검증되지는 않는다. 메모리나 목업에서 응답했을 수 있다. 데이터 쓰기 API를 확인할 때는 요청 전 DB 상태, 요청 응답, 요청 후 DB 상태, 서버 재시작 후 상태를 함께 본다. 브라우저/Postman에는 DB 비밀번호를 넣지 않고 백엔드가 DB에 연결한다.

### 0-1. 이 프로젝트에서 확인된 설정

- `backend/compose.yml`: `postgres:17-alpine`을 실행한다.
- `application-local.yml`: 기본 연결은 `jdbc:postgresql://localhost:5432/sajuppugi`다.
- `build.gradle`: PostgreSQL JDBC 드라이버와 PostgreSQL용 Flyway 의존성이 있다.
- migration: UUID, 시간대 포함 timestamp, CHECK/FK/UNIQUE 등을 사용한다.
- 자동 테스트의 기본 DB는 H2다. H2의 PostgreSQL 모드는 편의용이며 실제 PostgreSQL 검증을 대체하지 않는다. GitHub Backend CI는 실제 PostgreSQL을 사용한다.

### 0-2. 로컬에서 실행하고 실제 DB를 보기

아래는 안내 절차다. 이번 문서 수정 중에는 실행하지 않았다. Docker Desktop과 Java 21이 필요하다. 저장소의 현재 로컬 checkout은 이전 A 작업 시점이므로 최신 기능을 확인할 때는 먼저 최신 코드를 확보하되 다른 사람의 작업을 덮어쓰지 않는다.

**1단계: DB 실행.** Docker Desktop을 실행한 뒤 아래 경로의 PowerShell에서 실행한다. PostgreSQL은 별도로 Windows에 설치하지 않아도 Compose가 실행한다.

```powershell
Set-Location 'C:\Users\최지우\Documents\ChatGPT\26데모데이 4\.codex-saju-review\backend'
docker compose up -d --wait db
docker compose ps
```

정상 결과는 db가 실행 중이고 health가 healthy인 상태다. 접속 거부나 포트 충돌이 있으면 다음 단계로 넘어가지 않고 Docker와 5432 포트를 확인한다. `.env`에서 값을 바꿨다면 아래 기본값 대신 그 값을 사용한다.

**2단계: 백엔드 실행.** 아래 fake 설정은 로컬에서 외부 Liner 유료 호출을 하지 않는 연습용이다. PostgreSQL은 실제 DB로 동작한다. 실제 Liner 연결 시험과 staging/production에는 이 연습 설정을 적용하지 않는다.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:LINER_ADAPTER = 'fake'
.\gradlew.bat bootRun
```

백엔드가 시작되면 Flyway가 아직 적용하지 않은 migration을 실행한다. DB 컨테이너 실행만으로 업무 테이블이 생기는 것은 아니다. 현재 local 설정에는 `.env` 선택 읽기도 있다. 이미 같은 터미널에 `DB_URL` 등이 설정돼 있다면 기본값 대신 그 값이 적용된다.

**3단계: Postman에서 백엔드 확인.** `GET http://localhost:8080/actuator/health`를 보낸다. 정상 응답은 `{"status":"UP"}`다. `http://localhost:8080/swagger-ui/index.html`에서 현재 구현된 업무 API를 볼 수 있다. health 성공은 업무 기능 완성 여부와 별개다. 인증 구현 전에는 보호된 API가 인증 오류를 반환하는 것이 정상이며, 임의 userId를 넣거나 보안을 끄는 방법으로 해결하지 않는다.

**4단계: DB에 직접 연결.** 가장 간단한 시작 방법은 실행 중인 컨테이너의 psql이다. backend 폴더의 새 터미널에서 실행한다.

```powershell
docker compose exec db psql -U sajuppugi -d sajuppugi
```

psql 창에서 아래를 한 줄씩 실행한다. `\dt`와 `\q`는 SQL이 아닌 psql 명령이므로 pgAdmin Query Tool에 넣지 않는다.

```sql
SELECT version();
SELECT current_database(), current_user;
\dt
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
SELECT code, price_currency, price_amount, active
FROM products
ORDER BY code;
\q
```

정상이라면 PostgreSQL 버전, 현재 DB 이름, products/wallets 등의 테이블과 migration 이력이 나온다. 충전 상품 active=false는 현재 판매 차단 정책과 맞을 수 있다. 지갑 행이 비어 있는 것도 실제 지급·구매 쓰기를 아직 연결하지 않았다면 정상이다.

화면으로 보고 싶다면 Windows 데스크톱 pgAdmin을 설치하고 서버 연결을 등록한다. 아래 값은 확인한 Compose 기본값이며 로컬 개발 전용이다.

| 접속 항목 | 값 |
|---|---|
| 표시 이름 | 뿌기사주 로컬 |
| Host | localhost |
| Port | 5432 |
| Maintenance database | sajuppugi |
| Username | sajuppugi |
| Password | local-development-only |

연결 후 `Databases → sajuppugi → Schemas → public → Tables`를 펼친다. DB를 선택하고 Query Tool을 열어 위의 SELECT 문을 실행한다. pgAdmin 자체를 컨테이너로 띄우면 localhost의 의미가 달라지므로 이 접속 표는 Windows 데스크톱 pgAdmin 기준이다.

**5단계: 요청과 저장 결과 대조.** 실제 쓰기 API가 구현되면 테스트 계정으로 API를 호출하고 응답의 orderId/purchaseId/quoteId로 DB 행을 찾는다. 같은 요청을 재전송한 뒤 거래 수와 잔액이 추가로 바뀌지 않는지 확인한다. 백엔드를 재시작한 뒤 조회해 데이터가 남는지도 확인한다. 예를 들어 구매 quote가 실제 저장됐는지는 다음과 같이 본다. UUID는 응답에서 받은 실제 값으로 바꾼다.

```sql
SELECT id, requester_user_id, product_code, price_amount,
       created_at, expires_at, purchase_id
FROM purchase_quotes
WHERE id = '여기에-실제-quoteId'::uuid;
```

DB 조회는 검증을 위한 관찰이다. 지갑 잔액을 직접 UPDATE해서 기능이 정상인 것처럼 만들지 않는다. 테스트 데이터 생성은 전용 fixture 또는 구현된 테스트용 업무 흐름을 이용한다.

DB는 `docker compose stop db`로 중지하면 데이터 볼륨이 남는다. 백엔드는 실행 터미널에서 Ctrl+C로 종료한다. 볼륨을 삭제하는 명령은 저장 데이터를 지우므로 이 절차에 포함하지 않는다.

## 1. 현재 위치

지우님의 BE-A 작업은 상품·견적·지갑 조회와 정책 검증을 구현해 PR #14로 병합한 단계다. 최신 main에는 B의 계산·운세 생성 기반과 FE의 목업 화면도 들어왔다. 실제 사용자에게 로그인부터 결제, 지급, 구매, 결과까지 제공하는 연결은 아직 남아 있다.

| 영역 | 확인된 구현 | 남은 연결 또는 개발 |
|---|---|---|
| BE-A 기반 | Spring Boot, 환경 프로필, Dockerfile, Flyway, 공통 응답·오류, traceId, PostgreSQL CI | 실제 staging/production 배포 설정과 운영 확인 |
| 상품·견적 | JDBC 저장, 가격 스냅샷, 소유권 검사, 30분 만료, 견적 단건 구매 연결 | HTTP 응답 변환, 운세 상품 데이터, 구매 시 판매 상태 재검사 |
| 견적 자금 계산 | 보유 잔액, 구매 후 예상 잔액, 부족분, 단일 충전 상품 추천 | B 견적 API에 결합, GET /quotes/{quoteId}, 통일된 DTO |
| 지갑 | 사용 가능한 유료·보너스 잔액 조회, 원장 cursor 조회, lot 차감 순서와 배분 계산 | 실제 차감·지급·복구 DB 서비스, 잠금, 중복 처리 방지, 대사 |
| 충전 | 6종 지급량 등록, 주문 상태와 외부 PG port | 토스 adapter, 주문/승인/조회 API, 원자적 지급, 결제 결과 불명 복구 |
| 선물·운영 | 만료·재시도 정책, token/outbox/audit 계약 | 실제 선물 주문, 토큰 관리, 알림톡 worker, 관리자 권한과 감사 |
| BE-B | 결정론적 사주 계산, Liner 검증·재시도·fallback·snapshot, 운세 견적/구매/조회 컨트롤러, 부적 저장 기반 | OAuth·세션·인물 정보, ReadingSubjectPort/WalletPurchasePort 실제 연결, 이미지 렌더링·R2·보관함 |
| FE | 로그인·입력·충전·결과·안내의 다수 목업 화면, 오류 처리, 서버 값 표시 원칙, 관측 URL 마스킹 | OpenAPI 생성 타입과 실제 adapter, SDK·세션 연결, 결과 계약 반영, 확정 정책·사업자 정보, 실기기 E2E |

이전 PR #14 작업 기록에는 로컬 124개 테스트 및 PostgreSQL CI 통과가 있다. FE PROGRESS에는 62개 파일/499개 테스트 기록이 있다. 이는 당시 검증 기록이며 이번 점검에서 최신 코드의 테스트를 실행한 결과는 아니다.

최신 backend README의 마지막에 남은 “업무 HTTP API와 외부 서비스 연동은 없다”는 문장은 PR #15와 맞지 않는다. 실제로 운세 컨트롤러와 Liner adapter가 있다. 상태를 판단할 때 문서의 작성 시점과 코드 둘 다 확인해야 한다.

## 2. 가장 먼저 진행할 작업

| 순서 | 담당 | 작업 | 완료 판정 |
|---|---|---|---|
| 1 | A+B+PD | Q-33~36 계약/문구 확정, API_SPEC의 500+80=580 정합성 수정 | API·정책·FE 포트의 값과 오류 의미가 일치 |
| 2 | A+B | staging 기동, OpenAPI JSON 제공, OAuth·세션·인물 연결 | 실제 계정으로 로그인하고 본인 인물만 조회 가능 |
| 3 | A | 지갑 쓰기·멱등 저장·원장 구현, B WalletPurchasePort 연결 | 동시 구매와 요청 재전송에도 이중 차감이나 음수 잔액 없음 |
| 4 | A+FE | 토스 테스트 주문·승인·조회·지급 연결 | PG 승인 후 지급 커밋된 CREDITED만 완료 표시 |
| 5 | A+B+FE | 본인 수능운 한 경로 E2E, 중단·복구 테스트 | 충전→견적→차감→결과, 실패 시 재개/복구까지 확인 |
| 6 | PD+FE+A/B | 필수 동의 기록·연령 정책·정책 페이지·사업자 값 | 심사 페이지와 서버 처리의 계약 일치 |
| 7 | A+B+FE | 일반 운세 확대, 부적 렌더/R2, 선물/outbox, 관리자·운영 job | 각 기능의 오류·재처리·권한 경로 검증 |

2~6은 일부 병행 가능하다. PG 심사 준비는 로그인·충전 경로와 확정 고지가 우선이고, 출시 준비에는 실제 운세 제공과 장애 복구가 추가로 필요하다. 전달된 일정은 답변 10/11, FE 심사용 사이트 10/12, 카드사 심사 요청 10/14 전후, 출시 목표 10/31이다. 일정 확정 가능성은 staging과 실제 결제 연결 진행에 달려 있다.

Q-34는 내부 계산이 이미 있으므로 “새 계산 기능 개발”보다 HTTP 계약 통합이 우선이다. 응답의 잔액은 조회 시점 값이며 구매를 예약한 잔액이 아니다. 팝업 표시 뒤 다른 구매가 생길 수 있으므로 실제 차감 시 DB 잠금 안에서 부족 여부를 다시 판단한다.

Q-35의 “자동 보상”은 환급 성공이 서버 상태로 확인된 때만 화면에 표시한다. 코드에 보상 호출이 있다는 사실만으로 성공을 보장하지 않는다. Q-33의 PG 오류 원문과 Q-36의 정책 빈칸은 FE가 임의로 확정하지 않는다.

## 3. 직접 이해해야 할 서비스 구조

```text
브라우저
  → Next.js 화면 → FE port → 실제 API adapter
  → 같은 출처 /api 프록시
  → Spring Security: 세션·CSRF·권한
  → application service: 상품·지갑·결제·운세·선물
  → repository → PostgreSQL

외부 호출: 서버 adapter → 토스 / 카카오 / Liner / R2 / 알림톡
후속 작업: DB에 업무 변경과 작업 등록 → worker → 재시도·조회·복구
```

현재 합의는 하나의 Spring Boot 안에서 모듈을 나누는 구조다. 이 구조를 유지하면서 각 모듈의 책임과 데이터 변경 경계를 명확히 하는 것을 권한다. worker는 같은 코드베이스에서 별도 프로세스로 실행할 수 있다. 사용자가 많다는 이유만으로 서비스를 여러 개로 나눌 필요는 없다.

- FE는 표시와 사용자 흐름을 책임진다. 가격, 할인, 지급량, 부족분, 소유권의 최종 결정은 서버가 한다.
- A는 금전·원장·주문의 일관성을, B는 인물·계산·콘텐츠를 책임진다. 모듈 간 호출은 공개 service/port를 통한다.
- PostgreSQL은 지갑, 주문, 작업 처리 여부의 기준이다. Java 메모리 잠금만으로 여러 서버를 조정할 수 없다.
- 외부 결제와 우리 DB를 한 번에 롤백할 수 없다. 외부 성공/실패/불명을 기록하고 조회와 재처리로 최종 상태를 맞춘다.
- DB 트랜잭션은 짧게 유지한다. Liner·토스·알림톡 응답을 기다리는 동안 지갑 잠금이나 DB connection을 붙잡지 않도록 설계한다.

## 4. 돈이 움직이는 경계

### 충전

서버 상품 조회 → 주문에 원화 금액/유료·보너스 지급량 저장 → PG 결제 → 서버 승인·조회 → 지급분/원장/잔액/주문 CREDITED를 같은 DB 트랜잭션으로 저장한다.

브라우저의 성공 URL은 결제 완료 증거가 아니다. 승인 요청의 사용자, 주문, 금액과 PG 조회 결과를 검증한다. 승인 응답이 끊기면 결과 불명으로 보관하고 조회한다. 승인·webhook·재처리 job이 모두 도착해도 주문당 지급이 한 번만 일어나도록 DB 제약과 상태 전이를 함께 둔다.

### 구매와 복구

소유권·견적·현재 판매 상태 검증 → 구매 의도 등록 → 짧은 트랜잭션에서 지갑 잠금/lot 차감/원장 기록/작업 등록 → 외부 생성 → 결과 저장 또는 보상 작업.

지갑 차감과 구매 상태가 별도 트랜잭션이면 두 단계 사이의 중단을 처리할 영속 기록이 필요하다. 원장 수정으로 환급하지 않고 원거래 배분에 대응하는 역분개를 추가한다. 환급 성공과 실패를 구분하고, 실패는 작업으로 남겨 재처리와 관리자 확인이 가능해야 한다.

“같은 요청”과 “같은 주문”은 다르다. 같은 키+같은 본문은 같은 결과를 반환하고, 같은 키+다른 본문은 거절한다. 다른 요청 키라도 같은 quote/order로 돈이 두 번 움직이지 않아야 한다. DB unique 제약, 원자적 상태 전이, 원장 참조를 조합한다. 키 만료 정책도 결제·주문 보존 정책과 맞춘다.

## 5. 최신 코드에서 먼저 보완할 구체적 지점

아래는 실제 지갑을 연결하기 전에 다뤄야 할 코드상 위험이다. 운영 장애가 이미 발생했다는 뜻은 아니다.

1. **차감 후 서버 중단에 대한 재개 경로.** `GeneralReadingService`는 CREATED가 아닌 구매를 반환하고 종료한다(99행). `markDebited` 뒤 또는 GENERATING 상태에서 프로세스가 종료되면 재호출만으로 작업을 이어가지 않는다. 현재 저장소 목록에는 이를 회수하는 운영 worker가 없다. 구매 상태별 재개/보상 worker와 처리 lease를 설계한다. 수능 경로도 함께 확인한다.
2. **보상 실패가 작업으로 남지 않는 경로.** 같은 서비스는 생성 실패 뒤 보상 예외를 suppressed에 넣고 공통 생성 실패를 반환한다(150~157행). FAILED 상태에 남은 차감 거래를 찾아 재처리하고 알리는 경로가 필요하다. UI에는 서버가 REFUNDED를 확인한 경우에만 환급 완료를 표시한다.
3. **완료 요청 재조회와 견적 만료.** 구매 메서드는 기존 완료 구매를 찾기 전에 `validateQuote`를 호출한다. `CatalogService.getQuote`는 30분 만료를 거절한다(65행). 같은 완료 구매를 늦게 재전송하면 기존 성공 대신 QUOTE_EXPIRED가 날 수 있다. 소유권과 요청 일치를 검증한 뒤 기존 구매 결과를 반환하는 순서, 별도 구매 조회 API를 검토한다.
4. **현재 판매 상태 재검사.** 견적은 가격 스냅샷을 보존하지만 getQuote는 현재 상품 활성/판매 기간을 다시 확인하지 않는다. 실제 debit 서비스가 구매 시 판매 가능 여부를 확인해야 한다. FE의 버튼 비활성화만으로 보장하지 않는다.
5. **모듈 경계.** `SuneungPurchaseClaimService`는 catalog repository를 직접 사용한다. 팀 역할 문서의 공개 service/port 원칙과 다른 부분이다. 견적 점유와 구매 생성의 원자성을 보존하면서 공개 application 계약으로 정리할지 A/B가 결정한다.
6. **생성 시간과 처리 용량.** 생성 서비스는 최대 3회 호출 후 fallback을 수행하고 기본 Liner read timeout은 30초다. 순차 timeout이면 대략 90초 이상 걸릴 수 있어 실제 프록시 제한, lease, 중복 요청, 비용과 맞춰 확인한다. 운영에서는 작업 ID+조회 방식과 동시 작업 수 제한을 검토한다. 큐의 길이와 대기 시간도 측정한다.

코드 근거는 아래의 고정 커밋 링크를 사용한다.

- [GeneralReadingService](https://github.com/Yeonb0/saju-project/blob/381787b98bd536200865f4d54c342fd1271d563e/backend/src/main/java/com/sajuppugi/fortune/reading/application/GeneralReadingService.java#L99)
- [CatalogService](https://github.com/Yeonb0/saju-project/blob/381787b98bd536200865f4d54c342fd1271d563e/backend/src/main/java/com/sajuppugi/catalog/application/CatalogService.java#L58)
- [SuneungPurchaseClaimService](https://github.com/Yeonb0/saju-project/blob/381787b98bd536200865f4d54c342fd1271d563e/backend/src/main/java/com/sajuppugi/fortune/reading/application/SuneungPurchaseClaimService.java)
- [ReadingGenerationService](https://github.com/Yeonb0/saju-project/blob/381787b98bd536200865f4d54c342fd1271d563e/backend/src/main/java/com/sajuppugi/fortune/generation/application/ReadingGenerationService.java)

## 6. 실사용자와 서버 운영을 위한 확인 항목

### 읽는 방법과 준비

이 목록은 이미 모두 구현됐다는 뜻이 아니다. 세션, 실제 충전·차감, worker, 선물, 관리자 등은 구현 후 시험한다. 먼저 테스트 계정 A/B, 별도의 테스트 DB, 토스 테스트 키를 준비한다. 장애 재현과 부하 시험은 로컬 또는 staging에서 수행한다. 운영에서는 설정·지표·기존 데이터부터 읽기 전용으로 확인한다.

확인 기록에는 환경, 코드 버전, 테스트 데이터, 보낸 요청, 기대 결과, 실제 결과, orderId/purchaseId/traceId를 남긴다. 성공 JSON뿐 아니라 DB 상태와 작업 내역도 증거로 남긴다. 기록에는 비밀번호·세션 쿠키·원문 토큰·실제 개인정보를 넣지 않는다.

### 6-1. 로그인과 권한: 다른 사람의 데이터를 볼 수 없는가?

**쉽게 말하면:** 세션은 서버가 “이 요청은 A가 보냈다”를 기억하는 방식이다. 권한 검사는 “A가 이 주문이나 인물을 볼 수 있는가”를 따로 확인하는 것이다. 로그인 성공만으로 모든 자원 접근을 허용하면 안 된다.

**확인 방법:**

1. 서로 분리된 브라우저 프로필 또는 쿠키 저장소에서 A/B로 로그인한다. Postman도 A/B 쿠키가 섞이지 않게 사용한다.
2. A의 인물·주문·결과 ID를 기록한다. B의 로그인 상태로 해당 ID를 조회하고 수정·구매 요청도 보낸다.
3. 로그아웃 상태에서도 같은 API를 호출한다. 상태 변경 요청에서는 CSRF 토큰 누락·잘못된 토큰도 시험한다.
4. 로그인 상태에서 백엔드를 재시작한다. 여러 서버로 운영할 예정이면 같은 쿠키로 서버 1/2에 번갈아 요청해 결과를 확인한다.
5. 로그인 returnTo를 허용된 내부 경로와 외부 사이트 주소로 바꿔 본다. 실제 로그인 콜백은 브라우저로 확인한다.

**정상 결과:** 타인 조회·변경은 거절되고 개인정보가 응답에 없다. 미인증과 CSRF 오류가 계약에 맞게 반환된다. 외부 returnTo는 허용되지 않는다. 세션 저장 방식과 만료 정책이 분명하다. 재시작 시 로그아웃은 정책에 따라 가능하지만, 의도하지 않은 반복 로그아웃이나 서버별 로그인 상태 차이는 없어야 한다.

**현재 다음 개발:** OAuth·세션·CSRF 발급·인물 소유권 연결. 다중 인스턴스라면 메모리 세션에만 의존하지 않는 저장 방식도 정한다.

### 6-2. DB와 지갑: 동시에 구매해도 돈이 맞는가?

**쉽게 말하면:** 잔액 20개에서 15개짜리 구매 2건이 거의 동시에 들어와도 두 요청 모두 “20개가 있네”라고 판단해 성공하면 안 된다. DB 잠금은 같은 지갑을 바꾸는 작업 순서를 조정한다. 트랜잭션은 지갑·지급분·원장을 모두 저장하거나 모두 취소하는 경계다.

**확인 방법:**

1. 전용 테스트 fixture로 계정 A의 사용 가능 잔액 20개를 준비한다. 결제 원장을 무시한 직접 잔액 수정은 피한다.
2. 서로 다른 구매 의도와 quote로 15개 구매 2건을 동시에 시작한다. Postman에서 빠르게 두 번 누르는 것은 기초 확인일 뿐이다. 자동 통합 테스트에서 두 요청이 같은 시점에 시작하도록 맞춰 확실히 재현한다.
3. 동일 구매 요청을 같은 Idempotency-Key로 반복한다. 같은 키에 다른 본문도 보내고, 동일 quote에 다른 키도 보내 본다.
4. pgAdmin에서 지갑, wallet_lots, wallet_transactions, wallet_transaction_lines와 구매 상태를 함께 확인한다.
5. 저장 단계 중간에 오류를 넣어 트랜잭션이 일부만 남지 않는지 시험한다. 서버 종료처럼 트랜잭션 사이에서 끊기는 경우는 복구 worker 점검과 연결한다.
6. staging의 충분한 테스트 데이터에서 지갑 내역 SQL에 EXPLAIN을 사용해 조회 방법을 본다. 작은 테이블의 전체 조회(Seq Scan)는 정상일 수 있다. 데이터가 많아졌을 때 실제 지연과 인덱스 적합성을 판단한다.

**정상 결과:** 예시에서는 구매 1건 성공, 1건 잔액 부족, 최종 잔액 5개이며 차감 원장은 1건이다. 같은 구매 재전송은 추가 차감하지 않는다. 본문 불일치는 거절한다. 지급분·원장 배분·잔액·구매 상태가 서로 일치한다. 사용하는 잠금 순서가 일정하고 deadlock 또는 lock timeout 발생 시 제한된 재시도/오류 처리가 있다.

**직접 볼 SQL:** 아래는 읽기 전용 관찰 예시다. 실제 사용자 UUID로 바꾸고 업무 요청 전후를 비교한다. projection 잔액과 지급분 기반 조회는 만료 반영 시점에 차이가 날 수 있으므로 공식 잔액 계산 규칙으로 대사한다.

```sql
SELECT user_id, paid_balance, bonus_balance, version
FROM wallets
WHERE user_id = '실제-테스트-사용자-UUID'::uuid;

SELECT id, balance_type, remaining_amount, expires_at, created_at
FROM wallet_lots
WHERE wallet_user_id = '실제-테스트-사용자-UUID'::uuid;

SELECT id, type, total_amount, reference_id, reversal_of_id, created_at
FROM wallet_transactions
WHERE wallet_user_id = '실제-테스트-사용자-UUID'::uuid
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

**현재 다음 개발:** WalletPurchasePort DB 구현과 PostgreSQL 동시성 테스트. connection pool은 백엔드가 재사용하는 DB 연결 묶음이다. 서버 1개의 최대 연결 수가 아니라 모든 앱·worker의 연결 합계가 DB 한도를 넘지 않는지 확인한다. 느린 요청을 connection pool 대기/SQL/외부 API 시간으로 나눠 본다.

### 6-3. 토스와 Liner: 응답이 안 왔을 때 처리할 수 있는가?

**쉽게 말하면:** 응답을 받지 못한 것과 실제 처리가 실패한 것은 다르다. 토스에서는 승인됐지만 우리 서버가 응답을 못 받았을 수 있다. 이때 새 주문을 만들게 하면 이중 결제로 이어질 수 있다.

**확인 방법:**

1. 테스트 adapter 또는 테스트용 HTTP 서버로 정상 응답, 지연, 429(요청 과다), 5xx, 잘못된 응답 형식을 재현한다. 실제 provider에 고의로 과도한 요청을 보내지 않는다.
2. 토스 승인 성공 뒤 우리 서버에 응답을 전달하지 않는 상황을 시험한다. 주문 조회/대사로 PG 결과를 다시 확인할 수 있어야 한다.
3. 같은 승인 요청과 같은 결제 알림을 재전송한다. 지급 원장이 몇 건 생기는지 확인한다.
4. Liner 생성 응답을 지연시키고 제한 시간 이후 상태·재시도 횟수·fallback 사용 기록을 확인한다.
5. 외부 응답 대기 중 다른 사용자의 지갑 조회와 구매가 막히는지 본다. 지갑 잠금이나 긴 DB 트랜잭션 안에서 외부 호출을 기다리지 않는지도 코드를 읽는다.

**정상 결과:** 승인 결과 불명은 실패나 지급 완료로 단정하지 않는다. 서버 조회로 확인하고 주문당 한 번 지급한다. 재시도는 횟수·기한이 제한되고 재시도 가능한 오류만 대상으로 한다. Liner 실패는 정책에 따라 fallback 또는 환급 처리되며 구매가 영구히 확인 중에 머무르지 않는다.

**현재 다음 개발:** 토스 adapter, 결과 불명 대사, 중복 지급 방지. Liner 기존 재시도와 실제 프록시 제한 시간을 맞추고 비용·동시 생성 수를 제한한다.

### 6-4. worker: 서버가 꺼져도 미완료 작업을 다시 처리하는가?

**쉽게 말하면:** worker는 뒤에서 생성·알림톡·환급 같은 일을 처리하는 실행 프로그램이다. outbox는 “이 업무 뒤에 이 작업을 해야 한다”를 DB에 남긴 목록이다. lease는 한 worker가 일정 시간 작업을 맡았다는 표시다. 맡은 worker가 죽으면 다른 worker가 이어갈 수 있어야 한다.

**확인 방법:**

1. 구매/발송/환급 요청으로 작업이 DB에 등록됐는지 본다. 업무만 성공하고 작업 등록이 빠지지 않아야 한다.
2. 작업을 맡은 직후 로컬 worker 프로세스를 종료한다. lease 만료 뒤 worker를 재시작해 작업이 재개되는지 본다.
3. worker 2개를 같은 테스트 DB에 연결한다. 같은 작업을 동시에 보더라도 한 worker만 점유하거나, 재실행돼도 최종 지급·환급이 중복되지 않는지 확인한다.
4. 첫 worker를 오래 멈춰 lease를 만료시키고 두 번째 worker가 점유한 뒤 첫 worker의 늦은 완료를 전달한다. 현재 점유 token/version과 일치하는 완료만 반영되는지 확인한다.
5. 항상 실패하는 테스트 작업을 하나 넣는다. 다른 작업이 계속 처리되는지, 실패 작업이 재시도 한도 뒤 별도 실패 목록과 알림으로 이동하는지 확인한다.

**정상 결과:** 프로세스가 종료돼도 작업 기록은 남고, 재개 또는 보상으로 수렴한다. 외부 요청은 중복 전달될 수 있으므로 PG 멱등 처리와 DB 중복 방지를 함께 사용한다. 운영자가 특정 작업의 실패 사유를 보고 안전하게 재처리할 수 있다.

**현재 다음 개발:** 실제 outbox 저장/worker, 상태별 구매 복구·환급 재처리. 현재 계약만 있는 부분은 먼저 구현해야 한다.

### 6-5. 부적과 선물: 링크를 가진 사람에게 무엇을 허용하는가?

**쉽게 말하면:** 선물 링크는 로그인 없이 접근할 수 있는 권한이 될 수 있다. 원문 token이 로그나 분석 URL에 남으면 그 기록을 보는 사람이 선물에 접근할 수 있다. 부적 파일 URL에도 공개/비공개 정책이 필요하다.

**확인 방법:**

1. 테스트 선물 링크를 발급하고 정상 수신 흐름을 확인한다. 선물 수신자는 비로그인 사용이 허용될 수 있으므로 일반 계정 소유권 정책과 구분한다.
2. token의 한 글자를 바꾸거나 만료시킨 뒤 호출한다. 시간 변경은 서버 시간 전체를 바꾸기보다 테스트 Clock 또는 fixture로 재현한다.
3. 링크를 재발급한 뒤 새 링크와 예전 링크를 모두 연다. 재발급이 이용기한을 연장하지 않는지도 본다.
4. 다른 계정으로 비공개 부적/보관함을 조회하고, R2 원본 주소와 signed URL을 각각 시험한다. signed URL은 정해진 시간까지 접근을 허용하는 링크다.
5. DB와 로그에는 token hash만 남는지, 관측 URL의 token/쿼리 문자열이 제거되는지 본다. 이미 생성한 signed URL의 별도 유효기간도 정책에 포함한다.

**정상 결과:** 일반 비공개 자원은 소유자만 접근하고 선물은 정의된 token 권한으로만 접근한다. 잘못되거나 만료·폐기된 token은 거절한다. 구 링크는 재발급 정책대로 차단되며, 원문 token과 개인 정보가 로그에 남지 않는다.

**현재 다음 개발:** 선물 token 저장·폐기, R2 권한·signed URL, 렌더/보관함 연결.

### 6-6. 배포와 DB 변경: 새 버전을 올려도 기존 서비스가 동작하는가?

**쉽게 말하면:** 앱 코드를 이전 버전으로 돌려도 이미 바뀐 DB가 자동으로 이전 구조로 돌아가지는 않는다. 테이블 칸을 바로 지우거나 이름을 바꾸면 아직 실행 중인 이전 서버가 오류를 낼 수 있다.

**확인 방법:**

1. local/staging/production의 DB 주소와 PG 키가 각각 다른지 확인한다. 값 전체를 로그에 출력하지 않고 환경별 대상만 검증한다.
2. 테스트 데이터를 넣은 staging DB에 새 Flyway migration을 적용하고 이력과 success를 확인한다. 이미 공유된 migration 파일은 고치지 않는다.
3. 이전 앱 버전과 새 앱 버전을 해당 DB에 연결해 핵심 조회·결제 상태 확인이 되는지 시험한다.
4. 변경은 필요할 때 “새 칸 추가 → 구/신 코드 호환 → 데이터 채우기 → 새 칸으로 전환 → 나중에 구 칸 제거” 순서로 나눈다.
5. health/readiness를 확인한 뒤 로그인·주문·지급·결과 조회의 간단한 실제 흐름을 확인한다. health=UP만으로 결제 기능이 정상이라고 판정하지 않는다.
6. 새 앱이 실패했을 때 이전 버전으로 복귀해 핵심 경로가 동작하는지 staging에서 연습한다.

**정상 결과:** 새 배포가 테스트 데이터를 망가뜨리지 않고, 정해진 배포 순서 동안 구/신 버전이 호환된다. 복귀 방법과 복귀 가능한 범위가 기록돼 있다. FE 운영 배포에서는 mock이 켜지지 않는다.

**현재 다음 개발:** 실제 staging, 배포 환경 설정과 변경·복귀 절차. PostgreSQL migration 자체는 이미 있다.

### 6-7. 백업과 복원: DB가 사라졌을 때 실제로 되돌릴 수 있는가?

**쉽게 말하면:** “백업 설정이 켜졌다”와 “그 백업으로 서비스를 복구했다”는 다르다. DB에 부적 파일 주소만 있고 R2 파일이 없으면 DB 복원만으로 부적을 되찾을 수 없다.

**확인 방법:**

1. DB 제공자의 백업 주기·보관 기간·실패 알림을 확인한다. 파일 저장소와 암호화 키도 복구 대상인지 정한다.
2. 테스트 회원·주문·원장·부적을 만든 시점을 기록하고 백업한다.
3. 원본 운영 DB를 덮어쓰지 않고 새 테스트 DB에 복원한다. 앱을 복원 DB에 연결해 회원·주문 조회와 원장 합계를 확인한다.
4. R2 파일 조회, 암호화된 개인정보 복호화, 세션/작업 상태의 처리 정책도 확인한다.
5. 복원에 걸린 시간과 마지막 백업 뒤 빠진 데이터 범위를 기록한다.
6. 결제 이력이 백업보다 최신일 수 있으므로 복구 후 PG 조회와 원장 대사 절차를 시험한다. 복원 DB에 운영 키와 worker를 바로 연결해 재지급·재발송이 시작되지 않도록 복구 순서를 정한다.

**정상 결과:** 독립된 복원 환경에서 데이터와 파일을 실제로 조회할 수 있다. RPO(최대 얼마 동안의 데이터 손실을 허용하는지), RTO(몇 시간 안에 복구할지)를 정하고 실측값과 비교한다. 예를 들어 하루 한 번 백업만 있으면 최근 하루 데이터가 빠질 수 있다는 점을 이해해야 한다.

**현재 다음 개발:** 실제 플랫폼의 백업 확인, 별도 DB 복원 연습과 PG 대사 절차.

### 6-8. 로그와 운영 도구: 사용자 문의 한 건을 추적할 수 있는가?

**쉽게 말하면:** 사용자가 “돈은 냈는데 충전이 안 됐어요”라고 할 때 주문 ID로 PG 승인, 우리 주문 상태, 지급 원장, 실패 작업을 연결해 볼 수 있어야 한다. traceId는 한 요청의 기록을 묶는 값이고 orderId/purchaseId는 여러 요청에 걸친 업무를 묶는 값이다.

**확인 방법:**

1. 토스 승인 성공 후 지급 실패 상황을 테스트로 만든다. 사용자가 받은 주문 ID를 기록한다.
2. 그 ID만으로 서버 로그, DB 주문, 지급 원장, PG 조회 결과, 재처리 작업을 찾아본다.
3. 정해진 처리 시간보다 오래 미지급/미환급 상태인 작업에 알림이 오는지 확인한다. 알림 기준은 실제 정상 처리 시간으로 정한다.
4. 권한 있는 관리자만 재처리할 수 있는지, 권한 없는 계정은 거절되는지 본다.
5. 같은 재처리 버튼을 두 번 눌러도 한 번만 지급/환급되는지 확인한다. 누가 언제 어떤 사유로 재처리했는지 감사 기록을 본다.

**정상 결과:** 문의 ID로 처리 경위를 설명하고 해결할 수 있다. 개인정보 원문 없이 추적 가능하고, 관리자 조작이 권한·멱등성·감사 기록으로 보호된다.

**현재 다음 개발:** 결제/구매 ID 중심 로그, 미완료 대사 알림, 관리자 권한과 감사. traceId 기반 일부는 이미 있다.

### 6-9. 개인정보: 수집한 값이 어디로 이동하고 언제 지워지는가?

**쉽게 말하면:** 생년정보가 DB에는 안전하게 저장돼 있어도 서버 로그, Sentry, 분석 URL, 외부 생성 요청에 그대로 남으면 다른 곳에서 유출될 수 있다. 데이터가 지나가는 전체 경로를 확인해야 한다.

**확인 방법:**

1. 실제 사람 정보 대신 쉽게 검색할 수 있는 가상 이름·가상 전화번호 등 표시값을 테스트 입력에 넣는다.
2. 브라우저 저장소와 Network, 서버 로그, Sentry 이벤트, PostHog 이벤트, outbox payload에서 표시값을 검색한다.
3. Liner로 보내는 내용을 읽어 원문 생년정보·이름·연락처 대신 필요한 계산 facts만 보내는지 본다.
4. 운영자가 필요한 DB 필드의 암호화와 키 보관 방식을 확인한다. 선물 token은 해시 조회, 민감 outbox는 암호화 등 데이터별 목적에 맞게 구분한다.
5. 인물 삭제/탈퇴를 실행하고 DB, 캐시, 파일, 대기 작업에 무엇이 남는지 확인한다. 결제·감사 등 정책상 보존 대상과 삭제 대상을 분리한다.
6. 백업에 남는 정보의 접근·보존·만료와 복원 뒤 삭제 요청 재반영 절차도 기록한다. 정책 페이지의 수집/위탁/보존 설명과 실제 처리가 일치하는지 PD와 맞춘다.

**정상 결과:** 필요한 곳에 필요한 값만 남고 로그·분석·외부 전송에 불필요한 원문이 없다. 암호화 키는 데이터와 분리해 관리한다. 삭제/보존 정책을 설명할 수 있다.

**현재 다음 개발:** 인물 정보 저장·암호화·삭제, 실제 외부 요청 검증과 보존 기간 확정. FE URL 마스킹과 수집 최소화 기반은 이미 있다.

### 6-10. 사용자 수가 늘어날 때: 어느 부분부터 느려지는가?

**쉽게 말하면:** 가입자가 많아도 모두 동시에 구매하지는 않는다. 서버 용량은 피크 요청 수, 요청 한 건의 처리 시간, 외부 API 한도에 따라 정한다. 30초 걸리는 생성이 초당 2건 들어오면 안정된 흐름에서 평균 약 60건이 동시에 진행될 수 있다. 설명용 예시이며 프로젝트 실측치가 아니다.

**확인 방법:** staging과 fake 외부 adapter에서 동시 요청 수를 점진적으로 늘린다. 로그인·단순 조회·충전·운세 생성 경로를 구분해 측정하고, 실제 Liner/PG는 별도 소량 검증으로 한도와 실제 지연을 확인한다. API 응답 시간뿐 아니라 DB connection 대기, SQL 시간, job 대기, 외부 호출 시간, 메모리와 오류율을 함께 본다.

**정상 결과:** 혼잡 시 요청을 무한히 쌓지 않고 정해진 동시 작업 수·대기열·요청 제한으로 제어한다. 돈이 움직이는 요청은 과부하 중에도 중복 지급·차감을 만들지 않는다. p95는 요청 100건 중 약 95건이 그 시간 안에 끝난다는 지표다. 목표와 알림 임계치는 실제 측정 후 정한다.

### 지금부터 확인할 순서

1. 지금: 로컬 PostgreSQL 실행, Flyway 테이블 확인, Postman health 응답 확인. local/staging/production의 역할 구분.
2. 로그인 구현 후: 6-1. 지갑 구현 후: 6-2. 토스 연결 후: 6-3.
3. 생성/환급 worker 구현 후: 6-4. 부적/선물 구현 후: 6-5.
4. 출시 전: 6-6~6-10과 전체 경로 E2E. 최종 점검 결과는 미확인 항목도 포함해 기록.

## 7. 바이브 코딩 작업 규칙

- 기능을 요청할 때 정상 흐름과 함께 “중복 요청, 타임아웃, 서버 중단, 타인 접근 때 어떻게 되는지”를 명시한다.
- AI에게 먼저 변경할 모듈, 읽고 쓰는 테이블, 트랜잭션 범위, 외부 호출 위치, 실패 상태를 설명하게 한 뒤 구현한다.
- 돈·권한·개인정보·DB migration 변경은 diff를 직접 이해하고 상대 BE가 검토한다. 이해할 기준은 파일 수가 아니라 장애 시 돈과 데이터가 어디에 남는지다.
- 미구현 port를 성공하는 가짜로 채워 운영에 연결하지 않는다. mock과 실제 adapter의 배포 조건을 확인한다.
- 기존 Flyway 파일을 수정하지 않고 새 migration을 추가한다. 데이터 삭제나 환급을 원장 직접 UPDATE로 해결하지 않는다.
- 테스트 개수보다 보장 내용을 본다. 결제/원장에는 PostgreSQL 동시성, 재전송, 프로세스 중단을 포함한 테스트가 필요하다.
- secrets는 배포 환경에 등록한다. 디버깅 편의를 위해 생년정보·토큰·PG 키를 로그나 프론트 환경변수에 넣지 않는다.
- PR마다 API/OpenAPI/정책/FE adapter의 변경을 함께 확인한다. 한 번에 큰 기능 여러 개를 생성하면 상태 전이와 리뷰가 어려워진다.

다음 개발 지시문 예시:

> WalletPurchasePort의 실제 차감·보상 구현을 진행한다. 먼저 quote, purchase, wallet, lot, ledger의 트랜잭션 경계와 잠금 순서를 설명한다. 동일 quote에 다른 요청 키가 들어와도 한 번만 차감하고, 같은 지갑의 동시 구매는 잔액을 넘지 않게 한다. 원거래 배분을 기준으로 한 번만 보상한다. 차감 직후 프로세스가 종료될 때의 복구 기록과 재개 경로를 설계하고 PostgreSQL 테스트로 검증한다. 외부 API를 지갑 잠금 안에서 호출하지 않는다. 미확정 환불 정책은 구현 전에 목록으로 제시한다.

## 8. 권장 읽기 순서와 출처

1. `backend/docs/BACKEND_ROLE_SPLIT.md`: 누구의 모듈이 무엇을 소유하는지.
2. `docs/ERD.md`: 돈·주문·결과의 연결과 DB 제약.
3. `WalletPurchasePort`, `WalletRepository`, wallet migration: 어떤 변경이 함께 커밋돼야 하는지.
4. `TopUpOrder`, `TopUpUseCase`, `PaymentProviderPort`: PG 승인과 우리 지급의 차이.
5. `GeneralReadingService`, `SuneungReadingService`: 차감·생성·복구 순서.
6. `SecurityConfig`, `frontend/next.config.ts`: 세션/CSRF와 실제 프록시 경로.
7. `IdempotencyStore`, `OutboxPort`: 중복과 중단을 DB에 남기는 방법.

- [BE-A PR #14](https://github.com/Yeonb0/saju-project/pull/14), [BE-B PR #15](https://github.com/Yeonb0/saju-project/pull/15), [FE PR #16](https://github.com/Yeonb0/saju-project/pull/16)
- [FE PROGRESS](https://github.com/Yeonb0/saju-project/blob/381787b98bd536200865f4d54c342fd1271d563e/frontend/docs/PROGRESS.md), [Backend README](https://github.com/Yeonb0/saju-project/blob/381787b98bd536200865f4d54c342fd1271d563e/backend/README.md)
- [PostgreSQL 17 잠금 문서](https://www.postgresql.org/docs/17/explicit-locking.html): 행 잠금의 지속 범위, 잠금 순서와 deadlock 확인.
- [토스 결제창 연동](https://docs.tosspayments.com/guides/v2/payment-window/integration), [토스 API](https://docs.tosspayments.com/reference): 승인 검증·조회·취소와 멱등 처리 기준 확인.
- [Postman 요청·응답 문서](https://learning.postman.com/docs/sending-requests/requests/): API 클라이언트의 역할.
- [pgAdmin 서버 연결](https://www.pgadmin.org/docs/pgadmin4/latest/connect_to_server.html), [Query Tool](https://www.pgadmin.org/docs/pgadmin4/latest/query_tool.html): DB에 직접 연결해 SQL로 확인하는 방법.
- [PostgreSQL EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html): SQL 실행 계획을 읽는 방법.

## 9. 개발·검증 기록

### 2026-10-09: PR 전 A/B/FE 연결과 최초 질문 재점검

- fetch 후 main 381787b, B 원격 81410c3, FE 원격 0ab2472와 로컬 2b09ab7을 대조했다. B backend는 main과 같고 FE 추가 커밋은 목업 보완이다. push/PR/배포는 하지 않았다.
- 점검 적용: 6-1 인증/인물/소유권, 6-2 HTTP 멱등성과 별도 커밋, 6-4 구매 상태/복구, 6-6 기본 비활성, 6-8 완료 상태 확인, 6-9 정책/개인정보 계약.
- 발견: FE 생성 오류만으로 환급 완료 표시, 처리 중 구매의 null/완료 전 이동 위험, 원본 HTTP 키 미보존, 만료 후 완료 재조회 및 중단 복구 미완료, 수능 구조화 sections 불일치, basic/구매의 절기 오류 code 차이. 상세 근거와 최초 질문 전체 판정은 backend/docs/PRE_PR_INTEGRATION_REVIEW_20261009.md.
- 기존 인계 문서의 '수능 sections 배열 유지'는 FE 구조화 계약 일치로 해석하면 안 되므로 정정했다. FAILED worker는 추가됐지만 소유자 상태 조회 HTTP는 아직 없음을 갱신했다.
- 이번 H2 관련 5개 suite 재실행: 총 44건 모두 통과, 실패/오류/제외 0. 일반/수능 HTTP 테스트의 지갑/인물/Liner는 mock이며 실제 A+B 전체 구매 통합 또는 FE 브라우저 E2E 검증이 아니다. PostgreSQL/PG/live Liner/운영은 이번에 재실행하지 않았다.
- Q-34 대부분 로컬 구현(충전 지급량 HTTP 제외), Q-35 부분 해결/불일치, Q-33/Q-36 제품/운영 결정 대기. '전체 FE 질문 해결'이나 '실사용 연결 완료'로 표시하지 않는다.

### 2026-10-09: FE 견적·지갑·오행 입력 계약

- 작업 기준: main `381787b`, 로컬 `codex/fe-quote-wallet-contract`. 앞의 1~5장 구현 현황은 이 작업 전 평가이며 아래 항목이 최신 변경이다.
- Q-34: 일반 5종/수능 견적에 자금 필드 연결, charged 통일, 소유자 견적 GET, 유료·보너스 지갑 GET, 500+80=580 문서 수정.
- Q-35: personId 오행 입력과 원문 입력의 상호 배타 검증, 기존 소유자 port 사용, OpenAPI JSON export와 nullable/견적 DTO 구분.
- 적용 점검: 6-1 소유권/미인증/CSRF, 6-2 조회 시 원장·견적 미변경/현재 사용 가능 잔액, 6-9 생년정보·이름·context hash 미노출. 이번 작업은 지갑 쓰기·PG 호출·새 migration을 추가하지 않았다.
- 로컬 전체 `check bootJar exportOpenApi` 성공. 기본 테스트는 H2 PostgreSQL 호환 모드이며 243건 중 실패 0, 오류 0, 제외 2건이다. 제외는 opt-in 실제 Liner 호출과 명시적 OpenAPI export 테스트이며 export task에서 후자는 따로 실행해 성공했다.
- OpenAPI schema의 nullable 필드와 충돌 없는 DTO를 실제 export 결과 및 통합 테스트로 확인했다. API 테스트의 인증/인물 데이터는 테스트 전용 대체값이다.
- 미확인: Docker 엔진 기동을 시도했으나 연결되지 않아 이번 변경의 실제 PostgreSQL 재실행은 못 했다. 배포 전 Backend CI PostgreSQL 검증이 필요하다. 운영 서버·실제 OAuth·실제 FE E2E·충전 승인/지급·다중 서버 부하를 확인한 것은 아니다.
- 남은 구현/결정: 실제 인물·세션 adapter, 지갑 구매·복구 worker, 충전 주문/승인 HTTP 및 지급량 응답, productName/정가 metadata, Q-33/Q-36 제품·정책 결정. 상세 인계는 `backend/docs/FE_REQUESTS_20261009.md`.

### 2026-10-09: 분리된 PostgreSQL 테스트 및 실제 Liner 연결

- 적용 점검: 6-2 실제 PostgreSQL 검증, 6-6 개발/테스트 DB 분리 및 migration, 6-9 외부 테스트 입력 최소화와 비밀값 미출력.
- Docker 엔진 접근은 권한 있는 실행에서 정상 확인했다. 기존 개발 DB는 localhost:5432이며 수정/중지하지 않았다.
- `backend/compose.test.yml`을 추가했다. 별도 Compose project `saju-tests`, localhost:55432, DB/계정 `sajuppugi_test`, 개발 볼륨과 분리된 tmpfs를 사용한다. 컨테이너 중지 시 테스트 데이터는 사라진다. 개발/운영 DB는 테스트 연결 대상으로 사용하지 않는다.
- SQL로 PostgreSQL 17.11과 테스트 DB/계정을 확인했다. Flyway migration 10개 모두 success=true였다.
- 테스트 DB 환경변수를 지정한 `check bootJar --rerun-tasks` 성공: 총 243건, 실패 0, 오류 0, 제외 2, 통과 241. 기본 테스트 외부 Liner는 fake다. 제외 항목은 실제 Liner opt-in과 OpenAPI export다.
- 이어서 실제 Liner smoke test 1건을 명시적으로 실행해 통과했다. 가상 일반 문장 요청이며 실제 개인정보/결제는 사용하지 않았다. 키는 출력하지 않았고 `.env`와 응답 산출물은 Git ignore 대상임을 확인했다.
- 실제 Liner smoke는 키/연결/일반 chat completion 응답 검증이다. 운세 facts/section의 실제 생성, 재시도/시간 초과, 실제 차감·환급·FE E2E를 모두 검증했다는 의미는 아니다.
- 앞선 기록의 Docker/PostgreSQL 미확인 상태는 이번 분리 테스트로 해소됐다. 다중 서버·동시 차감·PG·운영 부하 검증은 여전히 미완료다.
- 다음 단계: BE-B 담당 실제 인증·세션/인물 저장소 계약 연결, BE-A 실제 지갑 차감·복구 구현 및 PostgreSQL 동시성 검증. 미정 정책은 팀 확정 없이 구현하지 않는다.

### 2026-10-09: A 지갑 쓰기 내부 adapter, 기본 비활성

- 단계 범위: `WalletPurchasePort`의 실제 지급분 차감/원장/잔액 갱신/영속 재전송/원거래 전체 복구. 충전 지급·토스·선물·자동 구매 복구 worker는 이번 변경에 포함하지 않았다.
- 적용 점검: 6-1 견적 소유권, 6-2 트랜잭션/잠금/동시 구매/중복 원장/배분, 6-4 복구 경계와 미완료 작업, 6-6 새 migration과 활성화 제한, 6-9 요청 키 해시 저장. 지급분 차감 순서는 확정 P-02A를 사용한다.
- wallet -> lot(ID 순) 잠금 아래에서 지급분 변경·원장 및 배분·projection·견적별 receipt·키별 receipt를 한 독립 트랜잭션으로 커밋한다. 외부 생성/결제 호출은 없다. 새 차감은 소유권·견적 만료·현재 판매 가능 상태를 재검사하며 가격은 견적 snapshot을 유지한다.
- `V202610091800`은 3개 신규 receipt/command 테이블과 unique/FK를 추가한다. 기존 공유 migration을 수정하지 않았다. 테스트 PostgreSQL의 migration 11개 모두 success=true 확인.
- 분리된 PostgreSQL 17.11에서 `check bootJar --rerun-tasks` 성공: 총 257건, 통과 255, 실패/오류 0, 선택 제외 2. 신규 WalletPurchasePersistenceTest 14건은 모두 통과했다.
- 실제 독립 DB 트랜잭션 2개 동시 실행: 잔액 20에서 15 구매 2건 중 한 건만 성공, 최종 잔액 5/차감 원장 1건. 동일 견적·서로 다른 키는 같은 차감 거래 반환. 동시 복구도 역분개 1건/잔액 20으로 일치했다.
- 차감 및 복구의 마지막 command 저장에 예외를 주입해 지급분·원장·projection·receipt가 함께 롤백됨을 확인했다. 복구 재시도는 한 번만 기록했다. 별도 서비스 인스턴스에서도 DB receipt를 재사용했다. OS 프로세스 강제 종료나 자동 worker 재개를 시험한 것은 아니다.
- 복구는 최초 lot 배분을 역분개하고 기존 만료를 유지한다. 복구 시 이미 만료된 lot의 사용 가능 잔액은 0이다. 만료 경계에서 새 지급분 발급/기한 연장 정책은 제품 확정 및 추가 구현 전까지 제공하지 않는다.
- `WALLET_PURCHASES_ENABLED=false` 기본 유지. 실제 인물/인증과 구매 복구 경로 및 A/B 리뷰가 끝나기 전 운영에서 활성화하지 않는다. 테스트에서만 명시적으로 켰다. 독립 공개 차감 HTTP API를 만들지 않았다.
- 남은 위험: B 구매 상태 변경과 지갑은 별도 커밋이며 DEBITED/GENERATING/FAILED 자동 재개/복구 worker가 없다. B가 전달하는 키는 구매 ID 기반 내부 키여서 클라이언트 원본 키/본문 불일치 계약을 아직 보장하지 않는다. 영속 receipt 보존 정책, 운영 잠금 대기/재시도, 대사/알림, 실제 FE/PG E2E도 후속이다.
- 다음 개발 순서는 `backend/docs/WALLET_PURCHASE_IMPLEMENTATION.md`에 기록했다. 운영 서버 배포와 실제 개발 DB 데이터 변경은 수행하지 않았다.
- H2 기본 경로에서도 전체 257건 중 통과 255, 실패/오류 0, 제외 2를 확인했고 `check bootJar exportOpenApi --rerun-tasks`가 성공했다. H2에서 드러난 테스트 종료 정리의 자기참조 FK 삭제 순서는 복구 거래 -> 원거래 순으로 수정했다. OpenAPI export는 별도 task에서 실행됐다.

### 2026-10-09: FAILED 구매 복구 재처리와 기능별 커밋

- 기존 변경을 FE 계약(`25fb0e8`), 테스트 DB(`14a8297`), 지갑 차감/복구(`72b0541`)의 로컬 커밋으로 나눴다. 푸시/PR/배포는 하지 않았다. AGENTS에 검증 후 기능별 로컬 커밋 지침을 기록했다.
- 적용 점검: 6-2 복구 원장/상태 일치, 6-4 재처리와 커밋 사이 중단 경계, 6-6 신규 migration/기본 비활성, 6-8 구매 ID/오류 코드 기반 운영 기록, 6-9 개인정보/키 미출력.
- FAILED 구매만 잠그고 A의 소유자/견적/원거래 검증 port로 보상한다. 복구 원장은 독립 커밋되며 구매 상태 저장 실패 시 다음 실행에서 같은 역분개를 재사용한다. 즉시 보상과 worker가 겹친 REFUNDED 상태 갱신도 멱등 처리했다.
- 신규 V202610091900은 재시도 횟수/다음 시각/오류 코드를 추가한다. PostgreSQL migration 12개 모두 성공 확인. 기본 총 5회 시도, 실패 시 30초부터 배수 backoff(최대 10분), 한도 초과는 FAILED로 남고 자동 재시도에서 제외된다. 운영 알림 연동과 관리자 재처리 API는 후속이다.
- 분리 PostgreSQL 17.11 전체 `check bootJar --rerun-tasks` 성공: 작업트리 총 269건(직접 실습 opt-in 포함), 통과 266, 실패/오류 0, 제외 3. 제외는 실제 Liner, OpenAPI export, walletDemo다. 복구 테스트 11건 모두 통과했다.
- 테스트는 독립 트랜잭션의 동시 worker, 커밋된 복구 후 상태 저장 예외, 복구 실패 후 재시도, 한도 초과, null 복구 응답 거절, 소유자/견적 연결 불일치, 후보 조회의 시각/상태/횟수 필터를 확인했다. scheduler poll은 테스트에서 명시적으로 호출했다. 실제 background 시간 경과나 OS 강제 종료/다중 프로세스 운영을 시험한 것은 아니다.
- 반복 PostgreSQL 실행에서 기존 CatalogPersistenceTest의 전역 견적 개수 가정이 드러나 테스트 사용자 범위로 좁혔다. 다른 사용자 데이터가 존재해도 해당 거절 요청이 견적을 만들지 않았는지를 검사한다.
- WALLET_PURCHASES_ENABLED/WALLET_RECOVERY_ENABLED는 모두 기본 false다. CREATED 차감 커밋 후 중단 및 DEBITED/GENERATING 재개·lease/fencing은 미완료이므로 운영 구매는 여전히 비활성 유지한다. 실제 인증/인물·HTTP 원본 키/본문 멱등성·PG·FE E2E도 미완료다.
- 복구는 purchase -> wallet -> lot 잠금 순서이며 독립 지갑 트랜잭션에 추가 DB connection이 필요하다. 운영 활성화 전 pool 용량·worker 동시 수·잠금 대기·알림을 검증한다. 외부 Liner/PG 호출은 이 트랜잭션에 없다.
- 복구 기능은 `063b483`으로 별도 로컬 커밋했다. H2에서도 전체 269건 중 통과 266, 제외 3, 실패/오류 0이며 `check bootJar exportOpenApi --rerun-tasks` 성공을 확인했다.

### 2026-10-09: 직접 PostgreSQL 실습과 읽기 전용 확인

- `walletDemo`는 명시 실행 시에만 전용 localhost:55432/sajuppugi_test에 가상 시작 지급분/원장을 만들고 실제 내부 차감·복구 명령을 호출한다. 실행 전 TEST_DATABASE_URL을 검사하고 테스트 Spring 연결도 해당 DB로 고정했다. 일반 test에서는 제외한다.
- demo 1건 성공, 가상 잔액 20 -> 5 -> 20, 동일 구매 재전송의 추가 차감 없음, 확정 실패를 가정한 구매 상태 전이 및 복구 후 REFUNDED를 확인했다. 시작 TOP_UP은 fixture이고 실제 토스 승인은 아니다. 실제 Liner 생성도 호출하지 않았다.
- `docs/sql/wallet-demo-inspect.sql`을 BEGIN READ ONLY에서 실행했다. 지갑 paid=20/bonus=0/version=2, lot remaining=20, 현재 사용 가능=20, TOP_UP +20/PURCHASE -15/REFUND +15 각 1건, 거래 총액=배분 합계, REFUND가 원차감 거래 참조, 구매 REFUNDED/recovery_attempts=1을 확인했다.
- `build/wallet-demo.json`은 마지막 실습의 가상 ID/잔액만 기록하며 Git 제외 대상이다. 실행/접속/정리/pgAdmin/직접 SQL 방법은 `backend/docs/POSTGRESQL_TEST_GUIDE.md`에 기록했다.
- 사용자가 바로 읽어볼 수 있도록 실습 테스트 컨테이너와 가상 행을 남겼다. 개발 DB는 건드리지 않았다. 컨테이너 down 시 임시 데이터가 사라지고 예제 lot은 약 1시간 뒤 만료된다. 다시 전체 테스트할 때는 test project만 down/up해 초기화한다.
