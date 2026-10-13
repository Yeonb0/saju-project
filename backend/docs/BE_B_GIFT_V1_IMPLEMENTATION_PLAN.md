# BE-B 1차 선물 수신·fulfillment 구현 계획

작성 기준: 2026-10-14, 브랜치 `feat/gift-direct-links-v1`, 기준 커밋 `e754e60`

이 문서는 1차 출시에서 BE-B가 이어서 구현할 선물 업무의 실행 기준이다. 현재 코드에 이미 들어간 BE-A의 견적·주문·등껍질 차감·취소·token 발급·재발급·수신 접수/최초 제공 내부 계약을 다시 구현하지 않고, 공개 수신부터 결과·부적·계정 연결·개인정보 파기까지 연결하는 데 사용한다.

역사적 알림톡 설계 문서보다 이 문서의 1차 직접 링크 정책을 우선한다. 구현을 시작할 때에는 반드시 현재 브랜치, migration, OpenAPI와 `docs/OPERATIONS_CHECKLIST.md`의 6-1, 6-2, 6-4, 6-5, 6-6, 6-8, 6-9, 6-10을 다시 확인한다.

## 1. 1차 출시 정책

- 로그인은 카카오만 제공한다. 네이버·구글 로그인은 2차다.
- 선물 구매자는 링크 복사 또는 카카오톡 공유하기로 직접 전달한다.
- 수신자 전화번호를 수집하지 않는다. 알림톡 대행사, 발신 프로필, 템플릿 심사, 발송·재시도는 2차다.
- 1차 선물은 수신자 1명, 수능 선물만 지원한다.
- 상품 옵션은 `TALISMAN_ONLY`, `READING_WITH_TALISMAN` 두 가지다.
- 선물은 원화 직접 결제가 아니라 구매자의 등껍질을 차감한다.
- 공개 링크는 로그인 소유권이 아니라 bearer token 권한이다. 링크를 전달받은 사람은 누구든 사용할 수 있다.
- 비로그인 수신을 허용하므로 링크 전달 자체를 막는다고 약속하지 않는다. 강한 token, 짧은 노출, hash 저장, 폐기·재발급 정책으로 보호한다.
- 링크 열기와 생년정보 입력은 콘텐츠 제공 개시가 아니다. 완성된 결과 또는 부적 정보를 서버가 처음 응답하는 경계가 제공 개시다.
- 일반 운세·오늘의 운세의 비식별 공유와 선물 권한 링크를 합치지 않는다.

## 2. 현재 구현과 BE-B 경계

### 2.1 BE-A에 이미 있는 것

- 선물 견적, 주문 초안, 등껍질 차감, 구매 복구, 미제공 취소와 등껍질 반환
- 주문 입력 `quoteId`, `recipientName`, `senderName`, `message`
- 구매 완료 시 32바이트 난수 token 생성, SHA-256 hash 장기 저장
- 구매자 본인만 가능한 링크 조회와 명시적 재발급
- 재발급 요청 멱등성, token version 증가, 이전 token 폐기, 원래 90일 만료 유지
- `/g#token=...` fragment 기반 공유 URL
- `GiftAccessPort`: token을 hash로 검증하는 BE-B 내부 권한 port
- `GiftFulfillmentPort.accept`: 90일 내 수신 신청 receipt와 BE-B recipient 저장 callback을 같은 트랜잭션에 묶는 port
- `GiftFulfillmentPort.provide`: 최초 콘텐츠 제공 기록과 BE-B 콘텐츠 확정 callback을 같은 트랜잭션에 묶는 port
- `GiftFulfillmentPort.link`, `owned`: 한 수신자 계정 연결과 이후 회원 접근 판단의 내부 기반
- 취소와 최초 제공의 gift work 잠금 직렬화, 제공 완료 settlement 기반
- BE-A draft 개인정보 정리 기반과 감사 이벤트

관련 코드:

- `gift/application/GiftAccessPort.java`
- `gift/application/GiftFulfillmentPort.java`
- `gift/infrastructure/JdbcGiftAccess.java`
- `gift/infrastructure/JdbcGiftFulfillment.java`
- `gift/infrastructure/JdbcGiftPrivacy.java`
- `gift/infrastructure/GiftPrivacyWorker.java`

### 2.2 BE-B가 구현해야 하는 것

1. 공개 선물 안내 조회
2. 암호화된 비회원 수신자 profile 저장
3. gift 전용 비동기 fulfillment intent와 worker
4. 상품 옵션에 맞는 수능운 snapshot 생성
5. gift 출처의 부적 생성·render job 연결
6. token 기반 fulfillment 상태와 결과 조회
7. 최초 콘텐츠 제공 시각의 실제 HTTP 응답 경계 연결
8. token 기반 READY 부적 signed download
9. 카카오 로그인 후 계정 claim과 reading/talisman ownership 연결
10. 비회원 생년정보·메시지·결과·파일 파기와 tombstone
11. 운영 조회·감사·알림 및 PostgreSQL 장애/경쟁 검증

### 2.3 현재 존재하지만 1차 신규 흐름에서 사용하지 않을 것

- `GiftAlimtalkProvider`
- `GiftDeliveryWorker`, `JdbcGiftDeliveryWork`
- `GiftPhoneCleanupWorker`, `JdbcGiftPhoneCleanup`
- delivery/phone cleanup 환경 설정
- 과거 migration의 전화번호·delivery column/table

공유된 migration을 수정하거나 삭제하지 않는다. 신규 주문·API·worker가 이 구조에 값을 쓰지 않게 유지하고, 제거가 필요하면 별도 additive/cleanup migration과 혼합 버전 배포 계획으로 진행한다.

## 3. 모듈·권한·트랜잭션 경계

### 3.1 모듈 책임

| 모듈 | 책임 |
|---|---|
| `gift` A 영역 | 주문, 차감·반환, token lifecycle, ACTIVE/CANCELED 상태, 최초 제공·claim receipt |
| `gift` B 영역 | 공개 API orchestration, recipient profile, fulfillment intent/result, 파기 |
| `fortune` | 수신자 facts 계산, Liner 요청, immutable reading snapshot |
| `talisman` | 부적 결정, row, render job, private asset, signed URL |
| `member/auth` | 카카오 세션 principal, 회원 상태, claim 이후 소유자 접근 |
| `operations/admin` | 실패 작업 관찰, 제한된 재처리, 감사 |

BE-B는 A의 DB 테이블을 임의 SQL로 우회해 token을 승인하거나 gift 상태를 변경하지 않는다. 가능한 한 `GiftAccessPort`와 `GiftFulfillmentPort`를 통해 권한 및 상태 전이를 수행한다. 필요한 정보가 부족하면 port 계약을 합의해 확장한다.

### 3.2 권한 종류

- 구매자 권한: 로그인 세션 + 주문 소유권. 링크 조회·재발급·취소에 사용한다.
- 공개 수신 권한: raw gift token. 로그인 여부나 이메일·닉네임 일치로 대체하지 않는다.
- 연결 회원 권한: 로그인 세션 + durable gift ownership. claim 이후 마이페이지 접근에 사용한다.
- 운영자 권한: DB에 저장된 운영자 role + 감사. 일반 role claim만으로 허용하지 않는다.

하나의 API에서 권한 종류를 혼합하지 않는다. 특히 로그인한 임의 회원이 gift ID만으로 공개 선물에 접근할 수 없어야 한다.

### 3.3 트랜잭션 원칙

- recipient profile과 fulfillment intent는 수신 접수 receipt와 같은 짧은 PostgreSQL 트랜잭션에서 저장한다.
- Liner 호출, renderer, R2 업로드, signed URL 생성은 DB 트랜잭션과 wallet/gift 잠금 밖에서 수행한다.
- worker claim, provider 호출, 완료 저장을 분리한다.
- 완료 저장은 lease token/fence와 gift ACTIVE·미환급 상태를 다시 확인한다.
- 결과를 DB에 생성한 시점과 사용자에게 제공한 시점을 분리한다.
- callback 안에서 새 트랜잭션을 열거나 외부 API를 호출하지 않는다.

## 4. 필요한 데이터 모델

정확한 이름은 기존 naming convention에 맞출 수 있지만 아래 의미와 제약은 유지한다. 새 Flyway migration만 추가한다.

### 4.1 `gift_recipient_profiles`

gift/order당 한 건의 수신자 입력 snapshot이다.

권장 column:

| column | 의미 |
|---|---|
| `id` | 내부 UUID |
| `gift_id` | gift 식별자, UNIQUE |
| `receipt_id` | A `gift_recipient_receipts.id`, UNIQUE |
| `profile_ciphertext` | 이름/닉네임, 생년월일, 달력, 윤달, 시간, 성별의 인증 암호문 |
| `encryption_key_id` | 키 회전 식별자 |
| `snapshot_hash` | 정규화한 profile의 안정적 hash/HMAC |
| `submitted_at` | 최초 저장 시각 |
| `purged_at` | 암호문 NULL 처리 시각 |
| `purge_policy_version` | 적용한 파기 정책 version |
| `reset_at`, `reset_by` | 운영자 초기화가 필요할 경우의 감사 정보 |

요구 제약:

- `gift_id`와 `receipt_id`는 각각 중복될 수 없다.
- 같은 requestId와 동일 snapshot replay는 기존 행을 반환한다.
- 동일 링크에서 다른 requestId 또는 다른 snapshot 제출은 기본 409다.
- 충돌 응답에 기존 생년정보, hash, 복호화 결과를 포함하지 않는다.
- 운영자 초기화는 별도 명령·사유·감사를 필요로 하며 기존 행을 조용히 덮어쓰지 않는다.
- 파기된 profile을 다시 생성 입력으로 사용할 수 없다.

### 4.2 `gift_fulfillment_jobs`

외부 생성과 렌더를 복구할 durable intent다.

권장 column:

- `gift_id` UNIQUE
- `receipt_id` UNIQUE
- `state`: `PENDING`, `GENERATING`, `WAITING_TALISMAN`, `READY`, `RETRY`, `FAILED`, `REVIEW_REQUIRED`, `CANCELED`, `PURGED`
- `product_code`, `product_option`, `product_snapshot_version`
- `calculation_version`, `generation_version`, `content_version`
- `attempt_count`, `next_attempt_at`, `last_failure_code`
- `lease_token`, `lease_until`, `fencing_version`
- `created_at`, `started_at`, `completed_at`, `updated_at`

요구 제약:

- gift당 하나의 intent만 존재한다.
- worker claim 때 fencing version을 원자적으로 증가시킨다.
- 완료·실패 update에는 `gift_id + lease_token + fencing_version`을 조건으로 사용한다.
- 취소·환급·만료로 중단된 작업은 재시도 후보에서 제외한다.
- 무한 재시도하지 않고 한도 뒤 `REVIEW_REQUIRED`로 보낸다.

### 4.3 `gift_reading_snapshots`

완성된 수능운 결과 또는 TALISMAN_ONLY의 제한된 콘텐츠 snapshot이다.

권장 column:

- `id`, `gift_id` UNIQUE, `receipt_id` UNIQUE
- `option`
- 계산 facts 또는 그 hash
- `calculation_version`, `generation_version`, `content_version`
- 결과 JSON/구조화 본문
- fallback 사용 여부와 안전한 생성 상태
- `created_at`
- `purged_at`, `purge_policy_version`

완성 snapshot은 immutable이다. 수정 대신 새로운 version migration/운영 정책을 사용하되, 같은 gift 재조회 때문에 다시 Liner를 호출하지 않는다.

### 4.4 talisman 연결

- `talismans` 또는 연결 테이블에 `source_type=GIFT`, `gift_id`를 저장한다.
- gift ID unique 제약으로 같은 선물에 부적이 여러 장 생기지 않게 한다.
- reading과 talisman 모두 같은 gift/receipt를 추적할 수 있어야 한다.
- 기존 일반 운세·추가 구매 출처의 제약을 깨지 않도록 nullable/additive migration을 사용한다.
- 원본 object key는 내부에만 저장한다.

### 4.5 ownership

claim 시 다음을 한 번에 연결하거나, 동일 transaction에서 재시도 가능한 durable intent를 남긴다.

- gift의 `recipient_user_id`
- reading ownership
- talisman ownership
- claim 감사 이벤트

gift당 수신자 계정은 하나만 허용한다. 동일 회원의 replay는 성공하고 다른 회원의 경쟁은 안전하게 거절한다.

## 5. 공개 API 계약

최종 경로는 OpenAPI와 FE 합의로 확정하되 아래 기능을 분리한다. raw token을 서버 로그에 남기지 않기 위해 FE는 fragment에서 token을 읽고 request body 또는 전용 header로 전달한다. token을 URL path/query로 보내지 않는다.

### 5.1 선물 안내 조회

`POST /api/v1/public/gifts/access`

예시 요청:

```json
{ "token": "원문-token" }
```

응답에 포함할 최소 정보:

- 보내는 사람 표시 이름
- 받는 사람 표시 이름
- 선물 메시지
- 상품 옵션과 사용자 안내용 상품명
- 수신 상태: 입력 가능, 처리 중, 결과 준비, 제공 기한 종료 등

응답하지 않을 값:

- gift/order 내부 ID가 필요 이상으로 드러나는 값
- 구매자 회원 ID
- 원장·가격 배분
- 생년정보
- token hash/version
- R2 object key

메시지와 이름은 문자열로만 반환하고 FE도 text node로 렌더링한다. HTML/Markdown 실행 계약을 만들지 않는다. OG에는 고정 서비스 문구와 일반 이미지만 사용하고 표시 이름·메시지·token을 넣지 않는다.

오류 원칙:

- 잘못된 형식, 변조, 존재하지 않음, 폐기된 구 token은 동일한 안전한 404 계열로 처리한다.
- 만료·취소·환급은 사용자가 이해할 수 있는 제한된 오류로 구분할 수 있지만 이름·메시지·상품 옵션은 함께 반환하지 않는다.
- 요청/응답/예외의 `toString`, access log, Sentry breadcrumb, PostHog property에 raw token을 남기지 않는다.

현재 `GiftAccessPort.GiftAccess`에는 giftId와 만료시각만 있다. 공개 안내에 필요한 A 소유 snapshot을 안전하게 받는 read port를 추가하거나 기존 port를 확장해야 한다. BE-B가 A 테이블을 직접 복호화하는 방식은 피한다.

### 5.2 수신자 정보 제출

`POST /api/v1/public/gifts/recipient-profiles`

필수 header 또는 body:

- raw token
- `Idempotency-Key` 또는 명시적 `requestId`

입력:

- 이름 또는 닉네임
- 생년월일
- `SOLAR` / `LUNAR`
- 음력인 경우 윤달 여부
- 태어난 시간 또는 시간 모름
- 성별
- 필요한 동의 version/시각. 동의가 수집 정책상 필요한 경우 원문 전체보다 version과 증거만 저장한다.

검증:

- 기존 people/person 입력 검증 규칙과 동일한 domain validator를 재사용한다.
- 존재하지 않는 날짜, 달력/윤달 조합, 시간 범위, 제어문자, 길이를 검증한다.
- 이름과 원본 생년정보를 Liner에 전달하지 않는다.
- 신규 profile을 일반 `people` 테이블에 넣지 않는다.

처리 순서:

1. token 형식과 입력을 검증한다.
2. 입력을 정규화하고 deterministic snapshot hash를 계산한다.
3. `GiftFulfillmentPort.accept(token, requestId, snapshotHash, callback)`을 호출한다.
4. callback에서 recipient profile과 fulfillment job을 같은 transaction에 저장한다.
5. 커밋 후 `202 Accepted`와 상태 조회 방법을 반환한다.

동일 요청 replay는 같은 receipt/job을 반환하고 job을 하나 더 만들지 않는다. 다른 입력 충돌 응답에는 이미 저장된 값을 포함하지 않는다.

### 5.3 처리 상태 조회

`POST /api/v1/public/gifts/fulfillment-status`

- 매 요청마다 token을 다시 검증한다.
- `PENDING`, `PROCESSING`, `READY`, `FAILED`, `REVIEW_REQUIRED`처럼 FE가 처리할 수 있는 안정된 상태만 노출한다.
- 내부 attempt, lease token, fence, provider body, object key는 노출하지 않는다.
- 조회 자체로 `first_content_provided_at`을 기록하지 않는다.
- `FAILED`만으로 등껍질 반환 완료를 표시하지 않는다. A의 durable 주문/반환 상태가 확정된 경우에만 환급 완료라고 표시한다.

### 5.4 결과 조회

`POST /api/v1/public/gifts/result`

처리 원칙:

1. token과 gift 상태를 다시 확인한다.
2. snapshot과 필요한 talisman 상태가 응답 가능한 상태인지 확인한다.
3. 아직 최초 제공 전이면 `GiftFulfillmentPort.provide(receiptId, contentId, callback)`을 호출한다.
4. callback에서 BE-B snapshot의 공개 가능 상태를 확정한다. 외부 호출은 하지 않는다.
5. 제공 transaction이 커밋된 뒤 snapshot을 응답한다.

동시 요청에서도 하나의 `first_content_provided_at`만 정해져야 한다. 취소가 먼저 잠금을 얻으면 결과를 내주지 않고, 제공이 먼저면 A의 일반 미제공 취소가 거절되어야 한다.

응답 범위:

- `TALISMAN_ONLY`: 편지, 부적 상태/표시 정보, 수능 준비물 체크리스트만 반환한다. 전체 수능 사주 해석을 생성하거나 반환하지 않는다.
- `READING_WITH_TALISMAN`: 위 항목에 구매 당시 정의된 수능운 결과를 추가한다.
- 클라이언트가 option, section 또는 포함 콘텐츠를 요청으로 선택하지 못하게 한다.
- 생성·콘텐츠 version과 서버 snapshot을 기준으로 응답한다.

READY 전에는 완성 본문을 부분적으로 유출하지 않는다. 정책상 제공 개시로 볼 수 있는 정보가 polling/status 응답에 포함되지 않도록 한다.

### 5.5 부적 다운로드

`POST /api/v1/public/gifts/talisman-download`

- raw token을 다시 검증한다.
- gift와 talisman의 실제 연관을 검사한다.
- talisman 상태가 `READY`일 때만 짧은 signed URL을 발급한다.
- 취소·환급·token 폐기·접근 만료 후에는 새 signed URL을 발급하지 않는다.
- 기존에 발급된 signed URL은 자체 TTL까지 동작할 수 있으므로 TTL을 짧게 정하고 정책에 기록한다.
- 원본 object key, bucket URL, 내부 render 정보는 응답하지 않는다.

`GiftAccessPort.verifyTalismanAccess`는 현재 fail-closed이므로 gift-talisman 연결이 완성된 뒤 구현한다.

### 5.6 계정 claim

`POST /api/v1/gifts/claims`

- 카카오 로그인 세션과 CSRF가 필요하다.
- body/header의 현재 raw token을 다시 검증한다.
- 이메일, 카카오 닉네임, 표시 이름이 같다는 이유로 자동 연결하지 않는다.
- `GiftFulfillmentPort.link(token, principalUserId)`를 사용해 계정 하나를 원자적으로 선점한다.
- reading/talisman ownership을 같은 transaction에서 부여하거나 durable 후속 intent를 함께 저장한다.
- 동일 회원 replay는 같은 결과를 반환한다.
- 다른 회원이 먼저 claim했다면 gift 존재나 상대 회원 정보를 노출하지 않는 안전한 충돌/404를 반환한다.
- claim 뒤에는 token이 만료돼도 `GiftFulfillmentPort.owned`와 각 자산 소유권으로 마이페이지에서 접근한다.
- 회원 탈퇴/withdrawal 상태에서는 접근을 차단하고 파기 정책으로 전환한다.

## 6. fulfillment worker

### 6.1 claim

- PostgreSQL의 `FOR UPDATE SKIP LOCKED` 또는 원자적 UPDATE/RETURNING으로 작은 batch를 점유한다.
- `PENDING` 또는 재시도 시간이 지난 `RETRY`만 대상으로 한다.
- claim 시 `lease_token`, `lease_until`, 증가한 `fencing_version`을 저장한다.
- worker 두 개가 같은 gift를 동시에 외부 생성하지 않도록 하지만, 프로세스 중단 때문에 외부 호출 자체는 중복될 수 있다고 가정한다.

### 6.2 계산과 Liner

1. profile을 복호화한다.
2. 서버에서 기존 사주 계산기를 사용해 facts를 계산한다.
3. Liner에는 이름과 원본 생년월일시를 보내지 않고 비식별 facts와 상품 맥락만 보낸다.
4. 기존 response schema 검증과 bounded retry를 재사용한다.
5. 재시도 소진 또는 허용된 장애에서는 결정론적 fallback을 사용한다.
6. 결과에 gift ID, 계산·생성·콘텐츠 version, fallback 여부를 저장한다.

option은 job 입력이나 클라이언트가 아니라 구매 당시 상품 snapshot에서 읽는다. `TALISMAN_ONLY`라면 전체 수능 해석을 위한 Liner 요청 자체를 만들지 않는다.

### 6.3 부적 생성

- 수신자의 계산 결과와 서버 content version으로 오행·동물·문구를 결정한다.
- 클라이언트가 talisman type이나 문구를 지정할 수 없다.
- 기존 `PENDING talisman + render job` 구조를 재사용한다.
- 동일 gift에 이미 talisman이 있으면 재사용한다.
- renderer와 R2 업로드는 gift/job transaction 밖에서 기존 render worker가 처리한다.
- 실제 승인 이미지·renderer·private R2가 없으면 `PENDING`에서 끝날 수 있으며 이를 READY로 가장하지 않는다.

### 6.4 완료 저장과 fencing

완료 transaction은 최소 다음을 조건으로 검사한다.

- 현재 job의 lease token/fencing version 일치
- gift가 여전히 ACTIVE
- 취소/환급/폐기되지 않음
- 현재 receipt가 같은 gift에 속함
- 같은 gift의 snapshot/talisman unique 제약 유지

R2 업로드 성공 후 DB 완료 저장이 실패한 경우:

- 같은 deterministic object key 또는 업로드 idempotency 규칙으로 재시도한다.
- orphan object 정리 대상을 기록한다.
- 이전 worker의 늦은 완료는 새 fence와 불일치하므로 DB 결과를 덮어쓰지 못한다.

### 6.5 실패와 운영 검토

- validation/schema failure, provider timeout, 429, 5xx, renderer failure, DB conflict를 구분한다.
- 재시도 가능한 오류만 제한적으로 재시도한다.
- max attempt 뒤 `REVIEW_REQUIRED`로 옮기고 운영 알림을 만든다.
- 실패 사유에 원본 개인정보, Liner 원문 response, raw token, R2 signed URL을 넣지 않는다.
- 운영자 재처리는 현재 상태·fence를 재검증하며 멱등이고 감사되어야 한다.

## 7. 개인정보·보안

### 7.1 암호화와 키

- recipient profile은 기존 개인/선물 cipher convention에 맞는 AEAD로 암호화한다.
- 암호화 키는 DB, Git, FE 환경 변수와 분리해 secret store에 둔다.
- key ID를 저장해 무중단 회전과 부분 실패 복구가 가능해야 한다.
- snapshot 비교용 hash에는 사전 대입 공격을 줄이기 위해 서버 비밀 HMAC을 사용한다.
- 로그에는 ciphertext 전체도 출력하지 않는다.

### 7.2 token 취급

- token은 최소 32바이트 난수이며 A가 생성한다. BE-B가 별도 token을 만들지 않는다.
- raw token을 controller/request DTO의 `toString`, 예외 message, access log, trace tag에 넣지 않는다.
- FE는 fragment에서 token을 읽은 뒤 브라우저 주소와 관측 도구에서 제거한다.
- 공개 endpoint는 request body를 로깅하지 않는다.
- proxy/server access log가 body/header를 기록하지 않는지 staging에서 가상 token으로 검색한다.

### 7.3 파기 기준

비회원 수신자:

- 한 번도 콘텐츠를 제공하지 않은 경우: 실제 결제/ACTIVE 기준 90일에 recipient profile과 메시지를 파기한다. 기한 내 접수돼 처리 중인 durable receipt의 권리는 A 정책에 따라 보호하되, 운영 검토 없이 무기한 방치하지 않는다.
- 콘텐츠를 제공한 경우: `first_content_provided_at + 90일`에 recipient profile과 메시지를 파기한다.
- 취소·환급·확정 실패: 목적이 종료되면 정책에 따라 신속히 파기 후보가 된다.
- 계정 claim 완료: 비회원 기한이 아니라 회원 탈퇴/보관 정책으로 전환한다.

파기 대상:

- recipient profile 암호문
- BE-B가 보유한 선물 메시지 사본이 있다면 그 암호문
- 재생성 가능한 민감 계산 context
- 정책에 따라 만료된 결과 DB 본문과 private R2 파일

보존 대상:

- 결제·환급·분쟁용 최소 주문/원장 기록
- 비식별 감사 식별자와 파기 시각
- `purged_at`, `purge_policy_version`, tombstone

파기 worker:

- bounded batch와 `SKIP LOCKED` 또는 lease를 사용한다.
- 행별 짧은 transaction으로 암호문 NULL, 시각, 정책 version을 저장한다.
- 파일 삭제 실패와 DB 파기 완료가 어긋날 수 있으므로 재시도 가능한 deletion intent를 둔다.
- 파기된 입력으로 결과를 새로 생성하지 못하게 job과 snapshot 상태를 함께 차단한다.
- 백업 복원 후 tombstone을 재적용하는 runbook과 검증 SQL을 만든다.

## 8. HTTP·CSRF·관측 설정

- 공개 GET을 token path로 만들지 않는다. token body/header를 사용하는 POST를 권장한다.
- 공개 수신 POST는 로그인 CSRF와 다른 경계다. bearer token 자체가 권한이므로 Origin/Content-Type/rate limit과 CORS를 별도로 제한한다.
- 구매자/claim/admin POST에는 기존 세션 CSRF를 반드시 적용한다.
- 공개 endpoint별 rate limit과 비정상 token 탐색 알림을 둔다. 오류 응답 차이와 지연으로 gift 존재 여부가 쉽게 드러나지 않게 한다.
- requestId, gift의 안전한 내부 식별자, job ID, traceId로 추적하되 raw token/개인정보는 tag로 쓰지 않는다.
- `RESOURCE_NOT_FOUND`, 만료, 취소, 처리 지연, worker review를 운영 지표로 구분하되 공개 메시지는 최소화한다.

## 9. migration·배포

- 이미 공유된 migration은 수정하지 않는다.
- 신규 테이블/column/index는 additive migration으로 추가한다.
- 구 앱과 신 앱의 혼합 배포 중 nullable/default 상태가 안전해야 한다.
- 순서 권장: schema 추가 → 코드 dual-compatible 배포 → worker 비활성 상태로 API 저장 확인 → worker 제한 활성화 → 관측 → 전체 활성화.
- 모든 gift, wallet, fulfillment, privacy worker flag의 환경별 값을 표로 관리한다.
- fake Liner, fake R2, mock renderer가 production profile에서 선택되지 않게 기동 검사를 둔다.
- rollback은 DB schema가 자동으로 되돌아가지 않는다는 전제로 앱 rollback 가능 범위를 기록한다.

## 10. 테스트 계획

금융·동시성·lease 검증은 H2가 아니라 PostgreSQL에서 수행한다.

### 10.1 API와 권한

- 정상 token 안내 조회
- 한 글자 변조, 잘못된 형식, 존재하지 않는 token의 동일한 정보 비노출 응답
- 만료, 취소, 환급, 폐기 token
- 재발급 후 새 token 성공/구 token 실패/만료일 불변
- 안내 조회만으로 최초 제공 시각이 생기지 않음
- 이름과 메시지의 HTML/script가 실행되지 않음
- token/이름/생년정보가 로그·Sentry·PostHog에 없음

### 10.2 recipient 제출

- 기존 person validator와 같은 날짜·달력·윤달·시간·성별 검증
- 동일 requestId/동일 body replay
- 동일 requestId/다른 body 충돌
- 다른 requestId/동일 또는 다른 body 충돌
- 동시에 두 제출 요청을 보내도 profile/job 하나
- callback 중 profile 저장 실패 시 receipt/job 모두 rollback
- 저장된 생년정보가 충돌 응답에 나타나지 않음
- 운영자 초기화 전 재제출 거절, 초기화 후 한 번 허용 및 감사

### 10.3 worker·fencing

- claim 직후 worker 강제 종료, lease 만료 뒤 재개
- worker 두 개 동시 실행, 단일 최종 snapshot/talisman
- 첫 worker 정지 → 두 번째 lease → 첫 worker 늦은 완료 차단
- Liner timeout/429/5xx/schema 오류와 제한 재시도
- fallback 결정성
- R2 성공 후 DB 실패와 orphan/retry 처리
- 항상 실패하는 job이 다른 job을 막지 않고 review로 이동

### 10.4 상품 범위

- `TALISMAN_ONLY`가 reading 전체를 생성·저장·반환하지 않음
- `TALISMAN_ONLY`가 편지·부적·체크리스트만 반환
- `READING_WITH_TALISMAN`만 수능운 결과 반환
- client가 option/section/talisman 값을 위조해도 구매 snapshot이 우선
- gift당 reading/talisman unique 제약

### 10.5 제공·취소 경쟁

- 결과 생성 완료만으로 `first_content_provided_at`이 생기지 않음
- 첫 결과 응답에서만 최초 제공 시각과 +90일이 기록됨
- 결과 조회 두 건 동시 요청 시 최초 제공 시각 하나
- 취소가 먼저면 제공 실패, 제공이 먼저면 미제공 취소 실패
- 90일 미열람 만료 후 늦은 worker 완료가 공개되지 않음
- 환급 뒤 늦은 worker가 READY로 되살리지 못함
- FAILED만으로 환급 완료 응답을 만들지 않음

### 10.6 부적·claim

- PENDING/READY/FAILED 응답
- READY만 signed URL 발급
- 다른 gift의 talisman ID 접근 실패
- object key 비노출
- 취소·만료 후 새 signed URL 발급 실패
- 같은 회원 claim replay
- 서로 다른 회원의 동시 claim에서 한 계정만 성공
- claim 후 token 만료에도 소유 회원 접근 성공
- 타 회원·탈퇴 회원 접근 실패

### 10.7 파기·복원

- 미열람 90일과 제공 후 90일 정확한 경계
- claim된 gift의 비회원 파기 제외와 탈퇴 정책 전환
- 여러 privacy worker의 중복 파기 방지
- 실패 행 bounded retry, 건강한 행 계속 처리
- 암호문 NULL 및 tombstone/정책 version 유지
- 파기 후 재생성 불가
- 독립 DB/R2 복원 후 tombstone 재적용

### 10.8 staging E2E

1. 카카오 구매자 A 로그인
2. 등껍질 충전/보유 확인
3. 선물 견적·주문·구매
4. 링크 복사 또는 카카오톡 공유
5. 별도 브라우저 B에서 비로그인 안내 조회
6. 수신자 정보 제출
7. worker 처리 중 상태 확인
8. 옵션에 맞는 결과·부적 확인
9. READY 부적 다운로드
10. B가 카카오 로그인 후 claim
11. token 만료 후 B 마이페이지 재조회
12. 원장, gift 주문, receipt, profile, job, snapshot, talisman, render job, ownership이 같은 gift를 가리키는지 대사

실제 R2 bucket/credential/CORS, 승인된 부적 asset, renderer가 없으면 부적 READY E2E는 미검증으로 명시한다. HTTP 200이나 전체 테스트 개수만으로 원장·권한·파일 정합성을 확인했다고 쓰지 않는다.

## 11. 작업 단위와 권장 순서

### 단계 1: 공개 access와 recipient profile

- A read port 계약 확장
- 공개 안내 API와 안전한 오류
- recipient profile migration/cipher/repository
- 제출 API, 단일 제출, A `accept` 연결
- fulfillment intent 저장
- PostgreSQL 멱등/동시성/rollback 테스트

완료 조건: 비로그인 token으로 최소 안내 조회와 단일 profile 접수가 가능하고 외부 생성은 아직 시작하지 않아도 된다.

### 단계 2: fulfillment worker와 snapshot

- job claim/lease/fencing/retry/review
- profile 복호화와 facts 계산
- option별 Liner/fallback
- immutable gift result snapshot
- 취소·환급·만료 fence

완료 조건: fake Liner 환경에서 프로세스 중단과 두 worker 경쟁 후에도 gift당 snapshot 하나로 수렴한다.

### 단계 3: gift talisman

- gift source/unique migration
- 기존 talisman 생성과 render job 연결
- PENDING/READY/FAILED 상태
- gift-talisman access port 구현
- signed download API

완료 조건: fake renderer/storage에서는 끝까지 검증하고 실제 R2/asset 의존 항목은 별도 blocker로 기록한다.

### 단계 4: 결과 제공과 취소 경쟁

- 결과 API
- A `provide` 연결
- 최초 제공/90일 원자화
- 동시 결과 조회와 취소 경쟁
- settlement 증거 대사

완료 조건: 생성 완료 시각과 최초 제공 시각이 분리되고 경쟁 결과가 PostgreSQL 테스트로 증명된다.

### 단계 5: claim과 회원 보관함

- 로그인 claim API
- gift/reading/talisman ownership 원자 연결
- replay/계정 경쟁
- 회원 마이페이지 접근과 token 만료 독립성

### 단계 6: privacy와 운영

- recipient/result/message/file 파기
- tombstone과 복원 runbook
- review 조회·재처리·감사·알림
- staging 관측 데이터 검색, 부하, 복원 연습

각 단계는 검증 후 로컬 feature commit을 만든다. substantially different한 다음 단계로 넘어가기 전 현재 기능을 feature-boundary PR로 게시하되 merge·배포는 별도 승인 없이 수행하지 않는다.

## 12. 완료 보고 형식

매 기능 완료 보고에는 다음을 포함한다.

- 변경한 모듈과 migration/API
- 권한 경계와 트랜잭션 경계
- 외부 호출 위치와 실패/복구 상태
- 적용한 운영 체크리스트 절
- 실제 실행한 PostgreSQL 테스트와 관찰한 DB 정합성
- 실제로 검증하지 못한 staging/R2/Liner/부하/복원 항목
- 새로 발견한 정책 결정 또는 blocker

“구현됨”, “환급됨”, “READY”, “운영 가능”은 durable DB 상태나 실제 외부 환경 증거가 있을 때만 사용한다.

## 13. 1차 범위 밖

- 네이버·구글 로그인
- 수신자 전화번호 수집
- 알림톡 provider, 발신 프로필, 템플릿 심사, 발송·재시도·재발송
- 여러 명에게 한 번에 선물
- 수능 외 일반 운세 선물
- 일반 운세·부적의 공개 공유 카드 디자인
- 선물 링크의 수신자 신원 강제 인증

범위 밖 기능을 위해 1차 profile/API에 전화번호나 delivery state를 다시 추가하지 않는다.
