package com.sajuppugi.fortune.talisman.domain;

import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class TalismanCatalog {
    private static final Map<FortuneType, List<TalismanComposition.Phrase>> PHRASES = phrases();
    private TalismanCatalog() {}
    static String fortuneKey(FortuneType type) {
        return switch (type) { case SUNEUNG -> "csat"; case OVERALL -> "general"; case LOVE -> "love";
            case WEALTH -> "wealth"; case COMPATIBILITY -> "match"; case SINSAL -> "sinsal"; };
    }
    static TalismanComposition.Phrase phrase(FortuneType type, int index) {
        List<TalismanComposition.Phrase> values = PHRASES.get(type);
        return values.get(index % values.size());
    }
    private static Map<FortuneType, List<TalismanComposition.Phrase>> phrases() {
        Map<FortuneType, List<TalismanComposition.Phrase>> values = new EnumMap<>(FortuneType.class);
        FortuneType current = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(TalismanCatalog.class
                .getResourceAsStream("/talisman/talisman-composition-2026.10.md"), StandardCharsets.UTF_8))) {
            for (String line : reader.lines().toList()) {
                if (line.startsWith("### ")) current = type(line);
                if (current != null && line.startsWith("| ") && !line.startsWith("| ---")) {
                    String[] row = line.substring(2, line.length() - 2).split(" \\| ");
                    if (row.length == 3 && !row[0].equals("멘트")) values.computeIfAbsent(current, ignored -> new ArrayList<>())
                            .add(new TalismanComposition.Phrase(row[0], row[1], row[2]));
                }
            }
        } catch (Exception e) { throw new IllegalStateException("Talisman catalog cannot load", e); }
        if (values.size() != FortuneType.values().length || values.values().stream().anyMatch(List::isEmpty))
            throw new IllegalStateException("Talisman phrase catalog is incomplete");
        return Map.copyOf(values);
    }
    private static FortuneType type(String header) {
        if (header.contains("`csat`")) return FortuneType.SUNEUNG;
        if (header.contains("`general`")) return FortuneType.OVERALL;
        if (header.contains("`love`")) return FortuneType.LOVE;
        if (header.contains("`wealth`")) return FortuneType.WEALTH;
        if (header.contains("`match`")) return FortuneType.COMPATIBILITY;
        return header.contains("`sinsal`") ? FortuneType.SINSAL : null;
    }
}
