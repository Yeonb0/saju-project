package com.sajuppugi.fortune.reading.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class SuneungEventPolicy {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalDate EXAM_DATE = LocalDate.of(2026, 11, 19);
    public static final LocalDateTime SALE_END = LocalDateTime.of(EXAM_DATE.minusDays(1), LocalTime.of(23, 59, 59));
    public static final String PRODUCT_CODE = "SUNEUNG_READING_WITH_TALISMAN";
    public static final String GENERATION_VERSION = "liner-suneung-v2";
    public static final String CONTENT_VERSION = "suneung-2026-v1";

    private SuneungEventPolicy() {}
}
