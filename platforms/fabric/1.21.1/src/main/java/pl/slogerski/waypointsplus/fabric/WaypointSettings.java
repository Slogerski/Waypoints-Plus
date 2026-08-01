package pl.slogerski.waypointsplus.fabric;

final class WaypointSettings {
    boolean enabled = true;
    boolean background = true;
    boolean showCoordinates = false;
    boolean showDistance = true;
    boolean laserEnabled = false;
    String language = "en";
    float scale = 1.0f;
    int markerArgb = 0xCCDBDBD3;
    int backgroundArgb = 0xE01C1C1C;

    void sanitize() {
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        if (!"pl".equals(language)) language = "en";
    }
}
