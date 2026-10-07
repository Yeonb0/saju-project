package com.sajuppugi.fortune.calculation.application;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class BirthInputNormalizer {
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private final Clock clock;

    public BirthInputNormalizer(Clock clock) {
        this.clock = clock;
    }

    public BirthInput normalize(
            String birthDate,
            String birthTime,
            boolean birthTimeUnknown,
            CalendarType calendarType,
            boolean leapMonth,
            Gender gender) {
        try {
            LocalDate date = LocalDate.parse(birthDate.strip());
            LocalDate today = LocalDate.now(clock);
            if (date.isBefore(MIN_DATE) || date.isAfter(today)) {
                throw new IllegalArgumentException("birthDate must be between 1900-01-01 and today");
            }
            if (calendarType == CalendarType.SOLAR && leapMonth) {
                throw new IllegalArgumentException("leapMonth is only valid for a lunar date");
            }
            if (birthTimeUnknown && birthTime != null && !birthTime.isBlank()) {
                throw new IllegalArgumentException("birthTime must be absent when birthTimeUnknown is true");
            }
            if (!birthTimeUnknown && (birthTime == null || birthTime.isBlank())) {
                throw new IllegalArgumentException("birthTime is required when birthTimeUnknown is false");
            }
            LocalTime time = birthTimeUnknown ? null : LocalTime.parse(birthTime.strip());
            return new BirthInput(date, time, birthTimeUnknown, calendarType, leapMonth, gender);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("birthDate or birthTime has an invalid format", exception);
        }
    }
}
