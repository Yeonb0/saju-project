# A 충전 구현과 검증 경계

## 구현

- POST /api/v1/top-up-orders: 인증 UUID, CSRF, Idempotency-Key와 상품 코드로 주문을 생성한다.
- GET /api/v1/top-up-orders/{orderId}: 본인 주문 snapshot과 상태를 조회한다. 타인/없는 주문은 같은 404다.
- 가격/지급량은 CatalogUseCase의 판매 가능 상품에서 가져온다. 클라이언트 금액/수량을 저장하지 않는다.
- paidShellAmount/bonusShellAmount/creditedShellAmount와 amount/currency/orderName을 반환한다.
- PAYMENT_PENDING의 총 지급량은 예정량이다. 원장/lot/잔액을 변경하지 않으며 walletBalance도 반환하지 않는다.
- 실제 판매 상품 활성화는 하지 않았다. 현재 비활성 상품을 성공시키는 개발용 우회는 없다.

## 중복과 트랜잭션

top_up_orders의 사용자+키 SHA-256 unique가 동시 요청을 조정한다.
동일 상품은 같은 주문 snapshot을 반환한다. 가격/판매 상태가 바뀐 후에도 이미 생성된 주문을 재사용한다.
다른 상품은 409 IDEMPOTENCY_KEY_REUSED다. 키 scope는 충전 주문 생성이며 512자 제한이다.
생성의 INSERT는 5초 제한 독립 트랜잭션이다. 경쟁에서 진 INSERT의 롤백 후 새 트랜잭션으로 승자를 조회한다.
DB 저장 직후 HTTP 응답이 유실돼도 동일 키 재시도로 저장된 주문을 재사용한다.
이 단계에는 외부 호출/지갑 잠금/실제 자금 이동이 없다.

## 후속 개발

1. 저장 주문/사용자/금액과 paymentKey 대응 검증 및 승인 명령의 영속 중복 방지.
2. 토스 서버 adapter와 승인 결과 불명 조회/재처리. timeout을 FAILED로 확정하지 않는다.
3. 승인 확인 뒤 지급 lot/원장/잔액/주문 CREDITED를 한 짧은 트랜잭션으로 저장한다.
4. 승인 성공 후 서버 중단, 지급 실패, 동시 재승인/worker/webhook의 중복 지급 방지를 PostgreSQL에서 검증한다.
5. 테스트 키를 서버 환경으로 구성하고 실제 토스 테스트 승인/조회와 FE 연결을 별도로 검증한다.

현재 confirm/webhook/cancel/refund는 제공하지 않으며 TopUpUseCase 전체 구현이 완료된 것은 아니다.
토스 키나 실제 결제는 이번 단계에 사용하지 않았다. 지급 만료는 최신 확정 정책을 따라야 하며
ERD의 이전 180일 설명을 그대로 구현하면 안 된다(PENDING_DECISIONS/PRD의 보너스 1년과 차이가 있다).
요청/주문 기록 보존과 rate limit, 운영 알림도 후속이다.

## B 합의

충전 주문 개발은 B의 생성 상태 머신을 바꾸지 않는다. 구매 상태 조회/중단 재개/환급 완료 판정은
A_B_PURCHASE_HANDOFF.md의 제안을 B와 확정한 다음 연결한다.

## 테스트

TopUpOrderApiTest는 실제 DB와 보안 필터/HTTP를 사용하며 CatalogUseCase만 테스트 상품으로 대체한다.
snapshot/반복 요청/판매 종료 후 재전송, 키 충돌, 없는 상품, 소유권, 미인증/CSRF/UUID principal,
동시 요청의 주문 한 건, 사용자별 키 격리/해시 저장, 원장 미변경을 검사한다.
실제 토스 승인, 실제 상품 판매 활성화, OS 강제 종료, 다중 프로세스 부하는 검증하지 않는다.
실행별 실제 결과는 docs/OPERATIONS_CHECKLIST.md 9장에 기록한다.
