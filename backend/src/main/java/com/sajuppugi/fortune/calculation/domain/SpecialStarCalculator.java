package com.sajuppugi.fortune.calculation.domain;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.DecadeLuck;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Luck;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Pillar;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.PillarPosition;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Pillars;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.SpecialStarActivation;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.SpecialStarOccurrence;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.SpecialStarType;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.SpecialStars;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Stem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic sinsal rule set. Liner receives these results and never calculates stars itself. */
public final class SpecialStarCalculator {
    public static final String RULE_VERSION = "sinsal-v1";

    private static final Map<String, Set<String>> HEAVENLY_NOBLE = Map.ofEntries(
            Map.entry("갑", Set.of("축", "미")), Map.entry("무", Set.of("축", "미")),
            Map.entry("경", Set.of("축", "미")), Map.entry("을", Set.of("자", "신")),
            Map.entry("기", Set.of("자", "신")), Map.entry("병", Set.of("해", "유")),
            Map.entry("정", Set.of("해", "유")), Map.entry("임", Set.of("묘", "사")),
            Map.entry("계", Set.of("묘", "사")), Map.entry("신", Set.of("인", "오")));
    private static final Map<String, Set<String>> LITERARY_NOBLE = singleByStem(
            "갑", "사", "을", "오", "병", "신", "정", "유", "무", "신",
            "기", "유", "경", "해", "신", "자", "임", "인", "계", "묘");
    private static final Map<String, Set<String>> YANG_BLADE = singleByStem(
            "갑", "묘", "을", "인", "병", "오", "정", "사", "무", "오",
            "기", "사", "경", "유", "신", "신", "임", "자", "계", "해");
    private static final Map<String, Set<String>> RED_CHARM = singleByStem(
            "갑", "오", "을", "신", "병", "인", "정", "미", "무", "진",
            "기", "진", "경", "술", "신", "유", "임", "자", "계", "신");
    private static final Set<String> WHITE_TIGER_DAYS = Set.of("갑진", "을미", "병술", "정축", "무진", "임술", "계축");
    private static final Set<String> GANG_STRENGTH_DAYS = Set.of("경진", "경술", "임진", "무술");
    private static final List<String> BRANCH_ORDER = List.of("자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해");

    private SpecialStarCalculator() {}

    public static SpecialStars calculate(Pillars pillars, Stem dayMaster, Luck luck) {
        Map<SpecialStarType, LinkedHashSet<PillarPosition>> positions = new EnumMap<>(SpecialStarType.class);
        String dayStem = dayMaster.hangul();
        String yearBranch = pillars.year().branch().hangul();
        String dayBranch = pillars.day().branch().hangul();

        matchTargets(positions, SpecialStarType.HEAVENLY_NOBLE, pillars, HEAVENLY_NOBLE.get(dayStem));
        matchTargets(positions, SpecialStarType.LITERARY_NOBLE, pillars, LITERARY_NOBLE.get(dayStem));
        matchTargets(positions, SpecialStarType.YANG_BLADE, pillars, YANG_BLADE.get(dayStem));
        matchTargets(positions, SpecialStarType.RED_CHARM, pillars, RED_CHARM.get(dayStem));
        for (String base : new LinkedHashSet<>(List.of(yearBranch, dayBranch))) {
            matchTargets(positions, SpecialStarType.PEACH_BLOSSOM, pillars, Set.of(groupTarget(base, "유", "묘", "오", "자")));
            matchTargets(positions, SpecialStarType.TRAVEL_HORSE, pillars, Set.of(groupTarget(base, "인", "신", "해", "사")));
            matchTargets(positions, SpecialStarType.CANOPY, pillars, Set.of(groupTarget(base, "진", "술", "축", "미")));
            matchTargets(positions, SpecialStarType.DISGRACE, pillars, Set.of(groupTarget(base, "해", "사", "신", "인")));
        }
        matchTargets(positions, SpecialStarType.VOID, pillars, voidBranches(pillars.day()));
        matchPairs(positions, SpecialStarType.YUANJIN, pillars,
                Set.of("자미", "축오", "인유", "묘신", "진해", "사술"));
        matchPairs(positions, SpecialStarType.SPIRIT_GATE, pillars,
                Set.of("자유", "축오", "인미", "묘신", "진해", "사술"));
        if (WHITE_TIGER_DAYS.contains(display(pillars.day()))) add(positions, SpecialStarType.WHITE_TIGER, PillarPosition.DAY);
        if (GANG_STRENGTH_DAYS.contains(display(pillars.day()))) add(positions, SpecialStarType.GANG_STRENGTH, PillarPosition.DAY);

        List<SpecialStarOccurrence> natal = java.util.Arrays.stream(SpecialStarType.values())
                .map(type -> new SpecialStarOccurrence(type,
                        List.copyOf(positions.getOrDefault(type, new LinkedHashSet<>())),
                        positions.getOrDefault(type, new LinkedHashSet<>()).size()))
                .sorted((left, right) -> Integer.compare(right.count(), left.count())).toList();
        int maximum = natal.stream().mapToInt(SpecialStarOccurrence::count).max().orElse(0);
        List<SpecialStarType> abundant = maximum < 2 ? List.of() : natal.stream()
                .filter(item -> item.count() == maximum).map(SpecialStarOccurrence::type).toList();
        return new SpecialStars(RULE_VERSION, natal, abundant, activations(pillars, dayMaster, luck));
    }

    private static List<SpecialStarActivation> activations(Pillars natal, Stem dayMaster, Luck luck) {
        List<SpecialStarActivation> values = new ArrayList<>();
        if (luck == null) return values;
        for (DecadeLuck decade : luck.decades()) {
            for (SpecialStarType type : activeTypes(natal, dayMaster, decade.pillar())) {
                values.add(new SpecialStarActivation(type, decade.startAge(), decade.endAge(), null, decade.pillar()));
            }
        }
        for (var annual : luck.annual()) {
            for (SpecialStarType type : activeTypes(natal, dayMaster, annual.pillar())) {
                values.add(new SpecialStarActivation(type, null, null, annual.year(), annual.pillar()));
            }
        }
        return List.copyOf(values);
    }

    private static Set<SpecialStarType> activeTypes(Pillars natal, Stem dayMaster, Pillar transit) {
        Pillars withTransit = new Pillars(natal.year(), natal.month(), natal.day(), transit);
        Set<SpecialStarType> active = new LinkedHashSet<>();
        calculateWithoutActivations(withTransit, dayMaster).forEach((type, positions) -> {
            if (positions.contains(PillarPosition.HOUR)) active.add(type);
        });
        if (WHITE_TIGER_DAYS.contains(display(transit))) active.add(SpecialStarType.WHITE_TIGER);
        if (GANG_STRENGTH_DAYS.contains(display(transit))) active.add(SpecialStarType.GANG_STRENGTH);
        return active;
    }

    private static Map<SpecialStarType, LinkedHashSet<PillarPosition>> calculateWithoutActivations(
            Pillars pillars, Stem dayMaster) {
        Map<SpecialStarType, LinkedHashSet<PillarPosition>> result = new EnumMap<>(SpecialStarType.class);
        String stem = dayMaster.hangul();
        matchTargets(result, SpecialStarType.HEAVENLY_NOBLE, pillars, HEAVENLY_NOBLE.get(stem));
        matchTargets(result, SpecialStarType.LITERARY_NOBLE, pillars, LITERARY_NOBLE.get(stem));
        matchTargets(result, SpecialStarType.YANG_BLADE, pillars, YANG_BLADE.get(stem));
        matchTargets(result, SpecialStarType.RED_CHARM, pillars, RED_CHARM.get(stem));
        for (String base : new LinkedHashSet<>(List.of(
                pillars.year().branch().hangul(), pillars.day().branch().hangul()))) {
            matchTargets(result, SpecialStarType.PEACH_BLOSSOM, pillars, Set.of(groupTarget(base, "유", "묘", "오", "자")));
            matchTargets(result, SpecialStarType.TRAVEL_HORSE, pillars, Set.of(groupTarget(base, "인", "신", "해", "사")));
            matchTargets(result, SpecialStarType.CANOPY, pillars, Set.of(groupTarget(base, "진", "술", "축", "미")));
            matchTargets(result, SpecialStarType.DISGRACE, pillars, Set.of(groupTarget(base, "해", "사", "신", "인")));
        }
        matchTargets(result, SpecialStarType.VOID, pillars, voidBranches(pillars.day()));
        matchPairs(result, SpecialStarType.YUANJIN, pillars, Set.of("자미", "축오", "인유", "묘신", "진해", "사술"));
        matchPairs(result, SpecialStarType.SPIRIT_GATE, pillars, Set.of("자유", "축오", "인미", "묘신", "진해", "사술"));
        if (WHITE_TIGER_DAYS.contains(display(pillars.day()))) add(result, SpecialStarType.WHITE_TIGER, PillarPosition.DAY);
        if (GANG_STRENGTH_DAYS.contains(display(pillars.day()))) add(result, SpecialStarType.GANG_STRENGTH, PillarPosition.DAY);
        return result;
    }

    private static void matchTargets(Map<SpecialStarType, LinkedHashSet<PillarPosition>> values, SpecialStarType type,
                                     Pillars pillars, Set<String> targets) {
        if (targets == null) return;
        for (var item : positioned(pillars)) if (targets.contains(item.pillar.branch().hangul())) add(values, type, item.position);
    }

    private static void matchPairs(Map<SpecialStarType, LinkedHashSet<PillarPosition>> values, SpecialStarType type,
                                   Pillars pillars, Set<String> pairs) {
        List<PositionedPillar> all = positioned(pillars);
        for (int left = 0; left < all.size(); left++) for (int right = left + 1; right < all.size(); right++) {
            String a = all.get(left).pillar.branch().hangul();
            String b = all.get(right).pillar.branch().hangul();
            if (pairs.contains(a + b) || pairs.contains(b + a)) {
                add(values, type, all.get(left).position);
                add(values, type, all.get(right).position);
            }
        }
    }

    private static List<PositionedPillar> positioned(Pillars pillars) {
        List<PositionedPillar> values = new ArrayList<>(List.of(
                new PositionedPillar(PillarPosition.YEAR, pillars.year()),
                new PositionedPillar(PillarPosition.MONTH, pillars.month()),
                new PositionedPillar(PillarPosition.DAY, pillars.day())));
        if (pillars.hour() != null) values.add(new PositionedPillar(PillarPosition.HOUR, pillars.hour()));
        return values;
    }

    private static void add(Map<SpecialStarType, LinkedHashSet<PillarPosition>> values,
                            SpecialStarType type, PillarPosition position) {
        values.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(position);
    }

    private static String groupTarget(String branch, String water, String fire, String metal, String wood) {
        if (Set.of("신", "자", "진").contains(branch)) return water;
        if (Set.of("인", "오", "술").contains(branch)) return fire;
        if (Set.of("사", "유", "축").contains(branch)) return metal;
        if (Set.of("해", "묘", "미").contains(branch)) return wood;
        throw new IllegalArgumentException("Unknown branch: " + branch);
    }

    private static Set<String> voidBranches(Pillar day) {
        List<String> stems = List.of("갑", "을", "병", "정", "무", "기", "경", "신", "임", "계");
        int stemIndex = stems.indexOf(day.stem().hangul());
        int branchIndex = BRANCH_ORDER.indexOf(day.branch().hangul());
        int xunStart = Math.floorMod(branchIndex - stemIndex, 12);
        return Set.of(BRANCH_ORDER.get(Math.floorMod(xunStart - 2, 12)),
                BRANCH_ORDER.get(Math.floorMod(xunStart - 1, 12)));
    }

    private static String display(Pillar pillar) {
        return pillar.stem().hangul() + pillar.branch().hangul();
    }

    private static Map<String, Set<String>> singleByStem(String... values) {
        Map<String, Set<String>> result = new java.util.HashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put(values[index], Set.of(values[index + 1]));
        return Map.copyOf(result);
    }

    private record PositionedPillar(PillarPosition position, Pillar pillar) {}
}
