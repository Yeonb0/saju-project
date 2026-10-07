package com.sajuppugi.fortune.calculation.domain;

import java.time.ZoneId;

public record CalculationPolicy(
        String version,
        ZoneId zoneId,
        DayBoundary dayBoundary,
        boolean trueSolarTime,
        boolean historicalDst) {

    public static final CalculationPolicy CURRENT = new CalculationPolicy(
            "manse-2026.10-v1",
            ZoneId.of("Asia/Seoul"),
            DayBoundary.MIDNIGHT,
            false,
            false);

    public enum DayBoundary {
        MIDNIGHT,
        ZI_HOUR
    }
}
