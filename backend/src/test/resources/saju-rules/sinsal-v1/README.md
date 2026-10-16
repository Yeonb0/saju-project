# 신살 규칙 fixture v1

이 fixture는 `sinsal-v1`의 구현 회귀를 고정하는 합성 사례다. `natalTypeCount`는 모든 label이 있는지가 아니라 13종 판정 결과의 구조가 유지되는지를, `occurrenceCounts`는 특정 판정의 count가 조용히 바뀌지 않는지를 확인한다.

명리 검수 근거와 추가 사례는 아직 필요하다. 새 신살 기준을 채택할 때는 기존 fixture를 조용히 고치지 말고 `ruleVersion`·검수 근거·변경 영향과 함께 새 fixture version을 추가한다.
