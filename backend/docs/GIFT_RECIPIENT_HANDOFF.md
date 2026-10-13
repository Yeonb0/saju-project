# A 선물 접수·최초 제공·보관 연결 계약

정책: 구매의 실제 차감 시각부터 90일 미만에 서버가 유효한 이용 신청을 영속 접수하면, 그 기존 요청은 만료 후에도 처리한다. 24시간은 신청 권리 소멸이 아니라 운영 확인 시점이다. 전화번호 30일 발송 종료는 별도로 유지한다.

## 이번 A 구현

- `GiftFulfillmentPort.accept`는 유효 token과 ACTIVE 선물을 같은 work 잠금 아래 확인하고 수신 신청 receipt를 저장한다. receipt에는 ID·HMAC 요청 대응·시각·상태만 있으며 생년·이름·전화번호 원문을 넣지 않는다.
- 동일 gift/requestId/snapshotHash는 기존 접수를 재사용하고 입력이나 requestId 변경은 충돌이다. snapshotHash는 B가 정규화한 고정 수신 snapshot의 hash이고, A가 다시 안정적인 HMAC으로 보호한다. client가 보내는 hash를 신뢰해서 B 입력 검증을 생략하면 안 된다.
- receipt 접수와 `saveRecipient`의 DB 저장은 같은 REQUIRED 트랜잭션이다. B는 별도 커밋을 하지 않고, callback 안에서 외부 생성/알림톡/파일 전송을 하지 않는다. context는 B 모듈에서 최소 범위로 암호화 저장한다.
- 90일 후 token은 신규 신청을 열지 않는다. 기한 내 ACCEPTED/REVIEW_REQUIRED receipt가 있으면 동일 요청 확인·생성 재개 권한만 보호한다. 이미 제공된 콘텐츠는 고정 재열람 기한을 적용한다.
- 처리 중에는 기존 token 검증의 accessExpiresAt가 과거여도 보호된 receipt가 있을 수 있다. B는 token의 날짜 하나만으로 새 신청 허용 또는 기존 접수 거절을 판단하지 않고 내부 receipt 상태와 동일 요청 대응을 확인한다.
- `provide`는 receiptId/contentId와 B의 콘텐츠 제공 DB 확정 callback을 같은 work 잠금·트랜잭션에 처리한다. 최초 제공 시각과 이후 90일 access 기한을 저장한다. 동일 contentId replay는 기존 시각을 유지하고 다른 contentId는 거절한다.
- **생성 완료 자체를 최초 제공으로 찍으면 안 된다.** B는 완성된 결과를 실제 응답으로 제공하는 경계에서 `provide`를 호출한다. A가 네트워크 너머 사용자의 수신·열람 성공을 직접 관측하는 것은 아니므로, 서버의 제공 확정 기준을 사용한다.
- 취소와 최초 제공은 같은 work 행을 잠근다. 취소가 먼저면 늦은 제공은 거절하고, 제공이 먼저면 일반 미제공 취소가 거절된다. 지갑 반환은 기존 receipt 경로를 유지한다.
- 제공 확정 receipt·차감 식별자가 맞는 선물은 `WalletPurchaseSettlementPort`가 완료 거래로 판정한다. 활성화만 된 선물은 정산 완료가 아니다. B 실패 보상이 이 확정 거래를 되돌리지 않도록 연결해야 한다.
- `link`는 ACTIVE 회원과 유효한 제공 token으로 수신자 계정 하나에 연결한다. 다른 계정의 재연결은 거절한다. token 기한은 연장하지 않고, `owned`가 실제 수신자 계정의 회원 보관 접근을 판단한다. 화면/API에서는 반드시 인증 principal을 사용한다.
- 임의 부적 파일 권한은 여전히 fail-closed다. receipt의 contentId만으로 아무 파일 접근을 허용하지 않는다. B는 reading/talisman 소유권·gift 연결을 검증해야 한다.

## 운영 확인

- `GIFT_FULFILLMENT_REVIEW_ENABLED=false` 기본값. 켜면 기한 내 접수 뒤 24시간이 지나도 최초 제공되지 않은 요청을 REVIEW_REQUIRED로 기록한다. 이것이 자동 생성 worker나 환급 실행은 아니다.
- 행마다 독립 트랜잭션·감사를 사용한다. 검토 기록을 반복 생성하지 않는다. 검토 상태여도 동일 접수의 처리·제공을 금지하거나 token을 폐기하지 않는다.
- 고객에게 무한 대기를 약속하지 않는다. B의 제한 생성 시도·lease·결과 저장·실패 분류와 운영 알림/재처리를 연결해야 한다. 이번 A worker가 실제 Liner를 호출하지 않는다.

## 개인정보 정리

- 미접수 ACTIVE는 90일 기한에 삭제 대상으로 전환한다. 기존 추가 24시간 전체 보관은 제거했다.
- 기한 내 접수돼 ACCEPTED/REVIEW_REQUIRED인 요청은 처리 중 자료를 먼저 삭제하지 않는다. 해당 운영 건을 해결하지 않은 채 장기 방치하는 것은 지원 완료가 아니다.
- 비회원 제공 완료는 최초 제공 후 90일에 A 메시지/이름 등 전체 draft를 삭제 대상으로 삼는다. 목적 종료 뒤 임의의 추가 보관기간을 적용하지 않는다. 만료 초안·취소/반환/실패 확정 주문도 정리 대상이다.
- 수신자 계정에 연결된 선물은 회원 보관을 유지하며, 해당 회원이 WITHDRAWAL_PENDING/WITHDRAWN이면 기한 전이어도 A 개인정보를 바로 삭제 대상으로 삼고 계정 접근을 거절한다. 법정 금융 기록·정산 receipt는 유지한다.
- 구매자와 수신자의 계정은 다를 수 있다. 구매자 탈퇴만으로 다른 회원에게 귀속된 선물을 삭제하지 않는다.
- `GIFT_RETENTION_ENABLED=false` 기본값은 유지하고 기존 임의 보관기간 설정은 제거했다. worker 기본 1분, 오류 최대 5회 뒤 자동 후보 제외·운영 검토. 실제 worker 활성화/모든 사본 삭제 완료는 이번 코드 검증과 별개다.
- B의 생년정보·콘텐츠 DB·파일 삭제는 A가 임의 테이블을 수정해 구현하지 않았다. B의 저장·파기 callback/worker가 같은 정책 시점을 따라야 한다. 회원 정보의 기존 30일 익명화도 B 담당이며 아직 변경하지 않았다.

## 배포·계약 경계

- 신규 `V202610132800__a_gift_recipient_receipts.sql`. 이전 게시 migration은 수정하지 않는다.
- 내부 port만 추가했다. 공개 선물 수신 HTTP·CSRF 예외·FE 화면·공유 OpenAPI/타입은 변경하지 않는다. 실제 B 연결 전 수신 기능 전체 완료라고 표시하지 않는다.
- 현재 local/test 제한과 기본 운영 비활성을 유지한다. 실제 알림톡 adapter·템플릿·PG 법정 예외 환불·미성년 동의 확인은 별도 미완료다.
- PD 수정 요청은 `PD_POLICY_V2_REVISION_REQUEST.md`를 따른다. 테스트 실제 결과는 `docs/OPERATIONS_CHECKLIST.md`의 후속 기록에 남긴다.
