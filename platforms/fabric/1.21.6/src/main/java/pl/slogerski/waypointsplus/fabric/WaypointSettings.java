package pl.slogerski.waypointsplus.fabric;

final class WaypointSettings {
    int schemaVersion = 1;
    boolean enabled = true;
    boolean background = true;
    boolean showCoordinates = false;
    boolean showDistance = true;
    boolean laserEnabled = false;
    boolean menuBackground = true;
    boolean crossDimensionWaypoints = true;
    String language = "en";
    float scale = 1.15f;
    int markerArgb = 0xCCDBDBD3;
    int backgroundArgb = 0xE01C1C1C;
    int markerTintPercent = 25;

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
        backgroundArgb = 0xE01C1C1C;
        markerTintPercent = 25;
    }

    void sanitize() {
        if (schemaVersion <= 0) schemaVersion = 1;
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        markerTintPercent = Math.max(0, Math.min(100, markerTintPercent));
        if (!"pl".equals(language)) language = "en";
    }
}
