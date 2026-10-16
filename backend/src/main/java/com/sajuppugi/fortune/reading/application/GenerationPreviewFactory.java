package com.sajuppugi.fortune.reading.application;

import com.nlf.calendar.Solar;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GenerationPreviewFactory {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Map<String, Zodiac> ZODIAC = Map.ofEntries(
            Map.entry("子", new Zodiac("mouse", "자", "쥐")),
            Map.entry("丑", new Zodiac("cow", "축", "소")),
            Map.entry("寅", new Zodiac("tiger", "인", "호랑이")),
            Map.entry("卯", new Zodiac("rabbit", "묘", "토끼")),
            Map.entry("辰", new Zodiac("dragon", "진", "용")),
            Map.entry("巳", new Zodiac("snake", "사", "뱀")),
            Map.entry("午", new Zodiac("horse", "오", "말")),
            Map.entry("未", new Zodiac("sheep", "미", "양")),
            Map.entry("申", new Zodiac("monkey", "신", "원숭이")),
            Map.entry("酉", new Zodiac("chicken", "유", "닭")),
            Map.entry("戌", new Zodiac("dog", "술", "개")),
            Map.entry("亥", new Zodiac("pig", "해", "돼지")));

    public GenerationPreview create(GenerationResumeContext context, Instant purchasedAt) {
        CalculationFacts facts = context.command().calculationFacts();
        Element strong = facts.fiveElements().dominant().stream().findFirst().orElse(facts.dayMaster().element());
        Element missing = facts.fiveElements().missing().stream().findFirst().orElse(null);
        String balance = missing == null
                ? "오행이 고르게 자리해 있어서, 어느 한쪽을 더 채우기보다 지금의 리듬을 일정하게 유지하는 게 중요해요."
                : "%s 기운은 상대적으로 적어서, 결과에서는 이를 생활 속에서 무리 없이 보완하는 방법도 함께 살펴볼게요."
                        .formatted(korean(missing));
        String message = "%s 일간을 중심으로 만세력을 차근차근 읽고 있어요. 전체 오행에서는 %s 기운이 또렷하게 보여서, 이 힘이 관계와 일, 선택 방식에서 어떻게 드러나는지 살펴보는 중이에요. %s 지금 보이는 한두 가지 특징만으로 성격이나 미래를 단정하지 않고, 대운과 세운까지 함께 확인해 현실에서 활용할 수 있는 이야기로 정리해 드릴게요."
                .formatted(facts.dayMaster().hangul(), korean(strong), balance);
        LocalDate date = purchasedAt.atZone(SEOUL).toLocalDate();
        String branch = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth())
                .getLunar().getDayZhiExact2();
        Zodiac zodiac = ZODIAC.get(branch);
        if (zodiac == null) throw new IllegalStateException("Unsupported daily branch");
        return new GenerationPreview(zodiac.key(), zodiac.branch(), zodiac.animal(), message);
    }

    private String korean(Element element) {
        return switch (element) {
            case WOOD -> "목";
            case FIRE -> "화";
            case EARTH -> "토";
            case METAL -> "금";
            case WATER -> "수";
        };
    }

    public record GenerationPreview(String animalKey, String branch, String animalName, String message) {}
    private record Zodiac(String key, String branch, String animal) {}
}
