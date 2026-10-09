package com.sajuppugi.fortune.calculation.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record BirthInput(
        LocalDate birthDate,
        LocalTime birthTime,
        boolean birthTimeUnknown,
        CalendarType calendarType,
        boolean leapMonth,
        Gender gender) {

    public enum CalendarType { SOLAR, LUNAR }

    public enum Gender { MALE, FEMALE, UNSPECIFIED }
}
