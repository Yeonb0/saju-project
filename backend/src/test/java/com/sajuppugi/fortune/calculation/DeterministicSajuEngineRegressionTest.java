package com.sajuppugi.fortune.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeterministicSajuEngineRegressionTest {
    private final DeterministicSajuEngine engine = new DeterministicSajuEngine();

    @ParameterizedTest(name = "{0} {1} => {2} {3} {4} {5}")
    @MethodSource("goldenCases")
    void matchesFiftyCrossValidatedGoldenCases(
            String date, String time, String year, String month, String day, String hour) {
        CalculationFacts facts = calculate(date, time, CalendarType.SOLAR, false, Gender.MALE);

        assertThat(facts.pillars().year().display()).isEqualTo(year);
        assertThat(facts.pillars().month().display()).isEqualTo(month);
        assertThat(facts.pillars().day().display()).isEqualTo(day);
        assertThat(facts.pillars().hour().display()).isEqualTo(hour);
    }

    @Test
    void convertsKoreanLeapLunarMonthWithoutChangingTheCanonicalInputSemantics() {
        CalculationFacts regular = calculate("2020-04-15", "09:00", CalendarType.LUNAR, false, Gender.FEMALE);
        CalculationFacts leap = calculate("2020-04-15", "09:00", CalendarType.LUNAR, true, Gender.FEMALE);

        assertThat(regular.calendarDates().solarDate()).isEqualTo(LocalDate.of(2020, 5, 7));
        assertThat(leap.calendarDates().solarDate()).isEqualTo(LocalDate.of(2020, 6, 6));
        assertThat(leap.calendarDates().lunarLeapMonth()).isTrue();
    }

    @Test
    void omitsTimeDependentFactsWhenBirthTimeIsUnknown() {
        BirthInput input = new BirthInput(LocalDate.of(2004, 3, 15), null, true, CalendarType.SOLAR, false, Gender.FEMALE);
        CalculationFacts facts = engine.calculate(input, CalculationPolicy.CURRENT);

        assertThat(facts.pillars().hour()).isNull();
        assertThat(facts.tenGods().hour()).isNull();
        assertThat(facts.twelveStages().hour()).isNull();
        assertThat(facts.luck().decades()).isEmpty();
        assertThat(facts.meta().birthTimeKnown()).isFalse();
        assertThat(facts.fiveElements().counts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(6);
    }

    @Test
    void appliesMinuteExactKstIpchunBoundary() {
        assertThat(calculate("2024-02-04", "17:26", CalendarType.SOLAR, false, Gender.MALE).pillars().year().display()).isEqualTo("계묘");
        assertThat(calculate("2024-02-04", "17:28", CalendarType.SOLAR, false, Gender.MALE).pillars().year().display()).isEqualTo("갑진");
    }

    @Test
    void rejectsUnknownTimeOnSolarTermBoundaryInsteadOfGuessingPillars() {
        BirthInput input = new BirthInput(LocalDate.of(2024, 2, 4), null, true,
                CalendarType.SOLAR, false, Gender.FEMALE);

        assertThatThrownBy(() -> engine.calculate(input, CalculationPolicy.CURRENT))
                .isInstanceOf(DeterministicSajuEngine.UnsupportedUnknownBirthTimeException.class);
    }

    private CalculationFacts calculate(String date, String time, CalendarType type, boolean leap, Gender gender) {
        return engine.calculate(new BirthInput(LocalDate.parse(date), LocalTime.parse(time), false, type, leap, gender), CalculationPolicy.CURRENT);
    }

    private static Stream<Arguments> goldenCases() {
        return Stream.of(
                Arguments.of("1901-06-16", "02:47", "신축", "갑오", "을축", "정축"),
                Arguments.of("1904-09-19", "05:08", "갑진", "계유", "병진", "신묘"),
                Arguments.of("1907-12-22", "08:29", "정미", "임자", "을사", "경진"),
                Arguments.of("1910-03-02", "11:50", "경술", "무인", "병인", "갑오"),
                Arguments.of("1913-06-05", "14:11", "계축", "정사", "정사", "정미"),
                Arguments.of("1916-09-08", "17:32", "병진", "정유", "무신", "신유"),
                Arguments.of("1919-12-11", "20:53", "기미", "병자", "정유", "경술"),
                Arguments.of("1922-03-14", "03:14", "임술", "계묘", "신사", "경인"),
                Arguments.of("1925-06-17", "06:35", "을축", "임오", "임신", "계묘"),
                Arguments.of("1928-09-20", "09:56", "무진", "신유", "계해", "정사"),
                Arguments.of("1931-12-23", "12:17", "신미", "경자", "임자", "병오"),
                Arguments.of("1934-03-03", "15:38", "갑술", "병인", "계유", "경신"),
                Arguments.of("1937-06-06", "18:59", "정축", "병오", "갑자", "계유"),
                Arguments.of("1940-09-09", "01:20", "경진", "을유", "을묘", "정축"),
                Arguments.of("1943-12-12", "04:41", "계미", "갑자", "갑진", "병인"),
                Arguments.of("1946-03-15", "07:02", "병술", "신묘", "무자", "병진"),
                Arguments.of("1949-06-18", "10:23", "기축", "경오", "기묘", "기사"),
                Arguments.of("1952-09-21", "13:44", "임진", "기유", "경오", "계미"),
                Arguments.of("1955-12-01", "16:05", "을미", "정해", "병신", "병신"),
                Arguments.of("1958-03-04", "19:26", "무술", "갑인", "경진", "병술"),
                Arguments.of("1961-06-07", "02:47", "신축", "갑오", "신미", "기축"),
                Arguments.of("1964-09-10", "05:08", "갑진", "계유", "임술", "계묘"),
                Arguments.of("1967-12-13", "08:29", "정미", "임자", "신해", "임진"),
                Arguments.of("1970-03-16", "11:50", "경술", "기묘", "을미", "임오"),
                Arguments.of("1973-06-19", "14:11", "계축", "무오", "병술", "을미"),
                Arguments.of("1976-09-22", "17:32", "병진", "정유", "정축", "기유"),
                Arguments.of("1979-12-02", "20:53", "기미", "을해", "계묘", "임술"),
                Arguments.of("1982-03-05", "03:14", "임술", "임인", "정해", "임인"),
                Arguments.of("1985-06-08", "06:35", "을축", "임오", "무인", "을묘"),
                Arguments.of("1988-09-11", "09:56", "무진", "신유", "기사", "기사"),
                Arguments.of("1991-12-14", "12:17", "신미", "경자", "무오", "무오"),
                Arguments.of("1994-03-17", "15:38", "갑술", "정묘", "임인", "무신"),
                Arguments.of("1997-06-20", "18:59", "정축", "병오", "계사", "신유"),
                Arguments.of("2000-09-23", "01:20", "경진", "을유", "갑신", "을축"),
                Arguments.of("2003-12-03", "04:41", "계미", "계해", "경술", "무인"),
                Arguments.of("2006-03-06", "07:02", "병술", "신묘", "갑오", "무진"),
                Arguments.of("2009-06-09", "10:23", "기축", "경오", "을유", "신사"),
                Arguments.of("2012-09-12", "13:44", "임진", "기유", "병자", "을미"),
                Arguments.of("2015-12-15", "16:05", "을미", "무자", "을축", "갑신"),
                Arguments.of("2018-03-18", "19:26", "무술", "을묘", "기유", "갑술"),
                Arguments.of("2021-06-21", "02:47", "신축", "갑오", "경자", "정축"),
                Arguments.of("2024-09-01", "05:08", "갑진", "임신", "무진", "을묘"),
                Arguments.of("2027-12-04", "08:29", "정미", "신해", "정사", "갑진"),
                Arguments.of("2030-03-07", "11:50", "경술", "기묘", "신축", "갑오"),
                Arguments.of("2033-06-10", "14:11", "계축", "무오", "임진", "정미"),
                Arguments.of("2036-09-13", "17:32", "병진", "정유", "계미", "신유"),
                Arguments.of("2039-12-16", "20:53", "기미", "병자", "임신", "경술"),
                Arguments.of("2042-03-19", "03:14", "임술", "계묘", "병진", "경인"),
                Arguments.of("2045-06-22", "06:35", "을축", "임오", "정미", "계묘"),
                Arguments.of("2048-09-02", "09:56", "무진", "경신", "을해", "신사"));
    }
}
