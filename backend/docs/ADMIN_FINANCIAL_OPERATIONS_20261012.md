# 관리자 금융 운영 API 전달

기준: PR #24 merge `b3a114b`, branch `codex/admin-financial-recovery`.
환불·운영 알림과 다른 권한 범위라 별도 PR로 분리했다. FE/기존 공유 OpenAPI/.env/실제 권한 부여/배포는 변경하지 않았다.

## 권한과 활성화

```ini
ADMIN_OPERATIONS_ENABLED=false
ADMIN_RECOVERY_ENABLED=false
```

- 기본은 비활성이다. 조회와 재처리를 각각 명시적으로 활성화한다.
- 서버의 실제 세션 사용자 UUID와 admin_operator_grants를 매 요청마다 대조한다. 클라이언트 역할/헤더/화면에서 ADMIN이라고 해도 권한이 생기지 않는다.
- active=true이면서 users.status=ACTIVE인 운영자만 조회할 수 있고 can_recover=true인 운영자만 재처리할 수 있다.
- 운영자는 신뢰할 수 있는 DB 관리/프로비저닝 절차로 등록한다. self-service 권한 부여·철회 API나 자동 첫 사용자 승격은 없다.
- 실제 운영자 지정, 권한 부여/철회자 감사와 정기 점검 절차는 팀이 별도로 확정해야 한다. 이번에는 테스트 DB의 가상 계정만 사용했다.
- 재처리 직전에 권한과 회원 상태를 다시 검사하고, 금융 저장 중 role/member 행을 잠가 권한 철회와 저장이 경쟁하지 않게 한다.
- 비활성/탈퇴 운영자는 새 작업과 미완료 작업 수행이 차단된다. 서버에 이미 위임된 작업은 로그인 쿠키보다 현재 DB 운영 권한을 기준으로 판단한다.

## 조회 API

모두 로그인과 DB 운영 권한이 필요하다. 데이터 조회와 감사 기록을 함께 저장하며 감사 저장이 실패하면 금융 응답을 반환하지 않는다.

| GET 경로 | 내용 |
|---|---|
| /api/v1/admin/top-up-orders | 주문 ID·사용자 UUID·금액·주문/환불 상태 |
| /api/v1/admin/refunds | 환불 ID·주문 ID·상태·금액·시도 횟수 |
| /api/v1/admin/wallets/{userId} | 저장 잔액·현재 사용 가능한 lot 합계·미해결 finding |
| /api/v1/admin/wallets/{userId}/transactions | 원장 ID·유형·부호 있는 수량·reference/reversal ID |
| /api/v1/admin/incidents | 운영 incident 상태·유형·재발 generation |
| /api/v1/admin/audit-events | 운영자·동작·대상·사유·발생 시각 |

- 목록은 UUID after cursor와 limit(기본 50, 1~100)을 사용한다. 응답은 items/hasNext/nextCursor다. 시간순 정렬이 아니라 ID 기준 scan이다.
- 조회는 REPEATABLE_READ snapshot으로 지갑 projection/lot 합계를 함께 읽는다. 만료 판단 시각도 한 번만 정한다.
- 잘못된 저장 잔액을 진단 화면에 보일 수는 있지만 임의 수정하지 않는다.
- 결제키 원문·해시, 요청키 해시, 세션/생년정보/이름/연락처/PG 오류 본문은 반환하지 않는다.
- 조회/복구 감사는 append-only application 경로다. 감사 행 수정·삭제 API는 없다. DB 접근 통제와 보존 정책은 별도 운영 준비다.

## 재처리 API

```text
POST /api/v1/admin/top-up-orders/{orderId}/reconcile
Idempotency-Key: 관리자 처리 의도별 요청키
X-CSRF-Token: GET /api/v1/session에서 받은 토큰
body: {"reasonCode":"CUSTOMER_INQUIRY" 또는 "SYSTEM_RECOVERY"}

GET /api/v1/admin/recovery-commands/{commandId}
```

- 실제 세션 쿠키는 SAJUPPUGI_SESSION이다. POST는 CSRF와 can_recover 권한, 사유, 요청키가 모두 필요하다.
- 운영자+요청키를 영속 저장한다. 같은 요청은 같은 command를 반환하고 다른 주문/사유에 키를 재사용하면 409다.
- 명령/최초 감사 INTENT를 먼저 저장한 뒤 DB 트랜잭션 밖에서 PG를 조회한다.
- PG에 승인·취소 POST를 새로 보내지 않는다. 금액 입력·임의 재지급·임의 환불·잔액 조정·지갑 제한 해제는 받지 않는다.
- 원래 intent의 주문/결제키 해시/원화 금액과 PG 조회 증거가 일치해야 한다. 원장 이상이나 사용 제한이 있으면 지급을 강행하지 않는다.

| 검증된 상황 | 처리 |
|---|---|
| PG DONE, 내부 PAYMENT_PENDING/PAID | 기존 wallet credit 경계로 한 번 지급, 주문 CREDITED, 기존 실행 lease 무효화 |
| PG DONE, 이미 CREDITED | ALREADY_CREDITED. 단, 불명 환불이 남아 있으면 MANUAL_REVIEW_REQUIRED |
| PG 전액 CANCELED, 아직 미지급 | CANCELED 저장, 지급하지 않음 |
| PG 전액 CANCELED, 예약 회수분이 남은 미사용 환불 | 회수 원장/receipt/lot을 확인한 뒤 REFUNDED 저장, 다시 취소하거나 지급하지 않음 |
| PG 외부 취소, 미사용 지급분 | 기존 환불 대사 경계 사용. 자동 회수가 불가능하면 검토/제한 유지 |
| 불명/증거 불일치/원장 이상/제한/반환 후 외부 취소 | 자동 강행하지 않고 재조회 또는 REVIEW_REQUIRED |

- 금융 변경, command 완료, 최종 감사 기록은 한 트랜잭션이다. 감사 실패 시 지급/주문 변경도 함께 롤백한다.
- order → command → role/member → wallet 순서로 잠근다. PG 대기 중에는 해당 잠금을 유지하지 않는다.
- command 등록도 order를 먼저 잠근다. 완료 시 role/member의 FOR NO KEY UPDATE는 권한·회원 상태 변경을 막되 외래키 KEY SHARE와 공존하여 새 명령 등록과의 교착을 피한다.
- command lease/token으로 오래된 응답을 거절한다. 최대 3회 조회, 30초 retry를 사용한다. scheduler는 20개씩, 기본 60초 간격이며 부팅 직후 즉시 실행하지 않는다.
- 기존 충전/환불의 누적 시도 횟수를 초기화하거나 과거 UNKNOWN을 처음 시도로 바꾸지 않는다.
- GET은 command 조회만 수행하며 재처리는 scheduler/기존 POST 의도 재개가 수행한다.
- POST는 처리 중 202, COMPLETED 또는 REVIEW_REQUIRED일 때 200이다. HTTP 200은 문제 해결 증거가 아니다.
- COMPLETED는 resultCode(CREDITED/REFUNDED/CANCELED/ALREADY_CREDITED/ALREADY_CLOSED)를 함께 해석한다. 현금 환불 완료는 REFUNDED인 경우뿐이다.
- REVIEW_REQUIRED는 임의 잔액 복구/지갑 해제 지시가 아니다. 별도 정책/권한/증거가 필요한 수동 처리는 아직 제공하지 않는다.

## FE/관리 도구 연결

- 기존 사용자 FE에 관리자 화면이나 버튼을 추가하지 않았다. 현재 사용자 화면 adapter와 별개 기능이다.
- 관리자 클라이언트도 같은 출처 쿠키·세션 CSRF·공통 data/traceId/error code를 사용한다.
- 관리자 전용 계약: backend/docs/openapi/admin-operations-v1.json. local에서 활성화되면 /v3/api-docs/admin 그룹을 제공한다.
- 별도 그룹은 실제 세션 쿠키와 POST CSRF를 명시한다. 기존 일반 v1 snapshot과 FE 생성 타입은 변경하지 않았다.
- backend에서 `.\gradlew.bat exportAdminOpenApi`로 테스트용 계약을 생성할 수 있다. 실제 운영자/PG 설정을 사용하지 않는 분리 테스트 환경을 사용한다.

## 검증

### 관리자 화면 없이 사용하는 절차

- 별도 관리자 화면은 필수가 아니다. 분리 환경에서 권한이 지정된 계정과 승인된 세션 처리 절차로 Postman 등 관리 도구를 사용할 수 있다. 일반 사용자 화면/FE 코드는 변경하지 않는다.
- 조회 권한만 먼저 준비해 주문·환불·원장과 지갑 상태를 읽는다. 복구가 필요하면 복구 권한, POST CSRF, 사유와 작업별 Idempotency-Key를 준비한다. 재조회는 같은 commandId를 사용한다.
- 요청/응답의 traceId와 감사 기록을 대조하고 COMPLETED만이 아니라 resultCode를 확인한다. REVIEW_REQUIRED는 수동 잔액 수정이나 재결제 지시가 아니다.
- 세션 쿠키/CSRF/키를 채팅·Git·공유 Postman 환경에 올리지 않는다. 실제 관리자 지정·기능 활성화·PG 사용은 이번 PR에서 하지 않았다.
- DB 도구는 읽기 전용 대조에 사용한다. 잔액/주문/감사 행을 직접 변경하여 권한·원장·복구 API를 우회하지 않는다.

### 기존 검증 기록

- PR #25 최초 Backend CI는 동시 command 등록의 FK KEY SHARE와 완료의 order/member FOR UPDATE 사이 교착으로 1건 실패했다. `45d292d`에서 등록도 order-first로 맞추고 member/grant 잠금을 FOR NO KEY UPDATE로 보완했다. 실패를 단순 재실행으로 숨기지 않았다.
- 새 PostgreSQL DB saju_admin_merge_20261012에서 check/bootJar 전체 재실행 성공: 496건 중 488 통과, 8 선택 제외, 실패/오류 0. 같은 주문 동시 처리 10회와 member FK KEY SHARE 공존 검사를 포함한다. 권한 철회·감사 rollback·중복 지급 방지 테스트도 함께 통과했다.

- 새로운 PostgreSQL 17.11/55432 DB saju_admin_verified_20261012에서 check/bootJar/exportAdminOpenApi 성공.
- 전체 486건 중 478 통과, 실패/오류 0, 선택 제외 8. 기존 선택 제외 7 + 관리자 export 1이며 관리자 export는 별도 task에서 성공했다.
- 최종 GET/HEAD 권한 처리 후 관리자 묶음 19건 중 18 통과, 선택 export 1 제외, 실패 0. 별도 export 2건 성공, bootJar 성공.
- migration 33개 모두 success=true. 권한/가짜 ROLE 무시/철회/탈퇴, 가상 실제 세션 쿠키·CSRF, pagination/금융 정보 최소화/조회 감사 실패 차단을 검사했다.
- 명령 replay/본문 키 충돌/두 작업 동시 지급/잘못된 증거/3회 한도/lookup 중 권한 철회/stale lease/금융+감사 롤백/예약 환불 종료/불명 환불 유지/원장 이상 중단을 검사했다.
- 실제 카카오 OAuth/실제 운영자 계정/실제 PG/브라우저 관리 도구/OS 종료/다중 인스턴스 부하/백업 복원/배포는 미실행이다.
- 코드 커밋: bf19607 조회·권한·감사, 4eceb99 재처리·진단·계약 검사. 문서는 마지막 커밋으로 분리한다.

## 남은 범위

2026-10-12 사용자 정책 정정: 환불 신청 시 주문 생성 후 7일 이내·해당 주문의 유료/보너스 모두 미사용이면 전액 환불하고 보너스도 회수한다. 신청 없는 자동 환불과 부분 환불은 제공하지 않는다. 신청 뒤 미완료 작업의 복구는 별개다.
가상계좌 환불 계좌, 운영 live adapter와 실제 승인/환불 시험은 별도다. 부분 환불은 추가 개발 예정 범위로 취급하지 않는다.
불명 환불 회수분의 강제 반환, 제한 해제, 임의 잔액 조정, 역할 관리 UI/API, 알림 ACK/수동 재전송/보존 정책은 미구현이다.
이 API의 자동 검증 통과가 운영 배포/활성화 승인을 의미하지 않는다.
