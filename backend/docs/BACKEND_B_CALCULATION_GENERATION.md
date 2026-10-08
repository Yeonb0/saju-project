# 백엔드 B 계산·생성 구현

## 범위

- `fortune/calculation`: 결정론적 만세력 계산과 버전이 고정된 `CalculationFacts`
- `fortune/generation`: 개인정보가 제거된 facts projection, 생성 키, 응답 검증, 재시도, snapshot 재사용
- 외부 Liner 공식 endpoint·인증·모델 계약이 제공되기 전까지 `FakeLinerProvider`를 기본 adapter로 사용한다. 임의의 외부 URL을 호출하지 않는다.

## 계산 정책

- 정책 버전: `manse-2026.10-v1`
- 시간대: `Asia/Seoul`
- 일주 경계: 자정(`MIDNIGHT`)
- 진태양시·해외 출생지 보정: 미적용
- 한국 음력·윤달 변환: `KoreanLunarCalendar 0.4.0`
- 절기 기준 사주·대운: `lunar-java 1.7.7`, KST와 라이브러리 기준 시간대 차이는 동일 순간으로 정규화
- 절기 전환일에 출생 시간이 미상이면 연·월주를 추측하지 않고 지원하지 않는 입력으로 처리한다.

`CalculationFacts`는 불변 record이며 원국, 오행, 십성, 12운성, 합·충·형·파·해, 대운·세운을 포함한다. 원본 이름과 사용자 ID는 포함하지 않는다. 계산 규칙이 바뀌면 기존 값을 덮어쓰지 않고 `calculationVersion`을 올린다.

## 생성·개인정보 경계

- 생성 명령에는 `requesterUserId`가 필수지만 `generationKey`와 Liner payload에는 넣지 않는다.
- 원본 생년월일시와 이름은 Liner payload 및 `reading_results.facts_json`에 넣지 않는다.
- 동일 facts·상품 맥락·기준일·계산/생성 버전은 계정과 무관하게 최초 성공 snapshot을 재사용한다.
- 사용자에게 결과를 노출하는 조회는 후속 `readings.owner_user_id` 또는 선물 접근권한을 반드시 통과해야 한다. `reading_results` 직접 조회 endpoint는 만들지 않는다.
- 스키마 계약은 `src/main/resources/liner/liner-response.schema.json`에서 버전 관리한다.

## 검증과 실패 처리

- section은 요청의 `allowedSections`에 포함되어야 한다.
- `sourceFactKeys`는 실제 전달된 scalar fact 경로만 참조할 수 있다.
- 누락 section은 `omittedSections`에 명시해야 한다.
- 확정적 합격 예언, 질병 진단, 죽음 예고, prompt 노출 표현은 거절한다.
- provider/검증 실패는 최대 3회 시도하고 모든 attempt의 입력 hash·지연·오류 코드를 저장한다.
- 같은 `generationKey`의 동시 호출은 JVM lock과 DB claim/lease로 중복 생성을 막는다.

## 검증 명령

```sh
cd backend
./gradlew test
```

회귀 테스트는 참고 구현에서 두 종류 이상의 역법 구현으로 교차검증한 1901~2048년 50개 기준 케이스, 입춘 분 경계, 한국 윤달, 시간 미상, 생성 동시성·재시도·근거 검증을 포함한다.

## 수능운 구매 API

- `POST /api/v1/quotes/fortune`: 소유 인물 확인 후 수능운 견적 발급
- `POST /api/v1/reading-purchases`: 견적 검증, 지갑 차감, 계산·생성, 결과 소유권 부여
- `GET /api/v1/readings/{readingId}`: `owner_user_id`가 일치하는 결과만 반환
- 시험일: 2026-11-19, 판매 종료: 2026-11-18 23:59:59 KST
- 상품 코드: `SUNEUNG_READING_WITH_TALISMAN`

견적 context는 인물·상품·시험일을 묶어 다른 인물에 재사용할 수 없다. 같은 견적의 재요청은 최초 구매를 반환하고 지갑과 Liner를 다시 호출하지 않는다. 생성 최종 실패와 차감 금액 불일치는 원 거래를 보상하고 구매를 `REFUNDED`로 기록한다.

현재 실제 구매 실행에는 BE-A의 `WalletPurchasePort` 구현과 수능 상품 seed, BE-B 인물 모듈의 `ReadingSubjectPort` 구현이 필요하다. 의존성이 준비되지 않은 운영 요청은 유료 콘텐츠를 우회 제공하지 않고 `READING_FULFILLMENT_UNAVAILABLE`로 실패한다.
