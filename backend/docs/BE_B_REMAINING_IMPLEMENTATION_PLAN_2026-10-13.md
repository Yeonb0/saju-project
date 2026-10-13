# BE-B 남은 구현 실행 계획

작성일: 2026-10-13  
대상 브랜치: `feat/saju-calculation` (`0890241`)  
관련 문서: [`BACKEND_ROLE_SPLIT.md`](BACKEND_ROLE_SPLIT.md), [`BE_B_FE_UNBLOCKING_IMPLEMENTATION_PLAN_2026-10-11.md`](BE_B_FE_UNBLOCKING_IMPLEMENTATION_PLAN_2026-10-11.md), [`PENDING_DECISIONS.md`](../../docs/PENDING_DECISIONS.md), [`OPERATIONS_CHECKLIST.md`](../../docs/OPERATIONS_CHECKLIST.md)

## 1. 목적과 현재 기준

이 문서는 이미 구현된 BE-B 기반을 다시 만드는 계획이 아니라, 출시 흐름을 완결하기 위해 남은 BE-B 범위를 순서대로 정리한 실행 계획이다. 금전 차감·환불·선물 token·알림 발송의 최종 소유자는 BE-A이며, BE-B는 결과 생성·부적·개인 데이터·공유 projection을 공개 application service/port로 제공한다. 상대 모듈 repository나 테이블을 직접 접근하지 않는다.

### 이미 구현되어 다시 작업하지 않는 범위

- 카카오 OAuth state, DB 기반 불투명 세션, CSRF/Origin 검사, 로그아웃, 30일 만료, 사용자당 활성 세션 5개 제한
- 회원/인물 CRUD, 암호화 저장, 소유권 검사, soft delete, `ReadingSubjectPort`
- 일반 운세·수능운 견적/구매/조회, `WalletPurchasePort`를 통한 등껍질 차감·보상 호출, 구매 상태 조회
- Liner structured-output 검증, 제한 재시도, 결정론적 fallback, 결과 snapshot
- 수능 typed guide, KST 기준 오늘의 운세, 오늘의 운세 24시간 비식별 공유·철회
- 부적의 결정(오행/동물/문구), `talismans`와 `talisman_ownerships`의 PENDING 저장 기반

### 실제로 남은 구현 범위

1. 부적 렌더링·비공개 파일 저장·상태 전이·보관함/다운로드
2. 부적 추가 구매와 선물 수신 결과/부적 fulfillment의 BE-B port 구현
3. 일반 운세/부적 공유 범위가 확정된 뒤의 비식별 공유 리소스
4. 신살 결과의 계산·콘텐츠가 확정된 경우의 구현
5. 네이버/구글 로그인 또는 계정 연결이 제품 범위에 포함될 경우의 인증 확장
6. 실제 staging에서 OAuth와 구매 E2E, 장애·복구·복원 검증

정책이 미정인 항목은 인터페이스, 상태, 테스트 double까지만 만들며 제품 정책을 코드의 임의 기본값으로 고정하지 않는다.

## 2. 우선순위와 선행 조건

| 순서 | 작업 | 우선순위 | 선행 조건 | 완료 산출물 |
| --- | --- | --- | --- | --- |
| B-1 | 부적 작업 상태와 렌더링/저장 worker | P0 | T-01 부족한 기운 기준, F-06 동기/비동기 결정 | READY/FAILED로 수렴하는 부적과 private object |
| B-2 | 부적 보관함·signed URL·추가 구매 | P0 | B-1, BE-A의 quote/차감 계약 확인 | 소유자만 조회·다운로드·구매 가능 |
| B-3 | 선물 결과/부적 BE-B fulfillment port | P0 | BE-A gift 상태·token·recipient 계약 | 선물 수신 정보 후 결과/부적이 한 번만 생성 |
| B-4 | staging OAuth·구매 E2E와 장애 연습 | P0 | 고정 staging URL, 카카오 콘솔 설정, 테스트 계정/재화 | 브라우저 기준 증거와 DB 대사 기록 |
| B-5 | 일반 운세·부적 공유 | P1 | G-11 공개 범위/카드 확정 | 개인정보 없는 공유 snapshot·asset·철회 |
| B-6 | 신살 | P1 | F-03의 계산 기준·17종 콘텐츠 확정 | 결정론적 계산·구조화 결과·부적 연동 |
| B-7 | 네이버/구글 및 계정 연결 | P1 | A-01/I-07 범위·일정 확정 | provider별 OAuth·안전한 연결/충돌 처리 |

`B-1`과 `B-3`은 구현을 병행할 수 있지만, 실제 업무 작업 실행 방식(worker/outbox/lease)은 공통 인프라와 BE-A의 gift 상태 전이와 함께 설계·리뷰한다. 부적 파일 업로드를 요청 트랜잭션 안에서 기다리면 안 된다.

## 3. B-1 — 부적 렌더링·파일 저장·복구

### 3.1 결정이 먼저 필요한 항목

다음은 구현 시작 전에 `docs/PENDING_DECISIONS.md`에서 확정하거나 명시적으로 feature flag로 격리한다.

- **T-01:** 부족한 오행 산출 규칙과 기준일. 일반 운세는 구매/조회일, 수능운은 시험일이라는 제품 의도를 계산 version에 포함한다.
- **F-06:** 이미지 합성·R2 업로드를 동기 응답으로 할지 비동기 작업으로 할지. 권장안은 **비동기**다. 생성 API는 결과와 `talisman.status=PENDING`을 반환하고 worker가 파일 작업을 수행한다.
- 문구/에셋 카탈로그의 승인 버전(`contentVersion`) 및 저작권이 확인된 원본 에셋.

정책 미확정 상태에서는 `TalismanCompositionPolicy`를 확장하지 않고, 렌더러는 결정된 `element`, `animal`, `phrase`, `contentVersion`만 입력으로 받는다.

### 3.2 모듈과 데이터 모델

영향 모듈은 `fortune/talisman`, `fortune/reading`, 공통 outbox/worker, object-storage adapter, 그리고 결과 API다. 현재 `talismans`는 `PENDING`, `READY`, `FAILED` 및 세 종류의 object key를 이미 표현한다.

필요한 후속 Flyway migration(새 파일만 추가)은 다음을 검토한다.

| 대상 | 변경 목적 |
| --- | --- |
| `talisman_render_jobs` 또는 공통 outbox payload | talisman ID, 상태, attempt 수, lease/fencing token, 다음 시도 시각을 영속화 |
| `talismans` | 마지막 오류 시각, 재시도 가능 여부, renderer/content version이 현재 스키마에 부족할 때만 additive 열 추가 |
| object key 규칙 | DB에는 object key만 저장하고 signed URL·원본 URL·provider credential은 저장하지 않음 |
| `talisman_ownerships` | 이미 `(talisman_id, owner_user_id)` PK가 있으므로 신규 ownership 삽입의 중복 방지 근거로 유지 |

기존 `V202610081300__b_talisman_storage.sql`은 적용된 migration이므로 수정하지 않는다. migration은 빈 PostgreSQL DB와 기존 데이터가 있는 upgrade DB 양쪽에서 검증한다.

### 3.3 처리 흐름과 트랜잭션 경계

```text
운세 구매 완료
  → 짧은 DB transaction: reading snapshot + PENDING talisman + render-job/outbox 저장
  → commit
  → worker가 lease를 얻음
  → DB transaction 밖: PNG/WebP/공유 에셋 렌더링, private R2 업로드
  → 짧은 DB transaction: fencing token 확인 후 object key + READY 또는 FAILED 저장
```

- 같은 `source_reading_id`의 부적은 하나만 생성한다. 현재 UNIQUE 제약과 repository의 재조회로 중복 생성 경쟁을 막고, job도 talisman ID 기준으로 dedupe한다.
- R2 호출, 이미지 합성, signed URL 생성은 지갑/구매/인물 row lock이나 긴 DB transaction 안에서 실행하지 않는다.
- worker는 lease 만료, fencing token, bounded retry를 사용한다. 오래 멈춘 worker의 늦은 READY 저장은 새 lease 소유자의 결과를 덮어쓰면 안 된다.
- 업로드가 성공했으나 READY 저장 전에 프로세스가 종료되면, 재시도는 결정론적 key로 같은 객체를 덮어쓰거나 존재 여부를 확인해 DB 상태를 수렴시킨다. 새로운 talisman이나 새로운 ownership을 만들지 않는다.
- FAILED는 사용자에게 준비 중/실패 상태로만 노출하며, 자동 재시도 한도 소진 뒤에는 운영자가 원인을 확인하고 안전하게 재시도한다. 금전 보상 여부는 B가 단독으로 결정하지 않고 기존 구매 보상 상태 머신과 연결한다.

### 3.4 저장소와 URL 보안

- 원본 PNG(1080×1920), thumbnail WebP(360×640), 공개 공유용 별도 에셋을 private/public prefix로 명확히 분리한다.
- 일반 원본과 thumbnail은 owner 또는 검증된 선물 접근권한이 있을 때만, 짧은 만료(정책 기준 10분)의 signed URL을 발급한다. object key를 API 응답에 노출하지 않는다.
- 권한 검사는 `talisman_id`만으로 하지 않고 `(authenticated user, talisman ownership)` 또는 BE-A `GiftAccessPort.verifyTalismanAccess` 결과를 함께 확인한다. 존재하지 않는 것과 타인 소유는 같은 404로 응답한다.
- raw gift token, signed URL의 query string, 원본 생년 정보, R2 credential은 로그·trace·analytics·오류 응답에 기록하지 않는다.

### 3.5 API와 완료 조건

계약을 먼저 OpenAPI에 additive 방식으로 추가한다.

- `GET /api/v1/talismans`: 본인 보관함 cursor pagination. animal/element/phrase/description/status/thumbnail access 정보만 반환한다.
- `GET /api/v1/talismans/{id}`: 본인 소유 detail. `PENDING`, `READY`, `FAILED`를 명시한다.
- `POST /api/v1/talismans/{id}/download-url`: 본인 또는 검증된 gift access만 사용 가능한 단기 signed URL을 발급한다. 상태가 READY가 아니면 URL을 주지 않는다.
- 결과 뒤 추가 구매 endpoint는 BE-A가 제공하는 quote와 `WalletPurchasePort`를 사용하며, talisman renderer에 클라이언트가 element/animal/price를 전달하지 못하게 한다.

완료 판정은 실제 PostgreSQL에서 동시 요청, 재시도, worker 중단, 업로드/DB 저장 실패를 포함해 talisman 상태·object key·ownership·구매 상태가 일치하는 것이다. HTTP 200만으로 완료 판정하지 않는다.

## 4. B-2 — 결과 후 부적 추가 구매

### 책임 경계

BE-B controller/service는 대상 reading의 소유권, 해당 reading이 부적 추가 구매 가능한지, 결과 계산 facts와 content version을 검증한다. BE-A는 quote 가격, idempotency, 지갑 차감·원장·보상 상태를 제공한다.

### 안전한 순서

1. 인증 사용자와 reading을 소유권 조건으로 조회한다.
2. 서버가 추가 구매 가능 상품/견적을 생성 또는 검증한다. 가격/부적 종류를 client body로 받지 않는다.
3. `Idempotency-Key`와 요청 지문을 확인하고 `WalletPurchasePort`로 차감한다.
4. 차감 성공과 부적 생성 의도를 같은 업무 상태로 저장한다. 이미 동일 reading에 부적이 있으면 기존 결과를 반환하고 재차감하지 않는다.
5. B-1 render job을 durable하게 등록한다. 이후 렌더링은 비동기다.

지갑 차감 이후 job 등록/저장이 실패하면, 전체 DB transaction을 롤백할 수 있는 경계에서는 함께 롤백한다. commit 뒤의 R2 장애는 원장 보상 또는 재시도 중 어느 경로인지 구매 상태와 함께 명시하며, 재시도/보상 결정을 중복 실행하지 않는다. 이 상태 전이와 포트 계약은 BE-A 리뷰가 필수다.

## 5. B-3 — 선물 수신 결과와 부적 fulfillment

### 필요한 port

BE-A gift 모듈은 token 검증, gift 주문 상태, 수신자 연락처/알림, 등껍질 환급을 책임진다. BE-B는 다음 공개 port를 제공한다.

```java
GiftFulfillment fulfill(GiftId giftId, GiftRecipientInput input);
GiftContentSummary summarize(GiftId giftId);
```

`GiftRecipientInput`은 필요한 최소 입력만 받고 암호화 converter를 통해 저장한다. 원문 전화번호와 raw token은 BE-B로 전달하거나 로그에 남기지 않는다. `GiftFulfillment`에는 내부 식별자, 결과/부적의 준비 상태, 재시도 가능한 오류 분류만 넣고 개인 출생 정보를 반환하지 않는다.

### 데이터·상태·동시성

- recipient profile의 schema/crypto converter는 BE-B가 제공하되, gift lifecycle migration과 상태 전이의 소유자는 BE-A와 합의한다.
- `gift_id`를 unique key로 하여 수신자 제출 재전송, 페이지 새로고침, worker 재개에도 결과와 talisman을 한 번만 만든다.
- 수신자 제출 transaction은 gift access를 다시 검증하고 gift row 또는 동등한 업무 row를 잠근 뒤, recipient snapshot + fulfillment intent를 저장한다.
- Liner/R2 호출은 transaction 밖에서 실행한다. 결과 생성/부적 생성 작업은 durable job으로 남기고, 결과 조회는 `PENDING/READY/FAILED`를 명확히 반환한다.
- gift 만료/철회/환불과 늦은 worker 완료가 경쟁할 경우, 현재 gift 상태와 fencing token을 저장 직전에 다시 확인한다. 만료된 gift에 새 결과/URL/ownership을 부여하지 않는다.

### 공개 조회

공개 선물 결과 endpoint는 BE-A가 token 접근을 먼저 검증한 뒤 `GiftContentProjectionPort`로 최소 표시 모델을 받는다. BE-B는 사용자 ID, person ID, 원본 birth input, object key를 projection에 포함하지 않는다. 부적 download URL도 `GiftAccessPort.verifyTalismanAccess`를 통과해야 한다.

### 완료 조건

동일 token/수신자 재전송, token 만료/폐기, worker 중단, Liner 실패, R2 성공 후 DB 실패, gift refund와 늦은 완료를 PostgreSQL 통합 테스트로 검증한다. 선물 결과·부적이 여러 개 생기거나, 환급된 gift가 READY로 되살아나면 실패다.

## 6. B-4 — staging 및 운영 준비 검증

이 단계는 코드 완료와 별개인 출시 전 검증이며, 실제 환경 변경/실제 테스트 결제는 사용자 및 해당 외부 계정 권한 범위에서만 수행한다. secret 값은 채팅·로그·문서에 기록하지 않는다.

### 6.1 OAuth/세션 (운영 체크리스트 6-1, 6-6)

1. staging 프런트 origin, backend callback URL, `FRONTEND_BASE_URL`, 카카오 Developers redirect URI가 같은 고정 origin을 가리키는지 값 자체가 아닌 대상/일치 여부만 확인한다.
2. 두 브라우저 프로필 A/B로 카카오 authorize → callback → HttpOnly session cookie → `GET /session`을 확인한다.
3. A의 people/reading/talisman을 B가 조회·변경·다운로드·공유 철회할 수 없는지, 타인/없는 리소스가 동일 404인지 확인한다.
4. CSRF 누락/오류, 외부 `returnTo`, 로그아웃, 세션 만료, 6번째 로그인 시 LRU 세션 폐기를 확인한다.
5. Next `/api/v1` proxy를 경유한 cookie의 Secure/SameSite/Domain 동작과 CORS를 브라우저 network 기록으로 확인한다.

### 6.2 구매·부적·선물 (6-2, 6-3, 6-4, 6-5)

- 전용 staging 사용자와 테스트 재화로 인물 → 견적 → 차감 → 결과 → 부적 PENDING → READY → 다운로드를 실행하고, order/purchase/ledger/talisman/job의 식별자를 서로 대사한다.
- 동일 `Idempotency-Key` 재전송, 다른 key의 동시 요청, 잔액 경계 경쟁에서 이중 차감/중복 ownership이 없는지 PostgreSQL에서 확인한다.
- Liner/R2 timeout, 5xx, 잘못된 응답, worker kill/restart, lease 만료 뒤 늦은 완료를 장애 주입으로 재현한다. 외부 결과가 불명확하면 새 구매가 아니라 lookup/reconciliation 경로를 사용한다.
- 선물 token 변조·만료·철회·재발급, signed URL 만료, 타인 URL 재사용을 확인한다.

### 6.3 migration·복구 (6-6, 6-7)

- 새 Flyway migration은 staging의 기존 데이터가 있는 DB에서 성공 이력과 구/신 애플리케이션 호환성을 확인한다.
- 롤백은 DB migration rollback이 아니라 호환 가능한 구 버전 앱 복귀 범위로 문서화한다. 업로드된 R2 객체와 DB record의 불일치 복구 절차도 포함한다.
- 백업 복원 시험은 운영 DB를 덮어쓰지 않는 별도 DB에서 실시하고, 복원 후 미완료 render job이 무단 재전송/재업로드되지 않도록 worker를 차단한 상태에서 대사한다.

## 7. B-5 — 일반 운세·부적 공유

오늘의 운세 공유와 달리 일반 결과/부적은 G-11의 공개 범위와 카드 구성이 확정되기 전에는 구현하지 않는다. 결정 뒤 다음 원칙을 적용한다.

- 공유 snapshot에는 이름, birth input, user/person ID, 상세 계산 facts, private object key를 절대 포함하지 않는다.
- 원문 URL token은 32바이트 이상 난수이고 DB에는 hash로만 조회한다. 공개 token을 logs/analytics에서 마스킹한다.
- snapshot은 생성 시점의 고정 데이터며, 결과 수정/탈퇴/철회/만료 정책을 명확히 한다.
- 공개 이미지와 private 다운로드는 다른 object key 및 권한 정책을 사용한다.
- 생성자 철회, 만료, 같은 대상의 활성 링크 재사용, 경쟁 생성, DB 저장 실패 후 재시도를 PostgreSQL에서 검증한다.

## 8. B-6/B-7 — 정책 확정 후의 확장 작업

### 신살 (F-03)

17종의 계산 기준, 역법 기준, 콘텐츠 문구가 확정되기 전에는 판매/노출을 feature flag로 막는다. 확정 후에는 계산 facts schema → 결정론적 calculator → 구조화 response → Liner 제한 입력 → fallback → snapshot 순서로 구현한다. 공포 조장 표현 방지와 `살 다스리기` 문구 검증도 response validator에 포함한다.

### 네이버/구글 및 계정 연결 (A-01/I-07)

카카오와 별도 provider adapter를 추가하되, provider subject와 provider type을 unique하게 저장한다. 같은 이메일/닉네임을 자동 계정 연결 근거로 사용하지 않는다. 계정 연결은 이미 인증된 세션, 명시적 사용자 확인, 충돌 계정 처리, unlink 최소 한 개 로그인 수단 보장 등 제품 정책이 확정된 뒤 구현한다.

## 9. 공통 테스트와 문서 갱신 규칙

각 기능 PR은 다음을 모두 갖춘다.

- 단위 테스트: composition, 입력 검증, 권한 projection, renderer request/response 검증
- H2 회귀 테스트와 별도 PostgreSQL 통합 테스트: migration, unique/FK, row lock, 동시성, transaction rollback
- 장애 주입: DB 저장 실패, object store timeout/5xx, process interruption, stale lease, 늦은 완료
- API 계약: OpenAPI export, FE generated type 검사, 성공뿐 아니라 PENDING/FAILED/권한 없음/재전송 예시
- 보안 점검: raw token, signed URL, birth input, provider key가 로그/trace/error/analytics에 없는지 확인
- 운영 기록: 환경, 코드 커밋, 확인한 DB 상태, 작업 식별자, 실제 결과와 미확인 범위를 `OPERATIONS_CHECKLIST.md`에 민감정보 없이 갱신

## 10. 다음 착수 제안

가장 먼저 T-01과 F-06을 확정한다. 확정되면 **B-1(비동기 부적 render job + private R2 + READY/FAILED 수렴)**을 하나의 기능 경계로 구현·PostgreSQL 검증·커밋한다. 그 다음 **B-2(보관함/다운로드/추가 구매)**, **B-3(선물 fulfillment)** 순으로 진행한다. 각 기능 경계가 검증되면 별도 PR을 만들고, staging E2E는 코드 PR과 분리해 결과만 운영 체크리스트에 기록한다.
