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
                List.of(new ExamPeriodGuide("BEFORE_FIRST_PERIOD", periods),
                        new ExamPeriodGuide("BETWEEN_PERIODS", periods),
                        new ExamPeriodGuide("FINAL_CHECK", periods)),
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
