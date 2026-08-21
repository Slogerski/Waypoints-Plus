package pl.slogerski.waypointsplus.fabric.remote;

import java.util.Objects;

public record LocalizedText(String english, String polish) {
    public LocalizedText {
        english = require(english, "english");
        polish = require(polish, "polish");
    }

    public String forLanguage(String language) {
        return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("pl")
                ? polish : english;
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 240) {
            throw new IllegalArgumentException(name);
        }
        return value;
    }
}
