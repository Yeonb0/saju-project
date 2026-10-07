package com.sajuppugi.fortune.calculation.domain;

import static com.sajuppugi.fortune.calculation.domain.CalculationFacts.*;

import java.util.List;
import java.util.Map;

public final class SajuRuleBook {
    private SajuRuleBook() {}

    public static final List<String> STEM_HANJA = List.of("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸");
    public static final List<String> STEM_HANGUL = List.of("갑", "을", "병", "정", "무", "기", "경", "신", "임", "계");
    public static final List<String> BRANCH_HANJA = List.of("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥");
    public static final List<String> BRANCH_HANGUL = List.of("자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해");

    private static final List<Element> STEM_ELEMENTS = List.of(
            Element.WOOD, Element.WOOD, Element.FIRE, Element.FIRE, Element.EARTH,
            Element.EARTH, Element.METAL, Element.METAL, Element.WATER, Element.WATER);
    private static final List<Element> BRANCH_ELEMENTS = List.of(
            Element.WATER, Element.EARTH, Element.WOOD, Element.WOOD, Element.EARTH, Element.FIRE,
            Element.FIRE, Element.EARTH, Element.METAL, Element.METAL, Element.EARTH, Element.WATER);
    private static final List<Integer> BRANCH_MAIN_STEM = List.of(9, 5, 0, 1, 4, 2, 3, 5, 6, 7, 4, 8);

    private static final Map<String, TwelveStage> STAGES = Map.ofEntries(
            Map.entry("长生", TwelveStage.BIRTH), Map.entry("沐浴", TwelveStage.BATH),
            Map.entry("冠带", TwelveStage.CROWN), Map.entry("临官", TwelveStage.OFFICIAL),
            Map.entry("帝旺", TwelveStage.PROSPERITY), Map.entry("衰", TwelveStage.DECLINE),
            Map.entry("病", TwelveStage.SICKNESS), Map.entry("死", TwelveStage.DEATH),
            Map.entry("墓", TwelveStage.TOMB), Map.entry("绝", TwelveStage.EXTINCTION),
            Map.entry("胎", TwelveStage.CONCEPTION), Map.entry("养", TwelveStage.NOURISHMENT));

    public static Pillar pillar(String ganji) {
        if (ganji == null || ganji.length() != 2) {
            throw new IllegalArgumentException("Invalid ganji: " + ganji);
        }
        int stemIndex = STEM_HANJA.indexOf(ganji.substring(0, 1));
        int branchIndex = BRANCH_HANJA.indexOf(ganji.substring(1, 2));
        if (stemIndex < 0 || branchIndex < 0) {
            throw new IllegalArgumentException("Unknown ganji: " + ganji);
        }
        return new Pillar(stem(stemIndex), branch(branchIndex));
    }

    public static Stem stem(int index) {
        int normalized = Math.floorMod(index, 10);
        return new Stem(
                STEM_HANGUL.get(normalized), STEM_HANJA.get(normalized), STEM_ELEMENTS.get(normalized),
                normalized % 2 == 0 ? YinYang.YANG : YinYang.YIN);
    }

    public static Branch branch(int index) {
        int normalized = Math.floorMod(index, 12);
        return new Branch(
                BRANCH_HANGUL.get(normalized), BRANCH_HANJA.get(normalized), BRANCH_ELEMENTS.get(normalized),
                normalized % 2 == 0 ? YinYang.YANG : YinYang.YIN);
    }

    public static TenGod tenGod(Stem dayMaster, Stem target) {
        if (dayMaster.hanja().equals(target.hanja())) return TenGod.DAY_MASTER;
        int dayElement = dayMaster.element().ordinal();
        int targetElement = target.element().ordinal();
        boolean samePolarity = dayMaster.yinYang() == target.yinYang();
        if (dayElement == targetElement) return samePolarity ? TenGod.FRIEND : TenGod.ROB_WEALTH;
        if ((dayElement + 1) % 5 == targetElement) return samePolarity ? TenGod.EATING_GOD : TenGod.HURTING_OFFICER;
        if ((dayElement + 2) % 5 == targetElement) return samePolarity ? TenGod.INDIRECT_WEALTH : TenGod.DIRECT_WEALTH;
        if ((targetElement + 1) % 5 == dayElement) return samePolarity ? TenGod.INDIRECT_RESOURCE : TenGod.DIRECT_RESOURCE;
        return samePolarity ? TenGod.SEVEN_KILLINGS : TenGod.DIRECT_OFFICER;
    }

    public static TenGod branchTenGod(Stem dayMaster, Branch branch) {
        return tenGod(dayMaster, stem(BRANCH_MAIN_STEM.get(BRANCH_HANJA.indexOf(branch.hanja()))));
    }

    public static TwelveStage stage(String chinese) {
        TwelveStage stage = STAGES.get(chinese);
        if (stage == null) throw new IllegalArgumentException("Unknown twelve stage: " + chinese);
        return stage;
    }
}
