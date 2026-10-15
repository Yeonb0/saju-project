package com.sajuppugi.fortune.generation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Last-resort prose generated only from already calculated, non-personal facts.
 * It intentionally makes no prediction of success and remains subject to the same validator as Liner output.
 */
@Component
public class DeterministicReadingFallback {
    private static final List<String> CORE_FACTS = List.of(
            "dayMaster.hangul", "dayMaster.element", "fiveElements.counts.WOOD",
            "fiveElements.counts.FIRE", "fiveElements.counts.EARTH",
            "fiveElements.counts.METAL", "fiveElements.counts.WATER");

    public String name() {
        return "deterministic-fallback";
    }

    public LinerResponse generate(LinerRequest request) {
        Profile profile = profile(request.facts(), "");
        Profile counterpart = request.facts().has("counterpart")
                ? profile(request.facts(), "/counterpart") : null;
        List<GeneratedSection> sections = request.allowedSections().stream()
                .map(key -> new GeneratedSection(key, content(key, request.fortuneType(), profile, counterpart),
                        evidence(key, counterpart != null)))
                .toList();
        return new LinerResponse(sections, List.of(), request.fortuneType()
                == com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType.SUNEUNG
                ? SuneungGuideFactory.fromSections(sections) : null);
    }

    private List<String> evidence(SectionKey key, boolean hasCounterpart) {
        if (key == SectionKey.GOOD_PERIOD) {
            return java.util.stream.Stream.concat(CORE_FACTS.stream(), java.util.stream.Stream.of("luck.annual[0].year"))
                    .toList();
        }
        if (key == SectionKey.SPECIAL_STARS || key == SectionKey.BALANCING_SPECIAL_STARS
                || key == SectionKey.SINSAL_POPULAR || key == SectionKey.SINSAL_MOVEMENT
                || key == SectionKey.SINSAL_INTUITION || key == SectionKey.SINSAL_INTENSITY
                || key == SectionKey.SINSAL_TIMING) {
            return java.util.stream.Stream.concat(CORE_FACTS.stream(),
                    java.util.stream.Stream.of("specialStars.ruleVersion")).toList();
        }
        if (hasCounterpart) {
            return java.util.stream.Stream.concat(CORE_FACTS.stream(),
                    CORE_FACTS.stream().map(path -> "counterpart." + path)).toList();
        }
        return CORE_FACTS;
    }

    private Profile profile(JsonNode facts, String prefix) {
        String dayMaster = requiredText(facts, prefix + "/dayMaster/hangul");
        String element = requiredText(facts, prefix + "/dayMaster/element");
        int wood = requiredInt(facts, prefix + "/fiveElements/counts/WOOD");
        int fire = requiredInt(facts, prefix + "/fiveElements/counts/FIRE");
        int earth = requiredInt(facts, prefix + "/fiveElements/counts/EARTH");
        int metal = requiredInt(facts, prefix + "/fiveElements/counts/METAL");
        int water = requiredInt(facts, prefix + "/fiveElements/counts/WATER");
        int[] values = {wood, fire, earth, metal, water};
        String[] keys = {"WOOD", "FIRE", "EARTH", "METAL", "WATER"};
        int max = 0;
        int min = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[max]) max = index;
            if (values[index] < values[min]) min = index;
        }
        return new Profile(dayMaster, korean(element), korean(keys[max]), korean(keys[min]),
                wood, fire, earth, metal, water);
    }

    private String content(SectionKey key, com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType type,
                           Profile p, Profile counterpart) {
        String distribution = "목 %d·화 %d·토 %d·금 %d·수 %d".formatted(
                p.wood, p.fire, p.earth, p.metal, p.water);
        return switch (key) {
            case SUMMARY -> summary(type, p, counterpart, distribution);
            case EXAM_DAY -> "시험 당일에는 %s의 추진력이 한 방향으로 몰리지 않도록 문제를 읽는 순서를 고정해 보세요. ① 수험번호와 선택과목 확인, ② 쉬운 문항 우선 표시, ③ 종료 10분 전 답안지 재확인의 세 단계가 좋습니다. 막히는 문제는 표시 후 넘기고, 한 교시의 체감을 다음 교시 판단으로 이어 가지 않는 편이 안전합니다."
                    .formatted(p.strong);
            case EXAM_PERIODS -> "초반에는 호흡과 시험지 전체 구성을 확인하고, 중반에는 %s 일간의 집중력을 한 문제씩 사용하는 흐름이 어울립니다. 후반에는 새 풀이를 벌이기보다 표시한 문항과 답안 밀림을 확인하세요. %s 기운이 강한 분포일수록 속도를 내기 쉬우므로 교시마다 ‘읽기-풀이-검산’ 시간을 미리 나누는 방식이 도움이 됩니다."
                    .formatted(p.dayMaster, p.strong);
            case FOCUS -> "%s이 강점으로 나타난 만큼 집중이 붙으면 오래 밀고 갈 수 있지만, 한 문제에 과하게 머무는 패턴은 경계해 주세요. 25~40분 단위 집중, 1~3분 자세·호흡 점검, 오답 이유 한 줄 기록처럼 짧고 반복 가능한 루틴을 권합니다. 공부량보다 시작 시각과 종료 시각을 일정하게 유지하는 것이 핵심입니다."
                    .formatted(p.strong);
            case MEAL -> "오행에서 상대적으로 적은 %s은 해석상의 균형 포인트일 뿐, 특정 음식의 효능을 뜻하지 않습니다. 시험 전날과 당일에는 새 보양식보다 평소 잘 맞았던 식사, 과하지 않은 양, 충분한 수분을 우선하세요. 간식도 처음 먹는 제품은 피하고 실제 모의고사 날에 미리 시험해 본 구성을 유지하는 편이 좋습니다."
                    .formatted(p.weak);
            case PREPARATION -> "준비물은 수험표·신분증·허용된 시계·필기구·물·체온 조절용 겉옷 순서로 전날 한 번, 출발 직전 한 번 확인하세요. %s의 균형을 떠올리는 작은 색상 소품은 재미 요소로만 활용할 수 있습니다. 이동 경로와 입실 시각은 대체 경로까지 적어 두고, 전자기기 반입 규정은 공식 수험 안내를 기준으로 확인해야 합니다."
                    .formatted(p.weak);
            case ANXIETY_MANAGEMENT -> "불안이 올라오면 결과를 예측하려 하기보다 지금 확인 가능한 행동으로 돌아오세요. 4초 들이마시고 6초 내쉬는 호흡을 5회 반복한 뒤, 발바닥 감각·어깨 힘·현재 문항 번호를 차례로 확인합니다. 불안이 일상 기능을 방해할 정도로 이어진다면 운세 해석보다 보호자·교사·전문가의 도움을 우선하세요."
                    .formatted();
            case MISSING_ELEMENT -> "현재 분포(%s)에서는 %s이 가장 많고 %s이 상대적으로 적습니다. ‘부족’은 나쁘다는 뜻이 아니라 의식적으로 챙길 생활 항목을 정하는 표지로 봅니다. 강한 쪽은 실행력으로 쓰되, 약한 쪽은 수면·휴식·검산·대화 같은 현실적인 습관 하나를 정해 보완하세요."
                    .formatted(distribution, p.strong, p.weak);
            case CURRENT_FLOW -> "%s 일간과 %s 중심의 분포는 익숙한 방식을 밀고 가려는 경향으로 읽을 수 있습니다. 큰 결론보다 현재 일정과 자원을 먼저 확인하고, 상대적으로 적은 %s의 역할을 작은 점검 습관으로 더해 보세요."
                    .formatted(p.dayMaster, p.strong, p.weak);
            case GOOD_PERIOD -> "좋은 시기를 날짜 하나로 단정하기보다 집중이 잘된 조건을 기록해 반복하는 방식이 적합합니다. %s의 장점을 사용할 수 있도록 중요한 일은 에너지가 안정적인 시간대에 배치하고, 전후에 검토 시간을 남겨 두세요."
                    .formatted(p.strong);
            case CAUTION -> "%s이 두드러질 때는 장점이 과속이나 고집으로 나타나지 않는지 살펴보세요. 결정 전 사실 확인, 다른 사람의 관점 한 번 듣기, 하루 뒤 재검토 중 하나를 안전장치로 두는 것이 좋습니다."
                    .formatted(p.strong);
            case ACTION_TIP -> "오늘 실행할 항목을 하나만 정하고 시작·종료 조건을 숫자로 적어 보세요. %s의 강점은 실행에 쓰고, %s의 보완점은 체크리스트와 휴식 시간으로 관리하면 해석을 현실적인 행동으로 연결할 수 있습니다."
                    .formatted(p.strong, p.weak);
            case RELATIONSHIPS -> "%s 일간은 관계에서도 자신의 기준을 분명히 세우는 쪽으로 해석할 수 있습니다. 상대의 의도를 추측하기보다 원하는 것과 어려운 것을 짧은 문장으로 확인해 보세요."
                    .formatted(p.dayMaster);
            case STUDY_AND_WORK -> "%s이 강한 분포는 한 번 잡은 과제를 밀고 가는 데 활용할 수 있습니다. 다만 완료 기준과 검토 시점을 먼저 정해 몰입이 재작업으로 이어지지 않게 관리하세요."
                    .formatted(p.strong);
            case WEALTH_FLOW -> "재정 흐름은 운세보다 실제 수입·고정비·상환 일정으로 먼저 확인해야 합니다. %s의 추진력은 예산 목표를 실행하는 데 쓰고, 월초에 가용 금액과 필수 지출을 나눠 기록하세요."
                    .formatted(p.strong);
            case INCOME -> "수입을 늘리는 선택에는 기대 금액뿐 아니라 필요한 시간과 비용도 함께 적어 보세요. %s의 강점을 기존 기술을 완성하거나 협상 자료를 준비하는 데 쓰고, 수익 보장은 경계해야 합니다."
                    .formatted(p.strong);
            case SPENDING_CAUTION -> "지출 전에는 필요·욕구·보류의 세 칸으로 나누고 큰 결제는 24시간 뒤 다시 확인하세요. 상대적으로 적은 %s의 균형은 월 한도와 계약 조건을 체크리스트로 점검하는 습관에서 찾을 수 있습니다."
                    .formatted(p.weak);
            case CONDITION -> "현재 해석은 의료 판단이 아닙니다. %s의 활동성을 살리되 수면, 식사, 움직임의 시간을 일정하게 기록해 컨디션 변화를 현실 자료로 확인하세요. 불편이 지속되면 의료 전문가의 안내를 우선해야 합니다."
                    .formatted(p.strong);
            case LUCKY_POINT -> "행운 요소는 결과를 보장하는 물건이 아니라 루틴을 떠올리는 표식으로만 활용하세요. 상대적으로 적은 %s을 연상시키는 색이나 메모를 ‘한 번 더 확인하기’ 신호로 정할 수 있습니다."
                    .formatted(p.weak);
            case MATCH_STRENGTH -> "두 사람의 중심인 %s 기운과 %s 기운은 서로의 성향을 확정하는 표지가 아니라 강점을 나눠 볼 출발점입니다. 실제로 잘 맞았던 상황을 돌아보고 역할과 기대 수준을 말로 합의해 보세요."
                    .formatted(p.dayElement, counterpartElement(counterpart));
            case MATCH_CONFLICT -> "갈등 가능성은 정해진 사건이 아닙니다. 한쪽의 %s과 다른 쪽의 %s이 강하게 표현될 때 속도 차이가 생길 수 있으니, 결론 전에 상대의 말을 한 문장으로 요약해 확인해 보세요."
                    .formatted(p.strong, counterpartStrong(counterpart));
            case COMMUNICATION -> "%s 일간과 %s 일간이 기준을 나눌 때는 평가보다 관찰-느낌-요청 순서가 좋습니다. 한 번에 한 주제를 다루고 답변 시간을 남기는 방식이 오해를 줄이는 데 유용합니다."
                    .formatted(p.dayMaster, counterpartDayMaster(counterpart));
            case RELATIONSHIP_TIP -> "관계의 결과를 운세로 확정하지 말고 연락 빈도, 약속 이행, 경계 존중처럼 관찰 가능한 행동을 기준으로 판단하세요. %s의 보완점은 질문하고 확인하는 습관으로 채울 수 있습니다."
                    .formatted(p.weak);
            case SPECIAL_STARS -> "신살은 사건을 예언하거나 성격을 확정하는 진단이 아닙니다. %s 일간과 전체 오행 분포(%s)를 함께 살펴, 반복해서 드러나는 강점과 과해질 때의 신호를 발견하는 전통적 참고 표지로만 활용하세요."
                    .formatted(p.dayMaster, distribution);
            case BALANCING_SPECIAL_STARS -> "신살의 이름보다 현실에서 조절할 행동을 정하는 편이 중요합니다. 강한 %s은 해야 할 일을 끝내는 데 쓰고, 상대적으로 적은 %s은 하루 한 번 멈춤·기록·휴식 중 한 가지를 실천하며 균형을 확인하세요."
                    .formatted(p.strong, p.weak);
            case STRENGTHS -> "%s 일간과 %s 중심의 분포에서 꾸준히 활용할 수 있는 강점은 시작한 일을 실제 행동으로 옮기는 힘입니다. 강점이 과해져 주변 의견을 놓치지 않도록 완료 전 한 번 검토하고, 잘된 조건을 기록해 다음 일정에도 반복해 보세요."
                    .formatted(p.dayMaster, p.strong);
            case TIMING -> "대운과 세운은 사건을 확정하는 시계가 아니라 어떤 기운을 더 의식할지 살피는 참고 자료입니다. 중요한 결정은 운세 날짜 하나보다 실제 마감, 자원, 관계자의 합의를 우선하고, 준비와 검토 시간을 나누어 배치하세요.";
            case DAILY_PRACTICE -> "해석을 생활에 적용할 때는 오늘 끝낼 행동 하나와 확인 시각 하나만 정해 보세요. %s의 강점은 실행에 사용하고, 상대적으로 적은 %s은 기록·질문·휴식 같은 작은 습관으로 보완하면 과도한 의미 부여를 줄일 수 있습니다."
                    .formatted(p.strong, p.weak);
            case SINSAL_POPULAR -> "도화살과 홍염살은 인기나 관계의 결과를 보장하는 표지가 아닙니다. 표현이 눈에 띄는 상황, 사람을 만날 때의 태도, 경계를 정하는 방식을 돌아보는 참고로 사용하고 호감은 실제 대화와 상호 존중으로 확인하세요.";
            case SINSAL_MOVEMENT -> "역마살과 망신살은 이동이나 공개 활동에서 에너지가 드러나는 방식을 살피는 표지입니다. 변화가 필요할 때는 목적·비용·회복 시간을 먼저 적고, 말이나 결과물이 널리 보이는 상황에서는 게시 전 한 번 더 확인하세요.";
            case SINSAL_INTUITION -> "화개살·귀문관살처럼 몰입과 감수성에 연결해 읽는 신살은 직관을 사실로 확정하라는 뜻이 아닙니다. 혼자 정리하는 시간과 타인에게 확인하는 시간을 나누고, 생각이 길어질 때는 관찰한 사실을 문장으로 기록해 보세요.";
            case SINSAL_INTENSITY -> "양인살·백호살·괴강살·원진살은 강한 추진력이나 긴장 패턴을 낙인찍는 표현으로 사용하지 않습니다. 반응이 빨라지는 상황에서는 결론 전 멈춤 신호를 두고, 갈등에서는 상대의 의도보다 실제 말과 행동을 확인하세요.";
            case SINSAL_TIMING -> "신살 자체는 원국에서 고정되지만 대운과 세운의 지지가 같은 표지를 다시 건드리는 시기는 달라질 수 있습니다. 10년 대운과 개별 연도를 구분해 참고하되, 그 시기에 사건이 반드시 생긴다고 해석하지 말고 준비와 점검의 계기로만 활용하세요.";
        };
    }

    private String summary(com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType type,
                           Profile p, Profile counterpart, String distribution) {
        String base = "%s 일간은 %s의 성향을 기준점으로 봅니다. 오행 분포는 %s이며, 강한 %s은 장점으로 쓰고 상대적으로 적은 %s은 생활 습관으로 보완해 보세요."
                .formatted(p.dayMaster, p.dayElement, distribution, p.strong, p.weak);
        return switch (type) {
            case SUNEUNG -> base + " 합격 여부를 단정하는 예측이 아니라 시험 준비 리듬을 점검하기 위한 참고 정보예요.";
            case OVERALL -> base + " 관계·일·재물·컨디션의 현재 조건을 함께 살피는 참고 정보로 활용하세요.";
            case LOVE -> base + " 관계의 결과를 미리 정하기보다 감정과 행동을 차분히 확인하는 참고 정보로 활용하세요.";
            case WEALTH -> base + " 수익을 보장하는 예측이 아니며 실제 예산과 계약 조건을 우선해 판단해야 합니다.";
            case COMPATIBILITY -> base + " 상대의 %s 일간과 %s 중심 분포도 함께 보되, 관계의 결론은 두 사람의 대화와 행동으로 판단하세요."
                    .formatted(counterpartDayMaster(counterpart), counterpartStrong(counterpart));
            case SINSAL -> base + " 신살은 사건이나 성격을 확정하지 않으며 장점과 주의 습관을 살펴보는 전통적 참고 표지입니다.";
        };
    }

    private String counterpartDayMaster(Profile counterpart) {
        return counterpart == null ? "상대" : counterpart.dayMaster;
    }

    private String counterpartElement(Profile counterpart) {
        return counterpart == null ? "상대 기운" : counterpart.dayElement;
    }

    private String counterpartStrong(Profile counterpart) {
        return counterpart == null ? "상대 기운" : counterpart.strong;
    }

    private String requiredText(JsonNode facts, String pointer) {
        JsonNode value = facts.at(pointer);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Fallback fact is missing: " + pointer);
        }
        return value.textValue();
    }

    private int requiredInt(JsonNode facts, String pointer) {
        JsonNode value = facts.at(pointer);
        if (!value.isIntegralNumber()) throw new IllegalArgumentException("Fallback fact is missing: " + pointer);
        return value.intValue();
    }

    private String korean(String element) {
        return switch (element) {
            case "WOOD" -> "목";
            case "FIRE" -> "화";
            case "EARTH" -> "토";
            case "METAL" -> "금";
            case "WATER" -> "수";
            default -> throw new IllegalArgumentException("Unknown element: " + element);
        };
    }

    private record Profile(String dayMaster, String dayElement, String strong, String weak,
                           int wood, int fire, int earth, int metal, int water) {}
}
