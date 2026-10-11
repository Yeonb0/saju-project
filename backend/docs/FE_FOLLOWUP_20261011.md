# FE/B 후속 전달 (2026-10-11)

FE/B 회신 확인 후 A 측 수용 기준을 정리했다. 아래 합의와 실제 API 제공 여부를 구분해 주세요.
자세한 계약은 [회신 반영 기록](TEAM_CONTRACT_DECISIONS_20261011.md)에 있다.

## FE에 전달할 내용

- Q-38: 오류 code만으로 환급 완료 표시 금지. B가 원거래 보상 receipt를 확인한 REFUNDED에서만 표시한다.
  새 조회 DTO에서는 compensationStatus=COMPLETED도 함께 확인하고 모순된 응답은 완료로 표시하지 않는다.
  FULFILLED+readingId일 때만 결과로 이동한다. FE main PR #18에서 두 표시 조건의 수정은 확인했다.
- 구매 조회는 B가 GET /reading-purchases/{purchaseId}와 /reading-purchases/by-quote/{quoteId}를 구현한다.
  DTO는 purchaseId/quoteId/status/readingId/compensationStatus/failureCode/nextAction/nextRetryAt/updatedAt이다.
  잔액은 GET /wallet 사용, 미완료 readingId는 null, 기존 구매 조회는 quote 만료와 무관하다.
  두 endpoint는 아직 미구현이다. OpenAPI 병합 전 실제 호출하지 않는다.
- B 권고 polling을 A 기준으로 수용했다: 처음 30초는 2초, 이후 5초, 총 2분.
  2분 후에는 실패/환급 단정 없이 자동 조회만 종료하고 수동 확인을 제공한다.
  REVIEW_REQUIRED/CONTACT_SUPPORT에서도 환급 완료를 표시하지 않는다.
- 브라우저는 원래 quote/키를 유지하고, 응답 유실 시 기존 구매를 먼저 조회한다.
  최소 선택값 저장/TTL/키 유지 범위는 현재 FE 충전 복귀 저장과 B안을 맞춰 주세요.
  원래 POST 본문을 복원할 수 없다면 새 본문을 만들지 않는다. 생년 원문·이름·세션 토큰 저장 금지.
- 궁합 관계는 엄마/아빠=FAMILY, 애인=LOVER, 직접 입력=OTHER이며 문자열은 전송하지 않는다.
  API_SPEC 2장에 반영했다. Q-26 질문 문구 결정과는 별개다.
- Q-28 A 신규 필드 기준: productName, regularPrice({currency, amount} 또는 null).
  기존 price/charged는 실제 판매/차감값이다. 새 필드는 미구현이며 생성 타입에 임의로 넣지 않는다.
- GET /products?category=TOP_UP은 A 로컬에 추가했지만 main 미병합이다.
  현재 판매 차단 상태의 items=[]는 정상이다. FORTUNE/비활성 소개 목록은 아직 대기다.
- 토스 승인/조회 adapter는 A 로컬 내부 기능만 추가됐다. confirm HTTP/지급 후 응답은 아직 없고,
  충전 완료는 여전히 서버 CREDITED 확인 후에만 표시한다. 예정 지급량으로 완료를 표시하지 않는다.
- 원화 amount+currency 매핑/null 유지, 생성 타입/오행/충전 adapter 준비는 회신 및 코드에서 확인했다.
  실제 port 선택은 아직 mock이다. origin/boyeon의 수능 adapter도 main 미병합 단계다.

## B에 전달할 내용

- 구매 상태 GET/재개/lease+fencing/늦은 결과 차단, FAILED 복구 오케스트레이션 유지 책임은 B로 수용한다.
  A는 원장/receipt/안전한 보상 명령을 제공한다. 이번 문서 작업에서 B 코드를 변경하지 않았다.
- 읽기 포트 필요성을 수용한다. 보상 조회는 originalTransactionId 단독 대신
  userId+quoteId+originalTransactionId로 연결 검증하는 A 보완안을 확인해 달라.
- 상태 GET DTO와 기존 구매 POST DTO는 구분하고, 전체 status/compensationStatus/nextAction 조합,
  차감 없는 FAILED와 GENERATING 상태 불명 시 문의 전환 조건을 명시해 달라.
- Q-37 수능 구조화 항목, 절기 경계 오류 통일, 실제 세션/CSRF/인물 및 Q-31 API는 별도 답변/구현 대기다.

## 제공 일정/정책

staging 고정 FE/BE 주소·제공일·팀 상점 테스트키 인계일은 아직 확약할 수 없다.
10/12 사이트·10/14 전후 심사는 목표이며 실제 인증/충전/정책 준비 여부 확인이 먼저다.
충전 동의/문구/CS/14세·가입 동의/신살 판매/수능 구조화의 1차 범위와 법적 표기는 PD/해당 담당 답변 대기다.
이번 회신 검토는 테스트/E2E/배포를 실행한 결과가 아니다.
