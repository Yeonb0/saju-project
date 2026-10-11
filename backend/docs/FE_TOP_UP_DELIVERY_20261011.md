# FE 전달: 충전·오류 계약 (10/11 로컬 구현)

main 병합/배포 전 코드이며 실제 서버에 제공됐다는 뜻은 아니다. 재화 표시명은 **등껍질**이다.

## A 구현

- Q-28: TOP_UP/FORTUNE 상품 조회, 견적 productName/regularPrice snapshot 구현.
- Q-17: 주문 생성/본인 조회/승인, 승인 증거 검증, 원자적 지급, 중복 방지, 제한된 PG 조회 복구 구현.
- Q-18: ApiError.details(없으면 빈 객체), OpenAPI 공통 오류 응답 선언. 부족 잔액은
  required/balance/shortage/currency를 details에 제공한다. 지갑 자체가 없을 때는 빈 객체일 수 있다.
- 승인 Idempotency-Key는 코드와 OpenAPI 모두 필수다. 승인 금액은 양의 정수 JSON 숫자만 허용한다.
- Q-38 f: A 내부 WalletPurchaseReceiptPort 구현. B의 상태 조회/재개 코드가 연결된 것은 아니다.

## 충전 연결

1. POST /api/v1/top-up-orders에 productCode와 생성용 Idempotency-Key를 보낸다.
2. 반환 orderId/orderName/amount/currency로 PG 창을 연다. 원화 금액을 FE에서 재계산하지 않는다.
3. PG 복귀 후 POST /api/v1/top-up-orders/{orderId}/confirm에 승인용 Idempotency-Key와
   paymentKey/orderId/amount를 보낸다. 같은 승인 재전송에는 같은 키와 본문을 쓴다.
4. 202/processing=true이면 본인 GET /api/v1/top-up-orders/{orderId}로 확인한다.
   응답 유실/새로고침 때 새 충전 주문을 자동 생성하지 않는다.

모든 쓰기는 로그인/CSRF가 필요하고 타인의 주문은 404다. secret 키는 서버 전용이며 FE에 전달하지 않는다.

| 응답 상태 | FE 처리 |
|---|---|
| CREDITED / VIEW_WALLET | 충전 완료. grantedShellAmount와 walletBalance 사용 |
| PAYMENT_PENDING / POLL | 처리 중. 완료·실패·환불 확정 금지, 기존 주문 조회 |
| PAYMENT_PENDING / CONTACT_SUPPORT | 자동 확인 한도 초과. 주문 ID로 문의, 재결제 자동 유도 금지 |
| FAILED 또는 CANCELED / RETRY_PAYMENT | 확인된 거절/취소. PD 문구에 따라 새 결제 선택 제공 |
| PAYMENT_PENDING / NONE | 생성만 된 주문, 승인 실행 전 |

creditedShellAmount/paidShellAmount/bonusShellAmount는 **예정량**이다.
grantedShellAmount와 walletBalance는 CREDITED 이전 null이다. HTTP 200은 완료 판정이 아니다.
walletBalance는 지급 당시 잔액 snapshot이므로 현재 값은 GET /api/v1/wallet으로 갱신한다.
failureCode는 PAYMENT_DECLINED/PAYMENT_CANCELED/PAYMENT_REVIEW_REQUIRED이며 주문 결과다.
공통 HTTP 오류의 code와 구분하고 토스 원문 사유를 화면에 노출하지 않는다.

## FE 확인할 것

현재 BE adapter는 주문서형/결제창형 test_gsk 계열만 허용한다. 이에 맞춘 FE 키는 같은 세트의
**test_gck 클라이언트 키**다. test_gsk 시크릿은 서버 TOSS_SECRET_KEY 전용이며 FE/Git에 전달하지 않는다.
사용자 캡처의 위쪽 `_docs_` 키는 공개 문서 예제용이었다. 아래 test_ck/test_sk 개별 연동 키는
현재 adapter와 맞지 않으므로 그대로 섞어 쓰지 않는다. 팀 상점 키 종류/FE SDK 확인 후 실제 시험한다.
키를 설정하기 전 사용자에게 확인하며 실제 키를 이 문서/채팅/PR에 넣지 않는다.
근거: [토스 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys).

- 같은 주문 반복 승인/응답 유실/새로고침에서 주문 ID를 유지하고 완료 UI는 CREDITED만 사용하는지.
- 처리 중/수동 확인/확정 거절·취소를 구분하고 nullable 필드를 안전하게 다루는지.
- 테스트 client 키와 서버 secret이 같은 팀 상점/연동 종류인지. 키 원문은 회신 문서에 넣지 않는다.
- B 로그인/CSRF와 실제 adapter 준비 후 브라우저 연결을 함께 확인한다.

## 남은 범위

Q-20 선물 견적·주문·토큰·발송은 미구현이다. 기존 API_SPEC 선물 장은 원화 PG 결제 목표안이어서
최신 등껍질 결제 정책과 정합성을 먼저 맞춰야 한다. 기존 경로가 사용 가능하다고 연결하지 않는다.
실제 토스 테스트 결제, FE 브라우저 E2E, staging 고정 주소, 라이브 운영 활성화도 아직 확인하지 않았다.
webhook/현금 취소·환불/관리자 수동 대사는 후속이며 테스트 구현만으로 운영 준비 완료를 뜻하지 않는다.
