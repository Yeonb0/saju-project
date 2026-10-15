package com.sajuppugi.fortune.talisman.domain;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import java.security.SecureRandom;
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
        return compose(fortuneType, facts.fiveElements());
    }

    /** Compose from the immutable subset retained in a completed reading result. */
    public Composition compose(FortuneType fortuneType, CalculationFacts.FiveElements fiveElements) {
        Objects.requireNonNull(fortuneType, "fortuneType");
        Element element = selectElement(fiveElements);
        Animal[] animals = Animal.values();
        Animal animal = animals[random.nextInt(animals.length)];
        String description = elementName(element) + " 기운을 채울 수 있도록 " + COLORS.get(element)
                + "빛을 담았어요.";
        String elementKey = element.name().toLowerCase();
        String animalKey = "animal_" + (animal == Animal.GOAT ? "sheep" : animal.name().toLowerCase());
        TalismanComposition visual = new TalismanComposition("border_" + elementKey + "_" + (random.nextInt(3) + 1),
                "paper_" + elementKey + "_" + (random.nextInt(3) + 1), animalKey,
                "item_" + TalismanCatalog.fortuneKey(fortuneType) + "_" + (random.nextInt(3) + 1),
                TalismanCatalog.phrase(fortuneType, random.nextInt(100)), "seal_default");
        return new Composition(element, animal, visual.phrase().text(), description, visual);
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

    private static String elementName(Element element) {
        return switch (element) {
            case WOOD -> "나무";
            case FIRE -> "불";
            case EARTH -> "흙";
            case METAL -> "쇠";
            case WATER -> "물";
        };
    }

    public record Composition(Element element, Animal animal, String phrase, String description,
                              TalismanComposition visual) {}
}
