package pl.slogerski.waypointsplus.fabric;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QuickWaypointClipboard {
    private static final int MAX_CLIPBOARD_LENGTH = 65;
    private static final int MAX_NAME_LENGTH = 64;
    private static final String SEPARATOR = "(?:\\s*[,;]\\s*|\\s+)";
    private static final Pattern WAYPOINT = Pattern.compile(
            "^\\s*(-?\\d+)" + SEPARATOR + "(-?\\d+)" + SEPARATOR + "(-?\\d+)(?:\\s+(.+?))?\\s*$");

    private QuickWaypointClipboard() { }

    static WaypointTransfer.Entry parse(String clipboard, String dimension, String colorArgb, boolean polish) {
        if (clipboard == null || clipboard.isEmpty() || clipboard.length() > MAX_CLIPBOARD_LENGTH
                || clipboard.chars().anyMatch(value -> Character.isISOControl(value))) return null;
        Matcher matcher = WAYPOINT.matcher(clipboard);
        if (!matcher.matches()) return null;
        String name = matcher.group(4) == null
                ? (polish ? "Szybki waypoint" : "Quick Waypoint")
                : matcher.group(4).trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return null;
        try {
            return new WaypointTransfer.Entry(name,
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), dimension, normalizeColor(colorArgb));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String currentPosition(int x, int y, int z, boolean polish) {
        return x + " " + y + " " + z + " " + (polish ? "Koordynaty" : "Coords");
    }

    private static String normalizeColor(String colorArgb) {
        String value = colorArgb == null ? "" : colorArgb.replace("#", "").toUpperCase(java.util.Locale.ROOT);
        return value.matches("[0-9A-F]{8}") ? value : "CCDBDBD3";
    }
}
