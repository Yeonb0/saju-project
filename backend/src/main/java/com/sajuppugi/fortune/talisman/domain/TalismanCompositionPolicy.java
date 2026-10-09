package com.sajuppugi.fortune.talisman.domain;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class TalismanCompositionPolicy {
    private static final List<Element> ELEMENT_ORDER = List.of(
            Element.WOOD, Element.FIRE, Element.EARTH, Element.METAL, Element.WATER);
    private static final Map<Element, String> COLORS = Map.of(
            Element.WOOD, "초록",
            Element.FIRE, "붉은",
            Element.EARTH, "노란",
            Element.METAL, "하얀",
            Element.WATER, "검푸른");
    private static final Map<Element, List<String>> ELEMENT_PHRASES = phrases();
    private static final Map<FortuneType, String> FORTUNE_THEMES = Map.of(
            FortuneType.OVERALL, "오늘의 흐름",
            FortuneType.LOVE, "따뜻한 인연",
            FortuneType.WEALTH, "단단한 결실",
            FortuneType.COMPATIBILITY, "서로의 마음",
            FortuneType.SINSAL, "평온한 기운",
            FortuneType.SUNEUNG, "차분한 집중");

    private final RandomGenerator random;

    public TalismanCompositionPolicy() {
        this(new SecureRandom());
    }

    public TalismanCompositionPolicy(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public Composition compose(FortuneType fortuneType, CalculationFacts facts) {
        Objects.requireNonNull(fortuneType, "fortuneType");
        Objects.requireNonNull(facts, "facts");
        Element element = selectElement(facts.fiveElements());
        Animal[] animals = Animal.values();
        Animal animal = animals[random.nextInt(animals.length)];
        List<String> phrases = ELEMENT_PHRASES.get(element);
        String phrase = FORTUNE_THEMES.get(fortuneType) + ", " + phrases.get(random.nextInt(phrases.size()));
        String description = elementName(element) + " 기운을 채울 수 있도록 " + COLORS.get(element)
                + "빛을 담았어요.";
        return new Composition(element, animal, phrase, description);
    }

    Element selectElement(CalculationFacts.FiveElements fiveElements) {
        Objects.requireNonNull(fiveElements, "fiveElements");
        for (Element element : ELEMENT_ORDER) {
            if (fiveElements.missing().contains(element)) return element;
        }
        int minimum = ELEMENT_ORDER.stream()
                .mapToInt(element -> fiveElements.counts().getOrDefault(element, 0))
                .min()
                .orElseThrow();
        return ELEMENT_ORDER.stream()
                .filter(element -> fiveElements.counts().getOrDefault(element, 0) == minimum)
                .findFirst()
                .orElseThrow();
    }

    private static Map<Element, List<String>> phrases() {
        Map<Element, List<String>> values = new EnumMap<>(Element.class);
        values.put(Element.WOOD, List.of("새로운 시작을 믿어요", "한 걸음씩 자라나요", "가능성을 펼쳐 봐요"));
        values.put(Element.FIRE, List.of("마음의 불빛을 밝혀요", "용기를 오래 지켜요", "밝은 기운을 이어 가요"));
        values.put(Element.EARTH, List.of("내 중심을 단단히 세워요", "차분하게 자리를 지켜요", "꾸준함이 힘이 돼요"));
        values.put(Element.METAL, List.of("선명한 마음으로 나아가요", "필요한 것에 집중해요", "단단한 결심을 지켜요"));
        values.put(Element.WATER, List.of("유연하게 길을 찾아요", "고요한 힘을 모아요", "흐름을 믿고 나아가요"));
        return Map.copyOf(values);
    }

    private static String elementName(Element element) {
        return switch (element) {
            case WOOD -> "나무";
            case FIRE -> "불";
            case EARTH -> "흙";
            case METAL -> "쇠";
            case WATER -> "물";
        };
    }

    public record Composition(Element element, Animal animal, String phrase, String description) {}
}
