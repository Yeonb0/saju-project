# 뿌기사주 백엔드 A/B 역할 분담

> 버전: Draft v0.1
> 작성일: 2026-10-02
> 인원: 백엔드 2명(BE-A, BE-B)
> 출시 목표: 2026-10-31
> 참조: [`ERD.md`](../../docs/ERD.md), [`API_SPEC.md`](../../docs/API_SPEC.md), [`COMMON_RESPONSE_AND_ERROR_CODES.md`](../../docs/COMMON_RESPONSE_AND_ERROR_CODES.md)

## 1. 분담 원칙

- 하나의 Spring Boot 애플리케이션 안에서 모듈 경계를 나눈다. 두 사람이 별도 서버를 만들지 않는다.
- BE-A는 **돈·주문·선물 전달의 정합성**, BE-B는 **사용자·명리 계산·결과·미디어**를 주 책임으로 한다.
- 각 기능에는 구현 담당 1명과 필수 리뷰어 1명을 둔다. 공동 소유를 이유로 책임자를 비워 두지 않는다.
- 외부 API는 adapter 뒤에 격리하고 실제 vendor 응답을 도메인에 직접 전파하지 않는다.
- 정책이 미정인 항목은 상태·인터페이스까지만 만들고 임의 기본값을 제품 결정으로 굳히지 않는다.
- 결제, 지갑, 선물 토큰, 개인정보, Liner 근거 검증은 상대 팀원의 승인이 있어야 merge한다.

## 2. 한눈에 보는 역할

| 영역 | 구현 책임 | 필수 리뷰 | 핵심 결과물 |
|---|---|---|---|
| 프로젝트 골격·CI·환경 | BE-A | BE-B | Spring Boot, profiles, PostgreSQL, Railway, health check |
| 공통 응답·예외·멱등성 | BE-A | BE-B | response envelope, error code, idempotency filter/store |
| 보안·카카오 인증·세션 | BE-B | BE-A | OAuth, session, CSRF/Origin, 권한 검사 |
| 사용자·인물 정보 | BE-B | BE-A | 회원, 본인/타인 프로필, 암호화, 삭제 |
| 상품·견적 | BE-A | BE-B | 서버 가격, 상품 옵션, 판매 기간 |
| 등껍질·원장 | BE-A | BE-B | lot, 불변 원장, 잔액, 환급 |
| 토스 결제·환불 | BE-A | BE-B | 충전·선물 주문, 승인, webhook/대사, 환불 |
| 선물 주문·토큰·알림톡 | BE-A | BE-B | gift 상태, 안전한 token, outbox, 전송·재발급 |
| 만세력 계산 엔진 | BE-B | BE-A | 입력 정규화, 결정론적 계산, 50+ 검증 케이스 |
| Liner 문장 생성 | BE-B | BE-A | JSON 계약, 근거 검증, 재시도, 결과 재사용 |
| 유료/무료 운세 결과 | BE-B | BE-A | 6종 결과, 스냅샷, 조회·재열람 |
| 부적·R2 | BE-B | BE-A | 합성, 업로드, signed URL, 창고·claim |
| 카카오톡 공유 리소스 | BE-B | BE-A | 비식별 share payload·이미지·landing |
| 관리자·감사 로그 | BE-A | BE-B | 결제/원장/선물 운영 API, 감사 기록 |
| 관측·장애 알림 | BE-A | BE-B | Sentry, 결제·원장·알림톡 alert |
| OpenAPI·FE 계약 | 영역 담당 | 상대방 | springdoc, 예시, generated type 호환성 |

## 3. 패키지 경계

권장 구조는 다음과 같다.

```text
backend/src/main/java/.../sajuppugi/
├── common/                 # BE-A 주도, 양쪽 사용
│   ├── api/                # envelope, ErrorCode, advice
│   ├── idempotency/
│   ├── security/           # BE-B 주도
│   ├── crypto/
│   └── outbox/
├── auth/                   # BE-B
├── member/                 # BE-B
├── person/                 # BE-B
├── catalog/                # BE-A
├── wallet/                 # BE-A
├── payment/                # BE-A
├── gift/                   # BE-A
├── fortune/                # BE-B
│   ├── calculation/
│   ├── generation/
│   └── reading/
├── talisman/               # BE-B
├── share/                  # BE-B
├── admin/                  # BE-A, 기능별 service port 사용
└── infrastructure/
    ├── toss/               # BE-A
    ├── alimtalk/           # BE-A
    ├── kakaooauth/         # BE-B
    ├── liner/              # BE-B
    └── r2/                 # BE-B
```

모듈 간에는 상대 모듈의 repository를 직접 호출하지 않고 공개 application service/port를 사용한다. 예를 들어 `gift`가 결과를 만들 때 `ReadingGenerationPort`를 호출하며 `reading_results` repository에 직접 접근하지 않는다.

## 4. BE-A 상세 책임

### 4.1 기반·공통

- Gradle, Java 버전, Spring profiles(`local/staging/production`), PostgreSQL, Flyway 구성
- `/actuator/health`와 Railway 배포 설정
- 공통 성공 envelope, `ErrorCode`, `@RestControllerAdvice`, traceId/MDC
- `Idempotency-Key` 저장·검증·최초 응답 재사용
- transactional outbox 기반 작업 골격
- 상품·금액이 클라이언트 값에 의존하지 않도록 서버 조회 구조 확립

### 4.2 상품·견적

- 운세 6종과 본인 옵션 `READING_ONLY`, `READING_WITH_TALISMAN`
- 선물 옵션 `TALISMAN_ONLY`, `READING_WITH_TALISMAN`
- 일반 30/45, 수능 40/55, 부적 추가 15 등껍질
- 등껍질 충전 8종과 유료/보너스 구성
- 판매 가능 기간·수능 판매 종료·quote 만료와 가격 스냅샷
- 미정인 선물 원화 가격은 설정/seed 경계만 만들고 숫자를 임의 입력하지 않음

### 4.3 지갑·원장

- `wallets`, `wallet_lots`, `wallet_transactions`, `wallet_transaction_lines`
- 충전, 사용, 자동 환급, 환불 회수, 관리자 조정, 소멸의 불변 원장
- 낙관적 잠금 또는 `SELECT ... FOR UPDATE`로 중복 차감 방지
- 유료/보너스 lot과 유효기간 분리
- 원장 합계와 잔액 projection 대사 job·운영 경보
- 차감 우선순위 `TBD(P-02A)`는 전략 객체로 분리

### 4.4 토스 결제·환불

- 충전 주문·선물 주문 생성과 서버 금액 검증
- 토스 승인, 승인 중복 방지, 결과 대사, 안전한 provider 오류 매핑
- 결제 성공 후 충전 lot 지급 또는 선물 활성화
- 전액/부분 취소 구조와 실패 재처리
- 결제·환불·재화 공급 증빙 데이터 보관
- staging test 결제와 production 소액 실결제/환불 runbook

### 4.5 선물·알림톡

- 구매자 선물 주문, recipient 이름·전화번호·편지 암호화
- 32바이트 이상 Base64URL token 생성, 영구 저장은 hash만 사용
- 결제 승인 시 gift 활성화와 알림톡 outbox를 한 DB 트랜잭션으로 생성
- 알림톡 최초 발송·재발송·링크 재발급·webhook 멱등 처리
- 원문 token URL을 암호화 outbox에 한시 보관하고 성공/재시도 종료 후 삭제
- 공개 token 조회의 만료·폐기 응답과 경로 token 로그 마스킹
- 선물 환불, 수신자 입력 초기화, 감사 로그

### 4.6 관리자·운영

- 주문·결제·지갑 원장·선물·전달 상태 조회
- 환불·원장 조정·토큰 폐기/재발급·알림톡 재발송
- `ADMIN`, `CS` 권한 경계와 감사 사유 필수화
- 결제 승인 실패, 금액 불일치, 중복 차감, 알림톡 실패 급증 경보

## 5. BE-B 상세 책임

### 5.1 인증·세션·보안

- 카카오 OAuth authorize/callback/logout
- 서버 세션 쿠키 `HttpOnly + Secure + SameSite=Lax`
- 로그인 시 session rotation, 로그아웃 즉시 폐기, 30일 만료
- CSRF token과 Origin 검증
- 최대 5개 기기 세션 조회·폐기 기능; 초과 처리 방식은 `TBD(I-02)` 반영
- resource ownership 검사와 존재 숨김용 404 정책

### 5.2 회원·인물·개인정보

- 회원 조회·탈퇴 상태 전환·30일 이내 삭제 job
- 본인 1명, 타인 최대 10명 프로필
- 양력/음력/윤달, 시간 미상, 성별, 관계 검증
- 궁합 상대 입력과 타인 정보 입력 권한 확인
- 생년월일시·이름 application encryption과 HMAC 지문
- 관리자가 생년정보를 직접 수정할 수 없도록 service 경계 설정

### 5.3 만세력 계산 엔진

- 입력 정규화와 `Asia/Seoul` 변환
- 원국, 간지, 십성, 12운성, 합충형파해, 대운·세운 결정론적 계산
- 생성형 AI가 계산에 개입하지 못하도록 `CalculationFacts` schema 확정
- 지원하지 않는 입력과 시간 미상 처리
- 최소 50개 기준 생년월일시 케이스, 양/음력·절기 경계 회귀 테스트
- 자시·역법·띠 기준 미정 항목을 명시적 설정/버전으로 관리

### 5.4 Liner 해석 문장 생성

- 계산 JSON → Liner request DTO 매핑
- 원본 생년월일시·자유 메모 제거
- 허용 section, 누락 field, `sourceFactKeys` 전달
- JSON Schema, section 목록, 근거 경로, 금지 표현 검증
- Liner가 미제공 명리 값을 추론하면 결과 폐기 후 제한 재시도
- 제한 재시도 소진 시 계산 facts 기반 결정론적 안전 응답 생성 및 `LINER`/`FALLBACK` 모드 기록
- `generationKey` 잠금, 최초 성공 snapshot 재사용, 중복 외부 호출 방지
- `calculationVersion`, `generationVersion`, `contentVersion` 저장

### 5.5 운세 API

- 비로그인 오늘의 운세의 결정적 일별 결과
- 일반 5종과 수능 1종 구매 결과 생성·조회·재열람
- 궁합 2인 입력, 선택형 관심 항목, 수능 이벤트일 검증
- 수능 교시별 흐름·도시락/간식·준비물·행운 아이템 구조화 응답
- 실패 시 BE-A의 지갑 보상 port 호출
- 신살 상세 schema는 `TBD(F-03)` 전까지 interface와 feature flag만 제공

### 5.6 부적·R2·공유

- 12지·운세별 부적 카탈로그와 손그림 에셋 조합
- 원본 1080×1920 PNG, 썸네일 360×640 WebP, 공개 공유 에셋 생성
- private R2 저장과 10분 signed URL
- 결과 포함 부적, 결과 후 15 등껍질 추가 구매 fulfillment
- 부적 창고, 동일 종류·동물 `×N`, 선물 부적 claim
- 개인 사주/부적 카카오톡 공유용 비식별 리소스와 landing
- 공유 범위·만료·철회 `TBD(G-11)`를 정책 인터페이스로 격리

## 6. API 소유권

### BE-A 구현

| 그룹 | endpoint |
|---|---|
| 상품·견적 | `GET /products`, `POST /reading-quotes`, `POST /gift-quotes` |
| 지갑 | `GET /wallet`, `GET /wallet/transactions` |
| 충전 | `POST /top-up-orders`, `POST /top-up-orders/{id}/confirm`, 주문 조회 |
| 선물 주문 | `POST /gift-orders`, `POST /gift-orders/{id}/confirm`, `GET /gift-orders*` |
| 전달 | `POST /gift-orders/{id}/deliveries`, `POST /gift-orders/{id}/link-reissues`, `POST /webhooks/alimtalk` |
| 환불 | `POST /gift-orders/{id}/refunds`, 충전 환불, 관리자 환불 |
| 공개 선물 | `GET /gifts/{token}`의 token·상태 부분 |
| 관리자 | 주문·결제·지갑·선물·알림톡·감사 로그 API |

### BE-B 구현

| 그룹 | endpoint |
|---|---|
| 인증 | `/auth/kakao/authorize`, `/auth/kakao/callback`, `/auth/logout`, 세션 조회·폐기 |
| 회원·인물 | `/me`, `/people*`, 탈퇴 |
| 무료 운세 | 오늘의 운세 생성·조회 |
| 유료 운세 | `POST /reading-purchases`, `GET /readings*` |
| 부적 | `/talismans*`, download URL, claim |
| 공유 | `POST /readings/{id}/shares`, `POST /talismans/{id}/shares`, `GET /shares/{id}` |
| 공개 선물 | `POST /gifts/{token}/recipient`, `GET /gifts/{token}/result`, 선물 부적 download URL의 결과 부분 |

### 교차 endpoint의 최종 책임

| 흐름 | controller owner | 호출 계약 |
|---|---|---|
| 본인 운세 구매 | BE-B | BE-B가 `WalletPurchasePort`로 BE-A 원장 차감·보상 호출 |
| 결과 후 부적 구매 | BE-B | BE-A `WalletPurchasePort` 차감 후 BE-B가 부적 생성 |
| 공개 선물 조회 | BE-A | BE-B가 제공물 summary assembler를 제공 |
| 수신자 정보 제출 | BE-A | token·잠금·gift 상태는 A, 입력 검증·결과 생성은 `GiftReadingPort`의 B |
| 선물 결과 조회 | BE-A | gift 접근권한 확인 후 B의 reading/talisman projection 반환 |
| 선물 부적 claim | BE-B | A의 `GiftAccessPort`로 유효 token·대상 확인 |

외부 HTTP endpoint에 대한 장애·보안 최종 책임은 controller owner에게 있다. 내부 계산·원장 결과의 정확성은 해당 port 제공자가 책임진다.

## 7. 테이블·Migration 소유권

| BE-A | BE-B |
|---|---|
| `products`, `purchase_quotes` | `users`, `user_sessions`, `user_roles` |
| `wallets`, `wallet_lots` | `people` |
| `wallet_transactions`, `wallet_transaction_lines` | `reading_results`, `generation_attempts`, `daily_fortune_results` |
| `payment_orders`, `payment_attempts`, `refunds` | `reading_purchases`, `readings` |
| `topup_grants` | `talisman_purchases`, `talismans`, `talisman_ownerships` |
| `gifts`, `gift_deliveries`, `outbox_events` | `gift_recipient_profiles`의 입력 schema·crypto converter |
| `idempotency_records`, `audit_logs` | `share_resources`, `content_versions` |

- `gift_recipient_profiles`의 migration 파일과 상태 변경은 BE-A가 소유하고, 암호화 converter·입력 검증은 BE-B가 제공한다.
- Flyway 파일명은 시간 기반 버전을 사용한다. 예: `V202610021430__a_create_wallet.sql`, `V202610021435__b_create_people.sql`.
- 이미 공유 브랜치에 올라간 migration은 수정하지 않고 후속 migration으로 고친다.
- FK가 상대 영역 테이블을 참조하면 상대 담당자의 리뷰를 반드시 받는다.

## 8. 공통 인터페이스 계약

### BE-A가 제공

```java
public interface WalletPurchasePort {
    DebitResult debit(UserId userId, QuoteId quoteId, IdempotencyKey key);
    RefundResult compensate(PurchaseReference reference, ReasonCode reason);
}

public interface GiftAccessPort {
    GiftAccess verify(String rawToken);
    GiftAccess verifyTalismanAccess(String rawToken, UUID talismanId);
}
```

### BE-B가 제공

```java
public interface ReadingGenerationPort {
    ReadingResult generate(ReadingGenerationCommand command);
}

public interface GiftReadingPort {
    GiftFulfillment fulfill(GiftId giftId, GiftRecipientInput input);
}

public interface GiftContentProjectionPort {
    GiftContentSummary summarize(GiftId giftId);
}
```

DTO에는 JPA entity를 넣지 않는다. 공개 ID, enum, 금액 value object, 필요한 snapshot만 전달한다.

## 9. 개발 일정

| 기간 | BE-A | BE-B | 통합 완료 조건 |
|---|---|---|---|
| 10/2~10/4 | 프로젝트·DB·공통 응답·멱등성 골격 | 인증·session·person schema·암호화 골격 | staging health, 로그인, 공통 오류 |
| 10/5~10/10 | 상품·quote·지갑·충전·토스 test 결제 | 만세력 입력/계산 spike·기준 케이스·Liner contract | 충전 후 잔액, 계산 JSON과 생성 mock |
| 10/11~10/17 | 원장 환급·관리자 조회·결제 대사 | 수능운 구매·결과·부적·R2 | 본인 수능 `사주/사주+부적` E2E |
| 10/18~10/23 | 선물 결제·token·알림톡·재발급 | 선물 수신 입력·결과·claim | 결제→알림톡→비로그인 결과 E2E |
| 10/24~10/27 | 환불·대사·장애 알림·운영 도구 | 일반 5종·공유·창고·회귀 테스트 | 6종·공유·운영 시나리오 통과 |
| 10/28 | 코드 프리즈·미해결 P0 정리 | 코드 프리즈·성능/보안 정리 | release candidate tag |
| 10/29~10/30 | production 실결제·환불·알림톡 점검 | production 로그인·결과·R2 점검 | launch checklist 승인 |
| 10/31 | 결제·원장·선물 모니터링 | 인증·결과·미디어 모니터링 | 출시 |

외부 심사 지연 시 기능 우선순위는 `본인 수능운 → 충전/결제 → 단건 선물 → 부적 → 일반 5종` 순서로 회의에서 재조정한다. 제품 범위를 임의로 삭제하지는 않는다.

## 10. 테스트 책임

### BE-A

- 원장 동시 차감, 멱등 재요청, 보상 거래, lot 만료
- 토스 금액 변조, 승인 timeout 후 대사, 중복 webhook, 환불 실패
- 선물 token hash, 만료·폐기, 재발급 경쟁, 알림톡 중복 발송
- 관리자 권한·감사 사유·민감값 마스킹

### BE-B

- OAuth state/returnTo, session fixation, CSRF/Origin, 소유권 404
- 양력/음력/윤달·절기·시간 미상·궁합 입력
- 50개 이상 만세력 기준 케이스와 버전 회귀
- Liner JSON schema·근거 key·금지 표현·추론 차단
- generationKey 경쟁 요청과 최초 성공 snapshot 재사용
- R2 private object, signed URL 만료, 공유 이미지 개인정보 부재

### 공동 E2E

1. 로그인 → 등껍질 충전 → 유료/보너스 lot 확인
2. 수능 `READING_ONLY` 구매 → 결과 → 15 등껍질 부적 추가 구매
3. 수능 `READING_WITH_TALISMAN` 구매 → 결과·부적·카카오 공유 리소스
4. 선물 `TALISMAN_ONLY` 원화 결제 → 알림톡 → 비로그인 정보 입력 → 편지·부적·수능 체크리스트
5. 선물 `READING_WITH_TALISMAN` → 전체 결과·부적 → 로그인 후 claim
6. 결과 생성 최종 실패 → 등껍질 역분개와 동일 키 재요청
7. 알림톡 실패 → 결제·선물 활성 유지 → 재발송
8. 탈퇴 → 접근 차단 → 개인정보 삭제 대상과 법정 거래 기록 분리

## 11. PR·리뷰 규칙

- 한 PR은 한 도메인 또는 하나의 통합 계약을 원칙으로 한다.
- PR 본문에 변경 endpoint, migration, 상태 전이, 신규 error code, 개인정보 처리, rollback 방법을 적는다.
- OpenAPI가 바뀌면 생성된 FE type diff 또는 호환성 확인 결과를 첨부한다.
- migration이 있으면 빈 DB 적용과 기존 staging DB upgrade를 모두 검증한다.
- 다음 PR은 상대 담당자 승인 필수다.
  - 지갑·결제·환불·멱등성
  - 세션·CSRF·OAuth
  - 선물 token·전화번호·알림톡
  - 만세력 계산·Liner 입력/출력 검증
  - R2 권한·공유 공개 범위
- 장애 수정 PR에는 재현 테스트를 반드시 추가한다.

## 12. 완료 기준

기능은 다음을 모두 만족해야 완료다.

- 정상·검증 실패·권한 없음·중복 요청·외부 장애 테스트가 있다.
- [`COMMON_RESPONSE_AND_ERROR_CODES.md`](../../docs/COMMON_RESPONSE_AND_ERROR_CODES.md)의 포맷과 code를 사용한다.
- OpenAPI 예시와 실제 응답이 일치한다.
- 개인정보·token·provider key가 로그와 Sentry에 남지 않는다.
- 상태 전이와 멱등성 제약이 DB/서비스 양쪽에 있다.
- 운영자가 실패 상태를 조회하고 안전하게 재처리할 수 있다.
- Sentry/PostHog 이벤트에 금지 데이터가 없는지 확인했다.
- 상대 담당자의 리뷰와 staging 통합 테스트를 통과했다.

## 13. 아직 담당자가 결정하지 말아야 하는 항목

다음은 구현 편의를 이유로 BE-A/B가 독단 확정하지 않는다.

- 유료/보너스 등껍질 차감 우선순위
- 선물 가격·링크 만료·제공 개시·환불 기준
- 알림톡 대행사·템플릿·재시도·SMS fallback·전화번호 보관기간
- 신살 결과 구성과 부적 종류
- 자시·역법·띠 기준
- 부적 동기/비동기 처리
- 공유 결과 범위·만료·철회
- 동시 로그인 5개 초과 처리 방식

막히는 경우 인터페이스·상태·테스트 double까지만 구현하고 [`PENDING_DECISIONS.md`](../../docs/PENDING_DECISIONS.md)의 해당 ID를 PR에 표시한다.
