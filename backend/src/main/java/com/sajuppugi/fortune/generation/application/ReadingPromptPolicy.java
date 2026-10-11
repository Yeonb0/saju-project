package com.sajuppugi.fortune.generation.application;

import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import java.util.Map;

public final class ReadingPromptPolicy {
    private ReadingPromptPolicy() {}

    private static final Map<String, String> QUESTION_RULES = Map.ofEntries(
            Map.entry("OVERALL_FLOW", "현재 흐름의 강점과 주의점을 구분하고, 오늘 조절할 행동 하나를 제안합니다."),
            Map.entry("RELATIONSHIPS", "상대의 마음을 추측하지 말고 관찰 가능한 관계 행동, 대화 방법, 경계 존중을 다룹니다."),
            Map.entry("STUDY_AND_WORK", "집중 방식, 과제 우선순위, 검토 루틴을 구체적인 실행 단위로 제안합니다."),
            Map.entry("WEALTH", "실제 예산과 고정비 확인을 우선하고 수익·투자 결과를 예측하지 않습니다."),
            Map.entry("CONDITION", "수면·식사·움직임 같은 생활 습관만 다루고 의학적 판단을 하지 않습니다."),
            Map.entry("CURRENT_RELATIONSHIP", "현재 관계에서 본인의 경향, 확인할 점, 피할 대화 방식, 작은 행동 하나를 제시합니다."),
            Map.entry("NEW_RELATIONSHIP", "새로운 만남을 단정하지 말고 관계를 시작할 때 확인할 기준과 경계를 제시합니다."),
            Map.entry("RECONCILIATION", "재회를 예언하지 말고 재접촉 전 확인할 변화, 안전한 대화, 중단 기준을 제시합니다."),
            Map.entry("MARRIAGE", "결혼 성사 여부를 단정하지 말고 가치관·생활·재정·갈등 조율 질문을 제시합니다."),
            Map.entry("OVERALL_WEALTH", "수입·지출·저축의 균형을 점검하고 실행 가능한 재정 습관을 하나 제시합니다."),
            Map.entry("INCOME", "소득 증가를 보장하지 말고 역량·협상·기록처럼 통제 가능한 행동을 다룹니다."),
            Map.entry("SPENDING", "충동 지출 가능성을 낙인찍지 말고 보류 규칙·예산·기록 방법을 제시합니다."),
            Map.entry("INVESTMENT", "종목·수익률·매수 시점을 제안하지 말고 손실 감내 범위와 계약 확인을 강조합니다."),
            Map.entry("CAREER_FINANCE", "직업 선택을 단정하지 말고 보상 조건·성장 가능성·생활비를 함께 확인하게 합니다."),
            Map.entry("OVERALL_MATCH", "두 사람의 공통점과 차이를 균형 있게 설명하고 관계 결론을 단정하지 않습니다."),
            Map.entry("COMMUNICATION", "관찰-느낌-요청 순서의 구체적인 대화 방법과 확인 질문을 제시합니다."),
            Map.entry("CONFLICT", "갈등을 예언하지 말고 촉발 조건, 멈춤 신호, 회복 대화를 제시합니다."),
            Map.entry("LONG_TERM_POTENTIAL", "장기 관계를 단정하지 말고 약속 이행·경계·생활 목표처럼 관찰 가능한 기준을 제시합니다."),
            Map.entry("OVERALL_SINSAL", "신살을 낙인이나 사건 예언으로 쓰지 않고 장점과 생활 습관 관점으로 설명합니다."),
            Map.entry("WORK_AND_STUDY", "신살 명칭보다 실제 집중·협업·검토 습관을 중심으로 제안합니다."),
            Map.entry("EXAM_FOCUS", "합격을 예측하지 않고 시험 전 준비, 교시별 집중, 식사, 준비물, 긴장 관리 루틴을 구체화합니다.")
    );

    public static String instructions(LinerRequest request) {
        return """
                운세 유형 목표: %s
                사용자가 선택한 질문 목표: %s
                작성 품질 규칙:
                - 첫 문장에서 입력 facts의 핵심 차이를 분명히 설명합니다.
                - 섹션마다 서로 다른 역할을 수행하며 같은 조언을 바꿔 쓰지 않습니다.
                - 행동 제안은 오늘 사용자가 실행할 수 있는 동사와 확인 기준을 포함합니다.
                - 상대방의 속마음, 미래 사건, 합격, 수익, 질병을 추측하거나 확정하지 않습니다.
                - 명리 용어를 나열하지 말고 해당 facts가 생활에서 어떤 점검 질문으로 이어지는지 설명합니다.
                - 누락된 시간 기반 facts는 추측하지 않고 시간 미상 한계를 자연스럽게 밝힙니다.
                """.formatted(typeRule(request.fortuneType()), questionRule(request));
    }

    private static String questionRule(LinerRequest request) {
        if (request.fortuneType() == FortuneType.SUNEUNG) return QUESTION_RULES.get("EXAM_FOCUS");
        return QUESTION_RULES.getOrDefault(request.interestKey(),
                "선택한 질문에 직접 답하고, 근거·주의점·실행 행동을 각각 구분합니다.");
    }

    private static String typeRule(FortuneType type) {
        return switch (type) {
            case OVERALL -> "관계·학업/일·재물·컨디션을 균형 있게 연결합니다.";
            case LOVE -> "관계 상태를 단정하지 않고 감정 확인, 대화, 경계 존중을 중심으로 씁니다.";
            case WEALTH -> "현실의 수입·고정비·계약 조건을 우선하며 절제된 재정 습관을 제안합니다.";
            case COMPATIBILITY -> "두 사람을 우열로 비교하지 않고 공통점·차이·대화 방법을 양쪽 facts에 근거해 씁니다.";
            case SINSAL -> "신살을 사건 예언이나 낙인으로 쓰지 않고 장점과 생활 속 다스림의 관점으로 설명합니다.";
            case SUNEUNG -> "시험 당일 준비·집중·긴장 관리에 도움이 되는 현실적 루틴을 제안합니다.";
        };
    }
}
