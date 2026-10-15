package com.sajuppugi.fortune.reading.application;

import java.util.regex.Pattern;

/** Keeps a paid-reading concern useful for personalization without retaining common direct identifiers. */
public final class PersonalizedConcern {
    public static final int MAX_LENGTH = 100;
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:01[0-9][- ]?)?\\d{3,4}[- ]?\\d{4}(?!\\d)");
    private static final Pattern KOREAN_ID = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");

    private PersonalizedConcern() {}

    public static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").strip();
        if (normalized.isEmpty()) return null;
        if (normalized.codePointCount(0, normalized.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("Personalized concern exceeds 100 characters");
        }
        return URL.matcher(KOREAN_ID.matcher(PHONE.matcher(EMAIL.matcher(normalized)
                .replaceAll("[이메일]"))
                .replaceAll("[전화번호]"))
                .replaceAll("[식별번호]"))
                .replaceAll("[링크]");
    }
}
