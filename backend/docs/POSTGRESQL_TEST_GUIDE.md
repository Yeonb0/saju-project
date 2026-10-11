# PostgreSQL 직접 테스트 안내

## 왜 권장하는가?

Postman 성공 응답만으로는 원장이 한 번만 기록됐는지, 지급분이 정확히 줄었는지 알 수 없다.
H2만으로 PostgreSQL의 잠금·동시성·외래키 동작을 완전히 검증할 수도 없다.
따라서 자동 PostgreSQL 테스트와 SQL 읽기 확인을 함께 한다. SQL로 잔액을 직접 수정해
성공하게 만드는 것은 검증 방법이 아니다. 운영/개발 DB에는 아래 테스트를 실행하지 않는다.

참고: [PostgreSQL 17 행 잠금](https://www.postgresql.org/docs/17/explicit-locking.html).

## 1. 전용 DB 실행

새 PowerShell에서 backend 폴더로 이동한다. 기존 bootRun 터미널은 그대로 둔다.

```powershell
cd 'C:\Users\최지우\Documents\ChatGPT\26데모데이 4\.codex-saju-review\backend'
docker compose -p saju-tests -f compose.test.yml down
docker compose -p saju-tests -f compose.test.yml up -d --wait test-db
$env:TEST_DATABASE_URL = 'jdbc:postgresql://localhost:55432/sajuppugi_test'
$env:TEST_DATABASE_USERNAME = 'sajuppugi_test'
$env:TEST_DATABASE_PASSWORD = 'local-test-only'
Remove-Item Env:LINER_LIVE_TEST -ErrorAction SilentlyContinue
```

- 개발 DB는 5432, 테스트 DB는 55432다. 혼동하지 않는다.
- TEST_DATABASE 변수는 이 PowerShell의 테스트 프로세스에만 전달된다.
- tmpfs 테스트 DB는 컨테이너 중지/제거 시 데이터가 사라진다.
- 첫 down은 이전 테스트/실습 데이터만 초기화한다. 보존할 실습 결과는 먼저 확인한다.

## 2. 자동 테스트

```powershell
.\gradlew.bat --no-daemon check bootJar --rerun-tasks
```

전체 테스트 대신 지갑 및 실패 구매 복구만 확인하려면:

```powershell
.\gradlew.bat --no-daemon test --rerun-tasks --tests 'com.sajuppugi.WalletPurchasePersistenceTest' --tests 'com.sajuppugi.FailedPurchaseRecoveryTest'
```

`BUILD SUCCESSFUL`과 `build/reports/tests/test/index.html`의 실패/제외 항목을 함께 본다.
일반 테스트는 가상 계정만 사용하고 종료 시 자신의 데이터를 정리한다. 따라서 SQL에서
테스트 구매가 보이지 않는 것은 정리됐기 때문일 수 있다. 실제 Liner/PG를 호출하지 않는다.

## 3. 읽어볼 예제 데이터 남기기

자동 테스트가 끝난 후 아래 명령을 실행한다. 데이터가 남는 실습은 별도 opt-in task다.

```powershell
.\gradlew.bat --no-daemon walletDemo
Get-Content build/wallet-demo.json
```

이 task는 TEST_DATABASE_URL이 정확히 로컬 55432의 sajuppugi_test인지 먼저 확인한다.
Spring 연결도 해당 테스트 DB로 고정한다. 일반 테스트에서는 실행하지 않는다.
가상 시작 지급분 20개와 TOP_UP 원장을 fixture로 만들고, 실제 내부 지갑 명령으로
15개 차감, 동일 요청 재전송, 확정 생성 실패를 가정한 상태 변경, 복구 명령을 수행한다.
실제 원화 충전, 실제 운세/Liner 생성, HTTP 로그인 전체 흐름을 시험하는 것은 아니다.

JSON의 기대값은 balanceBefore=20, balanceAfterDebit=5, balanceAfterRecovery=20,
purchaseStatus=REFUNDED다. userId/quoteId/purchaseId는 가상 ID이며 실행마다 다르다.
재실행은 새 가상 계정을 추가하고 JSON에는 마지막 실행 ID가 기록된다.
예제 lot은 약 1시간 뒤 만료된다. 시간이 지나면 사용 가능 잔액은 다시 줄 수 있다.

## 4. PostgreSQL에 연결해 직접 읽기

추가 PostgreSQL 설치 없이 컨테이너의 psql을 사용할 수 있다.
아래 자동 읽기 명령은 JSON의 가상 사용자 ID를 받아 읽기 전용 SQL 파일을 실행한다.
각 SQL에 UUID를 여러 번 붙여 넣지 않아도 된다.

```powershell
$demo = Get-Content build/wallet-demo.json -Raw | ConvertFrom-Json
$demoUser = ([guid]$demo.userId).ToString()
Get-Content docs/sql/wallet-demo-inspect.sql | docker compose -p saju-tests -f compose.test.yml exec -T test-db psql -U sajuppugi_test -d sajuppugi_test -v ON_ERROR_STOP=1 -v "demo_user=$demoUser"
```

직접 SQL을 입력하며 살펴보고 싶으면:

```powershell
docker compose -p saju-tests -f compose.test.yml exec test-db psql -U sajuppugi_test -d sajuppugi_test
```

먼저 DB를 확인하고 읽기 전용 트랜잭션을 시작한다. userId는 위 JSON의 값으로 대체한다.

```sql
SELECT current_database(), current_user;
BEGIN READ ONLY;
SELECT user_id, paid_balance, bonus_balance, version
FROM wallets WHERE user_id = 'JSON의-userId'::uuid;
SELECT id, balance_type, granted_amount, remaining_amount, expires_at
FROM wallet_lots WHERE wallet_user_id = 'JSON의-userId'::uuid;
SELECT id, type, total_amount, reference_id, reversal_of_id
FROM wallet_transactions WHERE wallet_user_id = 'JSON의-userId'::uuid
ORDER BY created_at, id;
SELECT t.type, t.total_amount, SUM(l.amount) AS allocated
FROM wallet_transactions t JOIN wallet_transaction_lines l ON l.transaction_id = t.id
WHERE t.wallet_user_id = 'JSON의-userId'::uuid
GROUP BY t.id, t.type, t.total_amount ORDER BY t.type;
SELECT id, status, wallet_transaction_id, recovery_attempts, recovery_last_error_code
FROM reading_purchases WHERE buyer_user_id = 'JSON의-userId'::uuid;
COMMIT;
```

기대 결과: 지갑/lot 잔액 20, TOP_UP +20·PURCHASE -15·REFUND +15 각 1건,
각 거래 total_amount와 allocated 일치, REFUND.reversal_of_id가 PURCHASE.id와 일치,
구매 status=REFUNDED. 단순 총합뿐 아니라 참조와 배분을 확인한다. psql 종료는 `\q`다.

pgAdmin을 사용하는 경우 Host=localhost, Port=55432, Maintenance DB=sajuppugi_test,
Username=sajuppugi_test, Password=local-test-only로 연결한다. 운영 연결과 별도로 등록하고
Query Tool에서 같은 읽기 전용 SQL을 실행한다. DB 비밀번호는 개발/테스트 전용이다.

## 5. 종료와 주의점

```powershell
docker compose -p saju-tests -f compose.test.yml down
Remove-Item Env:TEST_DATABASE_URL, Env:TEST_DATABASE_USERNAME, Env:TEST_DATABASE_PASSWORD -ErrorAction SilentlyContinue
```

예제 기록은 모두 사라지고 기존 개발 DB는 그대로 남는다. walletDemo 후 다른 전체 테스트는
예제 데이터 때문에 기존 전역 개수 assertion에 영향을 줄 수 있으므로, DB를 down 후 다시
up해서 새로 시작한다. 운영 지갑 쓰기와 자동 복구 flag는 이 실습 때문에 켜지 않는다.

직접 SELECT 확인은 구조 이해에 좋지만 동시 구매·장애 재현의 대체물이 아니다.
실제 PostgreSQL 자동 테스트, 실제 인증/FE/PG E2E, 중단 복구·부하 검증은 구분해서 기록한다.
