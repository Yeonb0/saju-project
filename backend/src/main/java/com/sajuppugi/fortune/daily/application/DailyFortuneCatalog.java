package com.sajuppugi.fortune.daily.application;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** PD-approved daily-fortune display catalog (2026-10-15). */
final class DailyFortuneCatalog {
    private static final String[] CONSONANTS = {"ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};
    static final List<String> INITIALS = initials();
    static final List<Guardian> GUARDIANS = guardians();
    static final List<Entry> MISSIONS = entries("m", 2);
    static final List<Entry> SONGS = entries("s", 3);

    private DailyFortuneCatalog() {}
    private static List<String> initials() {
        List<String> values = new ArrayList<>(196);
        for (String first : CONSONANTS) for (String second : CONSONANTS) values.add(first + second);
        return List.copyOf(values);
    }
    private static List<Guardian> guardians() {
        List<Guardian> values = new ArrayList<>();
        for (String[] row : rows()) if (row.length == 3 && row[1].matches("[a-z]+_[1-5]"))
            values.add(new Guardian(row[1], row[1].replaceFirst("_[1-5]$", ""), row[0], row[2]));
        if (values.size() != 60) throw new IllegalStateException("Daily guardian catalog is incomplete");
        return List.copyOf(values);
    }
    private static List<Entry> entries(String prefix, int columns) {
        List<Entry> values = new ArrayList<>();
        for (String[] row : rows()) if (row.length == columns && row[0].matches(prefix + "\\d{2}"))
            values.add(new Entry(row[0], row[1], columns == 3 ? row[2] : null));
        int expected = prefix.equals("m") ? 60 : 50;
        if (values.size() != expected) throw new IllegalStateException("Daily catalog is incomplete: " + prefix);
        return List.copyOf(values);
    }
    private static List<String[]> rows() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                DailyFortuneCatalog.class.getResourceAsStream("/daily-fortune/daily-2026.10-v3.md"), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> line.startsWith("| ") && !line.startsWith("| ---"))
                    .map(line -> java.util.Arrays.stream(line.substring(2, line.length() - 2).split(" \\| ", -1))
                            .map(String::trim).map(value -> value.replace("`", "")).toArray(String[]::new)).toList();
        } catch (Exception failure) { throw new IllegalStateException("Daily catalog cannot load", failure); }
    }
    record Guardian(String key, String animal, String name, String line) {}
    record Entry(String key, String value, String detail) {}
}
