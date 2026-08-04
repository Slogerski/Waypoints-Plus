package pl.slogerski.waypointsplus.fabric;

final class WaypointSettings {
    boolean enabled = true;
    boolean background = true;
    boolean showCoordinates = false;
    boolean showDistance = true;
    boolean laserEnabled = false;
    boolean menuBackground = true;
    String language = "en";
    float scale = 1.0f;
    int markerArgb = 0xCCDBDBD3;
    int backgroundArgb = 0xE01C1C1C;
    int markerTintPercent = 25;

    void resetDefaults() {
        enabled = true;
        background = true;
        showCoordinates = false;
        showDistance = true;
        laserEnabled = false;
        menuBackground = true;
        language = "en";
        scale = 1.0f;
        markerArgb = 0xCCDBDBD3;
        backgroundArgb = 0xE01C1C1C;
        markerTintPercent = 25;
    }

    void sanitize() {
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        markerTintPercent = Math.max(0, Math.min(100, markerTintPercent));
        if (!"pl".equals(language)) language = "en";
    }
}
