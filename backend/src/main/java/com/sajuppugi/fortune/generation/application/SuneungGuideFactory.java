package com.sajuppugi.fortune.generation.application;

import com.sajuppugi.fortune.generation.domain.GenerationModels.ExamPeriodGuide;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FoodRecommendation;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.PreparationItem;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey;
import com.sajuppugi.fortune.generation.domain.GenerationModels.SuneungGuide;
import java.util.List;

public final class SuneungGuideFactory {
    private SuneungGuideFactory() {}

    public static SuneungGuide fromSections(List<GeneratedSection> sections) {
        String periods = content(sections, SectionKey.EXAM_PERIODS);
        return new SuneungGuide(content(sections, SectionKey.SUMMARY), content(sections, SectionKey.EXAM_DAY),
                List.of(
                        new ExamPeriodGuide("BEFORE_ENTRY", periods + " 입실 후에는 좌석과 준비물을 확인하고 첫 교시에 사용할 호흡 순서를 정하세요."),
                        new ExamPeriodGuide("KOREAN", "첫 교시는 체감 난도보다 정해 둔 풀이 순서를 지키세요. 막힌 문항은 표시하고 다음 지문으로 이동합니다."),
                        new ExamPeriodGuide("MATH", "수학에서는 조건과 구하려는 값을 먼저 표시하고, 계산이 길어지면 중간 값을 적어 검산 가능하게 남기세요."),
                        new ExamPeriodGuide("LUNCH", "점심에는 답을 맞춰 보지 말고 평소 먹던 양으로 식사한 뒤 물과 화장실, 다음 교시 준비물을 확인하세요."),
                        new ExamPeriodGuide("ENGLISH", "듣기 전 방송과 마킹 상태를 확인하고, 독해는 배점보다 본인에게 안정적인 문항 순서를 유지하세요."),
                        new ExamPeriodGuide("ELECTIVES", "한국사와 탐구는 과목·문제지 순서를 먼저 확인하고, 과목 전환 때 답안 위치를 다시 점검하세요."),
                        new ExamPeriodGuide("FINAL_CHECK", "종료 전에는 새 풀이를 넓히기보다 미표기 문항, 밀려 쓴 답, 수험 정보 기입을 차례로 확인하세요.")),
                List.of(content(sections, SectionKey.FOCUS)),
                List.of(new FoodRecommendation("평소 잘 맞았던 식사", content(sections, SectionKey.MEAL))),
                List.of(new PreparationItem("ADMISSION_TICKET", "수험표", "DOCUMENT"),
                        new PreparationItem("IDENTIFICATION", "신분증", "DOCUMENT"),
                        new PreparationItem("WRITING_TOOLS", "허용된 필기구", "WRITING_TOOL"),
                        new PreparationItem("WATCH", "허용된 시계", "TIMEPIECE"),
                        new PreparationItem("WATER", "물", "FOOD"),
                        new PreparationItem("OUTERWEAR", "체온 조절용 겉옷", "CLOTHING"),
                        new PreparationItem("GUIDE", content(sections, SectionKey.PREPARATION), "OTHER")),
                List.of(content(sections, SectionKey.ANXIETY_MANAGEMENT)),
                content(sections, SectionKey.MISSING_ELEMENT));
    }

    private static String content(List<GeneratedSection> sections, SectionKey key) {
        return sections.stream().filter(section -> section.key() == key).findFirst()
                .map(GeneratedSection::content).orElse("제공할 수 있는 안내가 없습니다.");
    }
}
