package com.sajuppugi.fortune.calculation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Versioned, immutable boundary between deterministic calculation and generated prose.
 * Raw names, user identifiers and the original birth date/time must never be added here.
 */
public record CalculationFacts(
        Meta meta,
        CalendarDates calendarDates,
        Pillars pillars,
        Stem dayMaster,
        FiveElements fiveElements,
        TenGods tenGods,
        TwelveStages twelveStages,
        Relations relations,
        Luck luck) {

    public record Meta(
            String engine,
            String engineVersion,
            String calculationVersion,
            String timezone,
            CalculationPolicy.DayBoundary dayBoundary,
            boolean birthTimeKnown) {}

    public record CalendarDates(
            LocalDate solarDate,
            LocalDate lunarDate,
            boolean lunarLeapMonth) {}

    public record Pillars(Pillar year, Pillar month, Pillar day, Pillar hour) {}

    public record Pillar(Stem stem, Branch branch) {
        @JsonProperty("display")
        public String display() {
            return stem.hangul() + branch.hangul();
        }
    }

    public record Stem(String hangul, String hanja, Element element, YinYang yinYang) {}

    public record Branch(String hangul, String hanja, Element element, YinYang yinYang) {}

    public enum Element { WOOD, FIRE, EARTH, METAL, WATER }

    public enum YinYang { YANG, YIN }

    public record FiveElements(Map<Element, Integer> counts, List<Element> dominant, List<Element> missing) {
        public FiveElements {
            counts = Map.copyOf(counts);
            dominant = List.copyOf(dominant);
            missing = List.copyOf(missing);
        }
    }

    public enum TenGod {
        DAY_MASTER,
        FRIEND,
        ROB_WEALTH,
        EATING_GOD,
        HURTING_OFFICER,
        INDIRECT_WEALTH,
        DIRECT_WEALTH,
        SEVEN_KILLINGS,
        DIRECT_OFFICER,
        INDIRECT_RESOURCE,
        DIRECT_RESOURCE
    }

    public record TenGodPosition(TenGod stem, TenGod branch) {}

    public record TenGods(
            TenGodPosition year,
            TenGodPosition month,
            TenGodPosition day,
            TenGodPosition hour) {}

    public enum TwelveStage {
        BIRTH, BATH, CROWN, OFFICIAL, PROSPERITY, DECLINE,
        SICKNESS, DEATH, TOMB, EXTINCTION, CONCEPTION, NOURISHMENT
    }

    public record TwelveStages(
            TwelveStage year,
            TwelveStage month,
            TwelveStage day,
            TwelveStage hour) {}

    public enum PillarPosition { YEAR, MONTH, DAY, HOUR }

    public enum RelationType {
        STEM_COMBINATION, STEM_CLASH,
        BRANCH_COMBINATION, BRANCH_TRINE, BRANCH_HALF_TRINE,
        BRANCH_CLASH, BRANCH_PUNISHMENT, BRANCH_HARM, BRANCH_DESTRUCTION
    }

    public record Relation(RelationType type, List<PillarPosition> positions, String display) {
        public Relation {
            positions = List.copyOf(positions);
        }
    }

    public record Relations(List<Relation> values) {
        public Relations {
            values = List.copyOf(values);
        }
    }

    public enum LuckDirection { FORWARD, BACKWARD }

    public record Luck(
            LuckDirection direction,
            Integer startAge,
            LocalDate startDate,
            List<DecadeLuck> decades,
            List<AnnualLuck> annual) {
        public Luck {
            decades = List.copyOf(decades);
            annual = List.copyOf(annual);
        }
    }

    public record DecadeLuck(int startAge, int endAge, Pillar pillar) {}

    public record AnnualLuck(int year, Pillar pillar) {}
}
