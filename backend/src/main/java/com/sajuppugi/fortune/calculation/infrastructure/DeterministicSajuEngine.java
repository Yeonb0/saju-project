package com.sajuppugi.fortune.calculation.infrastructure;

import static com.sajuppugi.fortune.calculation.domain.CalculationFacts.*;
import static com.sajuppugi.fortune.calculation.domain.SajuRuleBook.*;

import com.github.usingsky.calendar.KoreanLunarCalendar;
import com.nlf.calendar.EightChar;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import com.nlf.calendar.eightchar.DaYun;
import com.nlf.calendar.eightchar.LiuNian;
import com.nlf.calendar.eightchar.Yun;
import com.sajuppugi.fortune.calculation.application.SajuCalculationUseCase;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.domain.RelationCalculator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DeterministicSajuEngine implements SajuCalculationUseCase {
    private static final String ENGINE_VERSION = "korean-lunar-0.4.0+lunar-java-1.7.7";

    @Override
    public CalculationFacts calculate(BirthInput input, CalculationPolicy policy) {
        if (!policy.zoneId().getId().equals("Asia/Seoul")) {
            throw new IllegalArgumentException("Only Asia/Seoul is supported");
        }
        CalendarDates dates = convert(input);
        if (input.birthTimeUnknown() && crossesPillarBoundary(dates.solarDate())) {
            throw new UnsupportedUnknownBirthTimeException(dates.solarDate());
        }
        LocalTime calculationTime = input.birthTimeUnknown() ? LocalTime.NOON : input.birthTime();
        LocalDateTime kst = LocalDateTime.of(dates.solarDate(), calculationTime);

        // lunar-java embeds solar-term timestamps in UTC+8. Shifting the KST wall clock by one hour
        // preserves the same instant for exact year/month pillar boundary decisions.
        LocalDateTime termClock = kst.minusHours(1);
        Lunar termLunar = solar(termClock).getLunar();
        Lunar wallLunar = solar(kst).getLunar();
        Pillar year = pillar(termLunar.getYearInGanZhiExact());
        Pillar month = pillar(termLunar.getMonthInGanZhiExact());
        Pillar day = pillar(wallLunar.getDayInGanZhiExact2());
        Pillar hour = input.birthTimeUnknown() ? null : pillar(wallLunar.getTimeInGanZhi());
        Pillars pillars = new Pillars(year, month, day, hour);
        Stem dayMaster = day.stem();
        EightChar eightChar = wallLunar.getEightChar();
        eightChar.setSect(policy.dayBoundary() == CalculationPolicy.DayBoundary.MIDNIGHT ? 2 : 1);

        return new CalculationFacts(
                new Meta("deterministic-java", ENGINE_VERSION, policy.version(), policy.zoneId().getId(), policy.dayBoundary(), !input.birthTimeUnknown()),
                dates,
                pillars,
                dayMaster,
                fiveElements(pillars),
                tenGods(pillars, dayMaster),
                stages(eightChar, input.birthTimeUnknown()),
                RelationCalculator.calculate(pillars),
                luck(input, eightChar, month, input.birthTimeUnknown()));
    }

    private CalendarDates convert(BirthInput input) {
        KoreanLunarCalendar calendar = KoreanLunarCalendar.getInstance();
        // KoreanLunarCalendar exposes one mutable singleton. Keep its set/read sequence atomic so
        // concurrent requests cannot observe another request's converted date.
        synchronized (calendar) {
            boolean valid;
            if (input.calendarType() == BirthInput.CalendarType.LUNAR) {
                valid = calendar.setLunarDate(input.birthDate().getYear(), input.birthDate().getMonthValue(),
                        input.birthDate().getDayOfMonth(), input.leapMonth());
            } else {
                valid = calendar.setSolarDate(input.birthDate().getYear(), input.birthDate().getMonthValue(),
                        input.birthDate().getDayOfMonth());
            }
            if (!valid) throw new IllegalArgumentException("Unsupported or invalid Korean calendar date");
            return new CalendarDates(
                    LocalDate.of(calendar.getSolarYear(), calendar.getSolarMonth(), calendar.getSolarDay()),
                    LocalDate.of(calendar.getLunarYear(), calendar.getLunarMonth(), calendar.getLunarDay()),
                    calendar.isIntercalation());
        }
    }

    private Solar solar(LocalDateTime dateTime) {
        return Solar.fromYmdHms(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
    }

    private boolean crossesPillarBoundary(LocalDate date) {
        Lunar start = solar(LocalDateTime.of(date, LocalTime.MIN).minusHours(1)).getLunar();
        Lunar end = solar(LocalDateTime.of(date, LocalTime.of(23, 59, 59)).minusHours(1)).getLunar();
        return !start.getYearInGanZhiExact().equals(end.getYearInGanZhiExact())
                || !start.getMonthInGanZhiExact().equals(end.getMonthInGanZhiExact());
    }

    private FiveElements fiveElements(Pillars pillars) {
        Map<Element, Integer> counts = new EnumMap<>(Element.class);
        for (Element element : Element.values()) counts.put(element, 0);
        for (Pillar pillar : List.of(pillars.year(), pillars.month(), pillars.day())) addElements(counts, pillar);
        if (pillars.hour() != null) addElements(counts, pillars.hour());
        int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Element> dominant = counts.entrySet().stream().filter(e -> e.getValue() == maximum && maximum > 0).map(Map.Entry::getKey).toList();
        List<Element> missing = counts.entrySet().stream().filter(e -> e.getValue() == 0).map(Map.Entry::getKey).toList();
        return new FiveElements(counts, dominant, missing);
    }

    private void addElements(Map<Element, Integer> counts, Pillar pillar) {
        counts.compute(pillar.stem().element(), (key, value) -> value + 1);
        counts.compute(pillar.branch().element(), (key, value) -> value + 1);
    }

    private TenGods tenGods(Pillars pillars, Stem dayMaster) {
        return new TenGods(position(dayMaster, pillars.year()), position(dayMaster, pillars.month()),
                position(dayMaster, pillars.day()), pillars.hour() == null ? null : position(dayMaster, pillars.hour()));
    }

    private TenGodPosition position(Stem dayMaster, Pillar target) {
        return new TenGodPosition(tenGod(dayMaster, target.stem()), branchTenGod(dayMaster, target.branch()));
    }

    private TwelveStages stages(EightChar value, boolean unknown) {
        return new TwelveStages(stage(value.getYearDiShi()), stage(value.getMonthDiShi()), stage(value.getDayDiShi()),
                unknown ? null : stage(value.getTimeDiShi()));
    }

    private Luck luck(BirthInput input, EightChar eightChar, Pillar month, boolean unknown) {
        if (unknown || input.gender() == Gender.UNSPECIFIED) return new Luck(null, null, null, List.of(), List.of());
        Yun yun = eightChar.getYun(input.gender() == Gender.MALE ? 1 : 0, 1);
        List<DecadeLuck> decades = new ArrayList<>();
        List<AnnualLuck> annual = new ArrayList<>();
        for (DaYun cycle : yun.getDaYun(9)) {
            if (cycle.getIndex() == 0 || cycle.getGanZhi().isBlank()) continue;
            Pillar cyclePillar = pillar(cycle.getGanZhi());
            decades.add(new DecadeLuck(cycle.getStartAge(), cycle.getEndAge(), cyclePillar));
            for (LiuNian year : cycle.getLiuNian(10)) annual.add(new AnnualLuck(year.getYear(), pillar(year.getGanZhi())));
        }
        Solar start = yun.getStartSolar();
        return new Luck(yun.isForward() ? LuckDirection.FORWARD : LuckDirection.BACKWARD,
                decades.isEmpty() ? null : decades.getFirst().startAge(),
                LocalDate.of(start.getYear(), start.getMonth(), start.getDay()), decades, annual);
    }

    public static class UnsupportedUnknownBirthTimeException extends IllegalArgumentException {
        private final LocalDate solarDate;

        public UnsupportedUnknownBirthTimeException(LocalDate solarDate) {
            super("Birth time is required on a solar-term boundary date: " + solarDate);
            this.solarDate = solarDate;
        }

        public LocalDate solarDate() {
            return solarDate;
        }
    }
}
