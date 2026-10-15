package com.sajuppugi.fortune.talisman.domain;

import java.util.Objects;

/** Immutable visual choices made when a talisman is first created. */
public record TalismanComposition(
        String borderKey,
        String paperKey,
        String animalKey,
        String itemKey,
        Phrase phrase,
        String sealKey) {
    public TalismanComposition {
        requireKey(borderKey, "borderKey");
        requireKey(paperKey, "paperKey");
        requireKey(animalKey, "animalKey");
        requireKey(itemKey, "itemKey");
        Objects.requireNonNull(phrase, "phrase");
        requireKey(sealKey, "sealKey");
    }

    private static void requireKey(String value, String name) {
        if (value == null || !value.matches("[a-z0-9_]{1,100}")) {
            throw new IllegalArgumentException(name + " must be a catalog key");
        }
    }

    public record Phrase(String text, String hanja, String meaning) {
        public Phrase {
            requireText(text, "text", 20);
            requireText(hanja, "hanja", 40);
            requireText(meaning, "meaning", 200);
        }

        private static void requireText(String value, String name, int maxLength) {
            if (value == null || value.isBlank() || value.length() > maxLength) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }
}
