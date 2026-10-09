# FE 요청 Q-33~36 구현 및 연결 안내

기준: `main` 381787b에서 시작한 `codex/fe-quote-wallet-contract` 작업. 이 문서는 로컬 구현과 남은 계약을 구분한다. 서버 배포나 실제 FE 연결 완료 보고가 아니다.

## Q-34: 견적·지갑

| 요청 | 이번 변경 | 남은 작업 |
|---|---|---|
| 보유·구매 후 잔액·부족·추천 | 일반 5종과 수능운 견적 POST에 추가 | 실제 인물 adapter와 세션 연결 |
| 사용 등껍질 이름 통일 | `charged {currency, amount}` | FE는 수능의 최상위 currency/amount 대신 charged로 변경 |
| 견적 재조회 | `GET /api/v1/quotes/{quoteId}` | FE adapter에서 충전 복귀 시 사용 |
| 유료·보너스 지갑 잔액 | `GET /api/v1/wallet`에 paidBalance/bonusBalance/balance | 만료 예정 상세·원장 HTTP 목록은 후속 |
| 충전 지급량 | 주문 도메인에 paidShells/bonusShells 스냅샷은 이미 존재 | 실제 주문·승인·지급 서비스와 HTTP 응답 구현 필요 |
| 500 상품 표 수정 | API_SPEC을 500+80=580으로 수정 | 실제 결제 준비 전 상품은 비활성 유지 |

견적 공통 자금 필드는 `walletBalance`, `balanceAfter`, `shortage`, `recommendedTopUp`이다.
부족하면 balanceAfter=null, 단일 추천이 없으면 recommendedTopUp=null이다. 추천 null은 잔액 충분을 의미하지 않으므로 shortage를 함께 확인한다.
GET은 저장 가격·만료를 유지하고 현재 잔액·부족분·추천만 다시 계산한다. 옵션/질문/event는 POST별 응답에 있고 GET에는 없다. 이름·원문 생년정보·context hash는 GET에 없다. productName과 정가/상품 metadata는 아직 별도 후속 계약이다.

이 조회는 DB read-only 서비스이며 원장, lot, 견적 구매 연결을 변경하지 않는다. 결제 팝업 숫자는 잔액 예약이 아니므로 실제 구매 서비스는 잔액과 판매 가능 여부를 다시 검증해야 한다. 조회를 위해 DB를 잠그거나 외부 결제/생성 API를 호출하지 않는다.

타인/미존재 견적은 같은 404 RESOURCE_NOT_FOUND, 본인 만료는 409 QUOTE_EXPIRED다. 전체 경로는 인증 필요하며 견적 POST에는 CSRF가 유지된다. OAuth는 이번 작업으로 구현되지 않는다.

충전 주문 응답은 지급 준비 전에도 스냅샷의 paidShellAmount/bonusShellAmount/creditedShellAmount를 표시할 수 있지만, 이는 예정 수량이다. 실제 지급 완료 표시와 walletBalance는 지급 트랜잭션 커밋 후 CREDITED에서만 사용한다. 현재는 TopUpUseCase 구현체가 없어 해당 HTTP 계약을 성공하는 임시 구현으로 제공하지 않았다.

## Q-35: 오행분석·결과·오류·OpenAPI

- `POST /api/v1/fortune/basic`에 `{ "personId": "uuid" }` 입력을 추가했다. 소유자 문맥으로 ReadingSubjectPort를 조회해 서버에서 계산한다. 기존 원문 입력은 유지하되 두 입력을 함께 보내거나 입력이 불완전하면 400 VALIDATION_FAILED다.
- 인물 adapter 미연결은 503 READING_FULFILLMENT_UNAVAILABLE, 타인/미존재는 404 RESOURCE_NOT_FOUND다. 저장된 절기 경계일 인물의 시간 미상도 422 BIRTH_TIME_REQUIRED_AT_TERM이다.
- 결과 고정 필드 순서는 API_SPEC 8장의 생성 section 순서와 현재 category controller 매핑을 따른다. JSON 객체 키의 나열 순서에 의존하지 않고 FE가 카테고리별 명시적 순서로 매핑한다. summary를 요약 표시 후보로 사용하되 PD 제목·원고 확정은 별도다.
- counterpart 외 결과 section 필드는 content/sourceFactKeys 구조다. 현재 controller는 누락된 section을 null로 매핑한다. 수능 상세의 sections 배열 계약은 유지했다.
- relationType은 FAMILY/FRIEND/LOVER/CRUSH/WORK_SCHOOL/OTHER 계약 그대로다. 엄마/아빠 구분과 직접 입력 문구는 이 enum에 표현되지 않으므로 PD/B의 선택지 확정이 필요하다. 임의 입력 필드를 만들지 않았다.
- 미연결 dependency의 503은 제공 준비가 안 됐다는 의미다. 앞으로 실제 지갑 adapter의 결과 불명까지 “차감 없음”으로 일반화하면 안 된다. 같은 구매 의도/quote와 Idempotency-Key를 유지하고 서버 상태를 확인한다.
- PURCHASE_DEBIT_MISMATCH와 READING_GENERATION_FAILED는 보상 코드가 있지만 보상 자체가 실패할 수 있다. FE가 오류 code만으로 환급 완료를 선언하지 않는다. 영속 REFUNDED 상태 조회 및 보상 worker는 후속 구현이 필요하다.

OpenAPI 산출물은 `docs/openapi/api-v1.json`이다. 실제 Spring controller와 springdoc에서 생성하며 local/test 프로필의 fake Liner를 사용한다. 이 export는 인증을 우회하는 실제 서버를 띄우지 않고 MockMvc로 문서를 조회한다.

backend 폴더에서 다음 명령으로 재생성한다.

```powershell
.\gradlew.bat --no-daemon exportOpenApi
```

전체 API 목록에는 B의 기존 운세 API도 포함된다. 정의된 경로가 있어도 OAuth·인물·지갑 구매 adapter까지 준비됐다는 뜻은 아니다. staging 주소와 기동 일정은 아직 이 작업에서 확정하지 않았다.

## Q-33 / Q-36: PD·운영 결정 대기

결제 실패·취소 문구, 상품 유지, 필수 결제 동의와 서버 동의 기록, 가입 동의 단계, 만 14세 처리, 정책 원고 빈칸은 제품/운영 결정이다. 이번 구현으로 임의 확정하지 않았다. 현재 대기 상태를 유지하고 확정 답을 받은 뒤 화면·서버 기록을 함께 반영한다.

## 검증 기록

후속 A 작업으로 WalletPurchasePort DB adapter(차감·원장·중복 방지·원거래 복구)를 추가했다.
기본 비활성이며 실제 인물/세션과 구매 중단 복구 worker 연결 전 운영에서 활성화하지 않는다.
따라서 앞의 FE 실제 구매/충전 연결 대기 상태는 유지한다. 구현 및 테스트 범위는
`WALLET_PURCHASE_IMPLEMENTATION.md`와 운영 점검 기록을 참고한다.

최종 실행 결과는 `docs/OPERATIONS_CHECKLIST.md`의 최신 작업 기록에 남긴다. HTTP와 DB 통합 테스트는 소유권·만료·재조회 최신 잔액·만료/미래 lot 제외·조회 무차감, 생년 입력의 상호 배타와 절기 오류를 확인한다. 실제 OAuth, 실제 충전, FE 브라우저 E2E는 아직 검증 대상 구현이 아니다.
