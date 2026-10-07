package com.sajuppugi.fortune.calculation.domain;

import static com.sajuppugi.fortune.calculation.domain.CalculationFacts.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RelationCalculator {
    private static final Set<String> STEM_COMBINATION = Set.of("甲己", "乙庚", "丙辛", "丁壬", "戊癸");
    private static final Set<String> STEM_CLASH = Set.of("甲庚", "乙辛", "丙壬", "丁癸");
    private static final Set<String> BRANCH_COMBINATION = Set.of("子丑", "寅亥", "卯戌", "辰酉", "巳申", "午未");
    private static final Set<String> BRANCH_CLASH = Set.of("子午", "丑未", "寅申", "卯酉", "辰戌", "巳亥");
    private static final Set<String> BRANCH_HARM = Set.of("子未", "丑午", "寅巳", "卯辰", "申亥", "酉戌");
    private static final Set<String> BRANCH_DESTRUCTION = Set.of("子酉", "丑辰", "寅亥", "卯午", "巳申", "未戌");
    private static final Set<String> BRANCH_PUNISHMENT = Set.of("寅巳", "巳申", "申寅", "丑戌", "戌未", "未丑", "子卯");
    private static final List<Set<String>> TRINES = List.of(Set.of("申", "子", "辰"), Set.of("亥", "卯", "未"), Set.of("寅", "午", "戌"), Set.of("巳", "酉", "丑"));

    private RelationCalculator() {}

    public static Relations calculate(Pillars pillars) {
        Map<PillarPosition, Pillar> known = new EnumMap<>(PillarPosition.class);
        known.put(PillarPosition.YEAR, pillars.year());
        known.put(PillarPosition.MONTH, pillars.month());
        known.put(PillarPosition.DAY, pillars.day());
        if (pillars.hour() != null) known.put(PillarPosition.HOUR, pillars.hour());

        List<Relation> result = new ArrayList<>();
        List<PillarPosition> positions = List.copyOf(known.keySet());
        for (int left = 0; left < positions.size(); left++) {
            for (int right = left + 1; right < positions.size(); right++) {
                PillarPosition lp = positions.get(left);
                PillarPosition rp = positions.get(right);
                String branch = known.get(lp).branch().hanja();
                if (branch.equals(known.get(rp).branch().hanja()) && Set.of("辰", "午", "酉", "亥").contains(branch)) {
                    result.add(new Relation(RelationType.BRANCH_PUNISHMENT, List.of(lp, rp), branch + branch + " 자형"));
                }
            }
        }
        for (int left = 0; left < positions.size(); left++) {
            for (int right = left + 1; right < positions.size(); right++) {
                PillarPosition lp = positions.get(left);
                PillarPosition rp = positions.get(right);
                Pillar l = known.get(lp);
                Pillar r = known.get(rp);
                addPair(result, STEM_COMBINATION, RelationType.STEM_COMBINATION, l.stem().hanja(), r.stem().hanja(), lp, rp, "천간합");
                addPair(result, STEM_CLASH, RelationType.STEM_CLASH, l.stem().hanja(), r.stem().hanja(), lp, rp, "천간충");
                addPair(result, BRANCH_COMBINATION, RelationType.BRANCH_COMBINATION, l.branch().hanja(), r.branch().hanja(), lp, rp, "지지육합");
                addPair(result, BRANCH_CLASH, RelationType.BRANCH_CLASH, l.branch().hanja(), r.branch().hanja(), lp, rp, "지지충");
                addPair(result, BRANCH_PUNISHMENT, RelationType.BRANCH_PUNISHMENT, l.branch().hanja(), r.branch().hanja(), lp, rp, "지지형");
                addPair(result, BRANCH_HARM, RelationType.BRANCH_HARM, l.branch().hanja(), r.branch().hanja(), lp, rp, "지지해");
                addPair(result, BRANCH_DESTRUCTION, RelationType.BRANCH_DESTRUCTION, l.branch().hanja(), r.branch().hanja(), lp, rp, "지지파");
                if (TRINES.stream().anyMatch(group -> group.contains(l.branch().hanja()) && group.contains(r.branch().hanja()))) {
                    result.add(new Relation(RelationType.BRANCH_HALF_TRINE, List.of(lp, rp), l.branch().hangul() + r.branch().hangul() + " 반합"));
                }
            }
        }
        for (Set<String> trine : TRINES) {
            List<PillarPosition> matches = positions.stream().filter(p -> trine.contains(known.get(p).branch().hanja())).toList();
            if (matches.size() == 3) result.add(new Relation(RelationType.BRANCH_TRINE, matches, String.join("", trine) + " 삼합"));
        }
        return new Relations(result);
    }

    private static void addPair(List<Relation> target, Set<String> rules, RelationType type, String left, String right,
                                PillarPosition leftPosition, PillarPosition rightPosition, String label) {
        if (rules.contains(left + right) || rules.contains(right + left)) {
            target.add(new Relation(type, List.of(leftPosition, rightPosition), left + right + " " + label));
        }
    }
}
