# BE-A 충전 환불·대사·지갑 운영 전달

기준: 2026-10-12, main `7bac2a1`에서 분리한 `codex/payment-refund-reconciliation`.
관리자 API·권한·수동 잔액 조정과 선물은 제외한다. 실제 PG/FE 공동 테스트와 배포는 미실행이다.
FE 소스·생성 타입·설정과 기존 `docs/openapi/api-v1.json`은 변경하지 않았다.

## 구현 범위와 정책 경계

- 충전 주문의 소유자만 전액 미사용 환불을 요청하고 조회할 수 있다.
- 자동 요청 범위는 주문 생성 후 7일 이내, 유료·보너스 지급분이 모두 미사용·미만료인 경우뿐이다.
- 사용 후 구매 보상으로 수량이 복구됐더라도 사용 이력이 있으면 자동 현금 환불을 거절한다.
- 2026-10-12 사용자가 주문 생성 후 7일 이내·유료/보너스 모두 미사용인 전액 자동 환불 범위를 확정했다. 이는 실제 PG 연결·대외 원고·예외 정책·배포 검증 완료나 기능 활성화를 뜻하지 않는다.
- 차감은 만료가 빠른 지급분 먼저, 만료가 같으면 보너스 먼저이며, 같은 종류/만료이면 먼저 지급된 순이다. 지갑 총 잔액은 paidBalance/bonusBalance, 이번 충전 수량은 주문의 paidShellAmount/bonusShellAmount를 사용한다. 지급 완료는 CREDITED로 확인한다.
- 부분 환불 금액·수수료·사용한 보너스 처리, 가상계좌 환불 계좌 입력, 운영 live 키 지원은 미구현이다.
- 검토 상태의 수동 해제와 이전 결과 불명이 있는 회수분의 반환은 관리자 후속 범위다. UNKNOWN을 실패로 단정해 반환하지 않는다.
- 첫 취소 시도의 명시적 REFUND_REJECTED와 재조회 DONE/동일 결제 증거가 함께 확인되면 회수분을 원래 lot·만료로 한 번 반환한다. NOT_CANCELABLE_PAYMENT 같은 모호한 오류는 반환 근거가 아니다.

## 트랜잭션과 중복 처리

1. 주문을 잠그고 소유권·정책·승인 식별 정보를 검사한다.
2. 같은 지갑/지급분 잠금으로 구매·만료·다른 환불과 직렬화한다.
3. 대상 지급분 회수, 음수 REFUND 원장, 잔액 갱신, RESERVED 환불, 요청키 대응을 함께 저장한다.
4. DB 잠금 밖에서 토스 조회와 취소를 실행한다. 원문 paymentKey는 PG 조회 응답에서 일시적으로만 사용한다.
5. 기존 주문·결제키 해시·금액·통화와 일치하는 전액 CANCELED 증거를 확인해야 REFUNDED와 주문 REFUNDED를 함께 저장한다.

회수 원장이 존재한다는 사실만으로 현금 환불 완료가 아니다. 회수분은 취소 여부가 불명인 동안 사용하지 못하게 유지한다.
같은 주문에 다른 요청키를 사용해도 같은 환불을 반환한다. 모든 별칭 요청키를 영속 저장하며 다른 주문에 재사용하면 409다.
토스 취소 멱등키는 환불 ID로 고정한다. 응답 유실 후에는 먼저 조회하며, 여전히 전액 DONE인 경우에만 같은 취소 명령을 재시도한다.
실행 lease/token으로 늦게 도착한 응답을 거절하고 최대 5회 뒤 REVIEW_REQUIRED/CONTACT_SUPPORT로 전환한다.
재시작 재개는 저장된 작업과 lease를 통해 지원한다. OS 강제 종료와 실제 PG 응답 유실은 이번에 실험하지 않았다.

## FE 연결 계약

- `POST /api/v1/top-up-orders/{orderId}/refund`: 로그인, CSRF, 환불 의도별 `Idempotency-Key` 필요. 금액·결제키·환불 사유 본문은 받지 않는다.
- `GET /api/v1/top-up-orders/{orderId}/refund`: 소유자 조회. GET은 PG 호출이나 재처리를 실행하지 않는다.
- 응답 껍데기는 기존 `data`/`traceId`, 필드는 `refundId`, `orderId`, `state`, `amount`, `nextAction`, `completedAt`이다.
- `amount`는 원래 주문의 전액 요청 금액이다. 미완료/검토 상태에서 실제 환불된 금액으로 표시하지 않는다.
- POST는 RESERVED/IN_FLIGHT/RETRY에서 202, REFUNDED/REVIEW_REQUIRED에서 200이다. HTTP 200만으로 완료 처리하지 않는다.

| state | nextAction | FE 표시 기준 |
|---|---|---|
| RESERVED / IN_FLIGHT / RETRY | POLL | 처리 중, 같은 환불 GET 조회 |
| REFUNDED | NONE | completedAt도 있는 경우만 현금 환불 완료 |
| REVIEW_REQUIRED | CONTACT_SUPPORT | 완료·실패·회수분 반환을 단정하지 않고 주문 ID로 문의 |
| REJECTED | CONTACT_SUPPORT | 현금 환불 거절 확인 및 예약 회수분 반환. 현금 환불 완료로 표시하지 않음 |

REJECTED는 첫 시도, 유효한 lease, 동일 주문/결제키 해시/금액, 명시적 거절을 모두 확인한 경우만 반환한다.
이전 UNKNOWN 시도가 있거나 lease가 바뀌었으면 자동 반환하지 않는다. 반환의 ADJUSTMENT 원장/receipt와 거절 resolution을 함께 저장한다.
만료된 lot도 원래 만료일을 유지하므로 반환이 사용 기한 연장을 의미하지 않는다. 반환 후 외부 취소가 발견되면 지갑을 다시 검토 제한한다.
REJECTED일 때 충전 주문의 공개 상태는 다시 CREDITED이며, 현재 사용 가능 잔액은 지갑 API로 조회한다.

충전 주문 GET/confirm은 환불 미완료/검토가 있으면 응답용 `REFUND_PENDING` 상태와 null 지급량·잔액을 반환한다.
이는 DB 주문 상태를 새 enum으로 바꾸는 것이 아니라 환불 행을 결합한 공개 projection이다.
기존 FE adapter는 모르는 상태를 UNKNOWN으로 받아 CREDITED 완료 표시를 하지 않는다. nextAction은 기존 POLL/CONTACT_SUPPORT를 재사용한다.
다만 명시적인 REFUND_PENDING 화면·새 환불 adapter·버튼·생성 타입 반영은 FE 담당 작업이다. 이번에는 FE 파일을 수정하지 않았다.
`REFUND_NOT_ELIGIBLE`, `WALLET_REVIEW_REQUIRED`는 409이며 feature/PG adapter 미준비는 503이다. 타인 주문은 404다.

새 계약은 `backend/docs/openapi/payment-operations-v1.json`에 별도 제공한다. 기존 FE 타입과 공유 snapshot은 유지했다.
backend 디렉터리에서 다음 명령으로 별도 계약을 다시 생성할 수 있다.

```powershell
.\gradlew.bat exportOpenApi '-PopenApiOutput=docs/openapi/payment-operations-v1.json'
```

FE의 기존 CSRF/HTTP/envelope/요청키 방식과 코드상 호환성을 대조했다. 실제 브라우저·세션·서버 간 통합 성공은 확인하지 않았다.

## Webhook와 대사

- `POST /api/v1/webhooks/toss`, 기본 비활성. PAYMENT_STATUS_CHANGED만 처리하며 그 외 이벤트는 무시한다.
- 로그인/CSRF 예외는 이 POST 경로에만 적용한다. 스트림을 최대 16 KiB + 1 byte까지만 읽어 초과 요청을 거절한다.
- 일반 결제 Webhook 본문을 인증된 금융 증거로 취급하지 않는다. 알려진 주문에 대해서만 PG 조회 작업을 영속 큐에 추가한다.
- 주문당 큐 행은 하나다. 반복/위조 신호는 재시도 횟수나 lease를 초기화하지 않는다. 원문 이벤트·paymentKey는 저장하지 않는다.
- worker는 실제 PG 조회의 결제키 해시·금액·통화·주문을 검사한다. 미지급 주문은 기존 충전 lease/지급 경계를 사용한다.
- 일치하는 지급은 CHECKED, 전액 외부 취소의 미사용분은 회수 후 REFUNDED로 수렴한다.
- 이미 사용한 외부 취소나 검증된 부분 취소는 REVIEW_REQUIRED로 남기고 해당 지갑의 변경과 신규 충전을 제한한다.
- 부분 취소에서 환불 금액이나 회수량을 임의 계산하지 않는다. 검토 지갑의 표시 잔액은 남아 있어도 사용할 수 없다.
- 주기적 주문 스캔은 cursor로 100개씩, 조회 작업도 100개씩 처리한다. 정상 조회는 한 시간 후 재확인, 불명은 최대 5회다.
- 공개 신호는 기존 CHECKED 작업을 즉시 깨우지 않는다. 외부 변경 반영에는 다음 주기까지 지연이 있을 수 있다.

## 지갑 운영

- 지급분 잔량과 지급분별 원장 합계, 거래 총량과 배분 합계, 사용 가능/만료분을 고려한 잔액 projection을 검사한다.
- 불일치는 wallet_integrity_findings에 기록한다. 잘못된 원장을 자동으로 고치거나 만료 처리를 계속하지 않는다.
- 정상 지갑의 만료분은 잔량 0, 음수 EXPIRY 원장, 잔액을 함께 저장한다. 동시 실행·재실행에도 추가 소멸이 없다.
- 외부 취소 검토 표시는 자동 해제하지 않는다. 관리자 검토·부하/복원 검증은 후속이다.

## 운영 알림

- 환불/충전 검토, 15분 이상 미처리 작업, 결제 대사 검토, 지갑 원장 이상을 operations_incidents에 기록한다.
- 문제당 OPEN episode/generation으로 중복 감지를 합치고, 문제가 해소되면 RESOLVED, 재발하면 새 generation을 만든다.
- 감지와 전송 예약은 함께 저장한다. 알림은 DB 밖에서 전송하며 lease, 30초 retry, 최대 5회와 REVIEW_REQUIRED를 적용한다.
- 해결된 문제의 미전송 알림은 취소한다. 100개 감지/해결 scan과 20개 전송으로 한 번의 작업량을 제한한다.
- HTTPS/Bearer 인증 수신기로 alertId, kind, resourceId, generation, detectedAt만 전송한다. 결제키/키 원문/금액/이름/외부 오류 본문은 보내지 않는다.
- 전송은 at-least-once다. 수신기가 alertId/Idempotency-Key로 중복 제거해야 하며 DELIVERED는 수신 서버의 접수이지 담당자의 열람/해결 확인이 아니다.
- 실제 Slack/메일/문의 시스템은 연결하지 않았다. 별도 신뢰 수신기·토큰·접근 정책이 필요하다. 관리자 화면/API, 경보 승인·수동 재전송은 후속이다.

## 활성화와 배포

다음 네 설정은 application.yml에서 기본 false다. 사용자 .env를 수정하거나 worker/실제 PG를 켜지 않았다.

```ini
TOSS_REFUNDS_ENABLED=false
TOSS_WEBHOOKS_ENABLED=false
TOSS_OPERATIONS_ENABLED=false
WALLET_OPERATIONS_ENABLED=false
OPERATIONS_ALERTS_ENABLED=false
OPERATIONS_ALERTS_ADAPTER=disabled
```

환불 자동 복구·Webhook 처리는 TOSS_OPERATIONS_ENABLED가 켜져야 진행된다. 정책·키·상품·FE가 준비된 분리 환경에서만 활성화한다.
운영 알림은 OPERATIONS_ALERTS_ENABLED, webhook adapter, OPERATIONS_ALERTS_WEBHOOK_URL/TOKEN을 설정해야 전송된다. 기본은 비활성이다.
OPERATIONS_ALERTS_STALLED_AFTER는 기본 15m이며 1분~7일만 허용한다. 실제 처리 시간/worker 간격에 맞춰 조정한다.
새 migration 6개는 추가형이다. 모든 인스턴스를 새 코드로 바꾼 뒤 기능을 켜야 한다.
환불 예약 이후에는 이전 코드가 해당 상태를 모르므로 구버전 혼합 운영/단순 앱 rollback을 하지 않는다. 외부 취소는 DB rollback으로 취소할 수 없다.

## 실제 검증 결과

- main 7bac2a1 기반, 전용 PostgreSQL 17.11/55432의 새 saju_payment_ops_final_20261012에서 전체 check/bootJar/exportOpenApi 성공.
- 전체 447건: 440 통과, 실패/오류 0, 선택 제외 7. Liner 실호출 1, B 전용 PG 복구 4, 선택 export 1, 지갑 시연 1이며 선택 export는 별도 task에서 성공했다.
- 최종 입력 제한/응답 계약/취소 금액 overflow 보완 후 금융 관련 69건 모두 통과, bootJar 및 별도 OpenAPI export 성공.
- migration 29개 모두 success=true. 이전 테스트 DB의 B 고정 fixture 충돌은 기존 DB 삭제 없이 새 DB를 사용해 분리했다.
- 중복/별칭/다른 주문 키 경쟁, PG 대기 중 재요청, reservation/finalization DB 롤백, lease 만료·늦은 응답, 조회 복구·5회 한도, 위조 Webhook, 외부/부분 취소 검토, 만료 동시 실행·원장 이상을 검사했다.
- 실제 토스 키 사용/PG 요청, 실제 로그인, FE E2E, OS 종료 복구, staging/production 배포·부하·복원·알림은 미실행이다.

토스 계약 참고: [결제 취소](https://docs.tosspayments.com/guides/v2/cancel-payment), [Webhook 이벤트](https://docs.tosspayments.com/reference/using-api/webhook-events).

### 2026-10-12 추가 구현 검증

- 환불 거절 반환 `cbf0767`, 운영 알림 `5917414`를 같은 PR #24에 추가했다. 기능이 같은 범위라 merge하지 않고 이어갔다.
- 새 전용 PostgreSQL 17.11 DB saju_ops_followup_20261012에서 check/bootJar/별도 exportOpenApi 성공: 467건 중 460 통과, 실패/오류 0, 선택 제외 7.
- 최초 거절/이전 UNKNOWN/만료 유지/반환 롤백/반환 뒤 외부 취소, 알림 동시 감지/5회 한도/늦은 응답/해결·재발/100개 초과 공정한 scan/HTTP 오류·안전 설정을 검사했다.
- FE 코드·기존 공유 OpenAPI·사용자 .env·실제 알림 수신기·배포는 변경하지 않았다. 관리자/부분 환불 정책/live PG/실제 팀 통합은 여전히 후속이다.
