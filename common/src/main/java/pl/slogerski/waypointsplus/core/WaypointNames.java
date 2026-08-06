package pl.slogerski.waypointsplus.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class WaypointNames {
    private static final DateTimeFormatter DEATH_TIME_EN =
            DateTimeFormatter.ofPattern("d MMM yy HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter DEATH_TIME_PL =
            DateTimeFormatter.ofPattern("d MMM yy HH:mm:ss", Locale.forLanguageTag("pl"));

    private WaypointNames() {
    }

    public static String death(String language) {
        DateTimeFormatter formatter = "pl".equals(language) ? DEATH_TIME_PL : DEATH_TIME_EN;
        return LocalDateTime.now().format(formatter);
    }
}
