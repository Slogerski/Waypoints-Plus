package pl.slogerski.waypointsplus.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WaypointSettings {
    int schemaVersion = 1;
    boolean enabled = true;
    boolean background = true;
    boolean showCoordinates = false;
    boolean showDistance = true;
    boolean laserEnabled = false;
    boolean menuBackground = true;
    boolean crossDimensionWaypoints = true;
    boolean topDonateExpanded = true;
    String language = "en";
    float scale = 1.15f;
    int markerArgb = 0xCCDBDBD3;
    int textArgb = 0xFFFFFFFF;
    boolean matchTextToBorder = true;
    int backgroundArgb = 0xE01C1C1C;
    int markerTintPercent = 25;
    List<String> waypointColorHistory = new ArrayList<>();

    void resetDefaults() {
        schemaVersion = 1;
        enabled = true;
        background = true;
        showCoordinates = false;
        showDistance = true;
        laserEnabled = false;
        menuBackground = true;
        crossDimensionWaypoints = true;
        language = "en";
        scale = 1.15f;
        markerArgb = 0xCCDBDBD3;
        textArgb = 0xFFFFFFFF;
        matchTextToBorder = true;
        backgroundArgb = 0xE01C1C1C;
        markerTintPercent = 25;
    }

    void sanitize() {
        if (schemaVersion <= 0) schemaVersion = 1;
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        markerTintPercent = Math.max(0, Math.min(100, markerTintPercent));
        if (!"pl".equals(language)) language = "en";
        if (waypointColorHistory == null) waypointColorHistory = new ArrayList<>();
        List<String> sanitizedColors = new ArrayList<>();
        for (String color : waypointColorHistory) {
            if (color == null) continue;
            String normalized = color.replace("#", "").toUpperCase(Locale.ROOT);
            if (!normalized.matches("[0-9A-F]{8}") || sanitizedColors.contains(normalized)) continue;
            sanitizedColors.add(normalized);
            if (sanitizedColors.size() == 8) break;
        }
        waypointColorHistory = sanitizedColors;
    }

    void rememberWaypointColor(String color) {
        String normalized = color.replace("#", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-F]{8}")) return;
        waypointColorHistory.removeIf(normalized::equalsIgnoreCase);
        waypointColorHistory.add(0, normalized);
        if (waypointColorHistory.size() > 8) waypointColorHistory = new ArrayList<>(waypointColorHistory.subList(0, 8));
    }
}
