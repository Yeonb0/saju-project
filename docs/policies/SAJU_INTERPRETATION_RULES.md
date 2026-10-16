# 사주 해석 규칙 — `sinsal-v1`

상태: 구현된 신살 계산의 추적 문서. 이 문서는 명리 검수 승인 전의 출시 해석 권위를 주장하지 않는다.

## 경계

계산 facts와 해석 문장을 분리한다. 생성 모델은 `CalculationFacts.specialStars`에 이미 들어 있는 결과를 설명할 수만 있으며, 신살을 새로 계산하거나 facts에 없는 신살을 단정해서는 안 된다.

`sinsal-v1`은 아래 13종의 원국 판정과 대운·세운 활성 결과를 제공한다.

| 내부 type | 표시명 |
| --- | --- |
| `HEAVENLY_NOBLE` | 천을귀인 |
| `LITERARY_NOBLE` | 문창귀인 |
| `YANG_BLADE` | 양인살 |
| `PEACH_BLOSSOM` | 도화살 |
| `TRAVEL_HORSE` | 역마살 |
| `CANOPY` | 화개살 |
| `VOID` | 공망살 |
| `YUANJIN` | 원진살 |
| `SPIRIT_GATE` | 귀문관살 |
| `WHITE_TIGER` | 백호살 |
| `GANG_STRENGTH` | 괴강살 |
| `DISGRACE` | 망신살 |
| `RED_CHARM` | 홍염살 |

## 출력 규칙

- 원국에 count가 0인 신살은 사용자 결과에서 "있다"고 표현하지 않는다.
- 활성 결과는 대운(나이 구간)과 세운(연도)을 구분해 설명한다.
- 건강·사고·합격·관계 결과를 확정하거나 공포를 조장하지 않는다. 부정적 label은 생활 리듬과 선택의 조언으로 제한한다.
- 용신·희신·기신·격국·대운의 길흉 결론은 별도 검수된 rule version이 생기기 전까지 결과로 단정하지 않는다.

## 변경 관리

신살 표, 판정 기준, 출력 허용 범주가 바뀌면 `ruleVersion`을 올리고 fixture와 검수 근거를 함께 갱신한다. `calculationVersion`과 `ruleVersion`은 독립적으로 관리한다.

