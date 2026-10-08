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
- 3회 모두 실패하면 외부 호출 없이 `CalculationFacts`만 읽는 결정론적 fallback을 1회 실행한다. fallback도 동일한 JSON Schema·section·근거 key·금지 표현 검증을 통과해야 저장된다.
- API와 DB의 `generationMode`는 정상 생성 `LINER`, 안전 응답 `FALLBACK`을 구분한다. fallback 성공은 정상 결과로 보존·재사용하며 이 경우 지갑을 환급하지 않는다.
- fallback까지 실패한 경우에만 생성을 최종 실패로 기록하고 유료 구매 보상 흐름으로 내려간다.
- 같은 `generationKey`의 동시 호출은 JVM lock과 DB claim/lease로 중복 생성을 막는다.

### 수능운 fallback 출력 예시

예를 들어 계산 결과가 `갑 일간`, 오행이 `목 3·화 2·토 1·금 1·수 1`이라면 응답은 아래 원칙으로 만들어진다. 동률은 목→화→토→금→수의 고정 순서로 선택하므로 같은 계산·버전에서는 항상 같은 문장이 나온다.

```json
{
  "generationMode": "FALLBACK",
  "sections": [
    {
      "key": "SUMMARY",
      "content": "갑 일간은 목의 성향을 기준점으로 봅니다. 오행 분포는 목 3·화 2·토 1·금 1·수 1이며, 가장 많이 드러난 목의 장점은 살리고 상대적으로 적은 토의 역할은 생활 루틴으로 보완하는 해석입니다. 이 결과는 합격 여부를 단정하는 예측이 아니라 시험 준비 리듬을 점검하기 위한 참고 정보예요.",
      "sourceFactKeys": ["dayMaster.hangul", "dayMaster.element", "fiveElements.counts.WOOD", "fiveElements.counts.FIRE", "fiveElements.counts.EARTH", "fiveElements.counts.METAL", "fiveElements.counts.WATER"]
    },
    {
      "key": "EXAM_DAY",
      "content": "시험 당일에는 목의 추진력이 한 방향으로 몰리지 않도록 문제를 읽는 순서를 고정해 보세요. ① 수험번호와 선택과목 확인, ② 쉬운 문항 우선 표시, ③ 종료 10분 전 답안지 재확인의 세 단계가 좋습니다. 막히는 문제는 표시 후 넘기고, 한 교시의 체감을 다음 교시 판단으로 이어 가지 않는 편이 안전합니다."
    },
    {
      "key": "EXAM_PERIODS",
      "content": "초반에는 호흡과 시험지 전체 구성을 확인하고, 중반에는 갑 일간의 집중력을 한 문제씩 사용하는 흐름이 어울립니다. 후반에는 새 풀이를 벌이기보다 표시한 문항과 답안 밀림을 확인하세요. 목 기운이 강한 분포일수록 속도를 내기 쉬우므로 교시마다 ‘읽기-풀이-검산’ 시간을 미리 나누는 방식이 도움이 됩니다."
    },
    {
      "key": "FOCUS",
      "content": "목이 강점으로 나타난 만큼 집중이 붙으면 오래 밀고 갈 수 있지만, 한 문제에 과하게 머무는 패턴은 경계해 주세요. 25~40분 단위 집중, 1~3분 자세·호흡 점검, 오답 이유 한 줄 기록처럼 짧고 반복 가능한 루틴을 권합니다."
    },
    {
      "key": "MEAL",
      "content": "오행에서 상대적으로 적은 토는 해석상의 균형 포인트일 뿐, 특정 음식의 효능을 뜻하지 않습니다. 시험 전날과 당일에는 새 보양식보다 평소 잘 맞았던 식사, 과하지 않은 양, 충분한 수분을 우선하세요."
    },
    {
      "key": "PREPARATION",
      "content": "준비물은 수험표·신분증·허용된 시계·필기구·물·체온 조절용 겉옷 순서로 전날 한 번, 출발 직전 한 번 확인하세요. 이동 경로와 입실 시각은 대체 경로까지 적어 두고, 전자기기 반입 규정은 공식 수험 안내를 기준으로 확인해야 합니다."
    },
    {
      "key": "ANXIETY_MANAGEMENT",
      "content": "불안이 올라오면 결과를 예측하려 하기보다 지금 확인 가능한 행동으로 돌아오세요. 4초 들이마시고 6초 내쉬는 호흡을 5회 반복한 뒤, 발바닥 감각·어깨 힘·현재 문항 번호를 차례로 확인합니다."
    },
    {
      "key": "MISSING_ELEMENT",
      "content": "현재 분포에서는 목이 가장 많고 토가 상대적으로 적습니다. ‘부족’은 나쁘다는 뜻이 아니라 의식적으로 챙길 생활 항목을 정하는 표지로 봅니다. 강한 쪽은 실행력으로 쓰되, 약한 쪽은 수면·휴식·검산·대화 같은 현실적인 습관 하나를 정해 보완하세요."
    }
  ]
}
```

실제 응답의 모든 section에는 예시의 `SUMMARY`처럼 검증된 `sourceFactKeys`가 포함된다. 음식은 효능을 주장하지 않고, 불안 안내는 전문가 도움을 우선하며, 행운 색·소품은 결과를 보장하지 않는 재미 요소로만 표현한다.

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

견적 context는 인물·상품·시험일을 묶어 다른 인물에 재사용할 수 없다. 같은 견적의 재요청은 최초 구매를 반환하고 지갑과 Liner를 다시 호출하지 않는다. 번들 부적은 `SuneungTalismanPort`가 `PENDING` 또는 `READY` fulfillment를 반환해야 구매가 완료된다. Liner 실패는 검증된 fallback으로 정상 완료하고, fallback을 포함한 결과 생성·부적 fulfillment 최종 실패와 차감 금액 불일치는 원 거래를 보상하고 구매를 `REFUNDED`로 기록한다.

현재 실제 구매 실행에는 BE-A의 `WalletPurchasePort` 구현과 수능 상품 seed, BE-B 인물 모듈의 `ReadingSubjectPort`, 부적 모듈의 `SuneungTalismanPort` 구현이 필요하다. 의존성이 준비되지 않은 운영 요청은 유료 콘텐츠를 우회 제공하지 않고 `READING_FULFILLMENT_UNAVAILABLE`로 실패한다.
