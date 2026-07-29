package pl.slogerski.waypointsplus.fabric;

final class WaypointSettings {
    boolean enabled = true;
    boolean background = true;
    boolean showCoordinates = true;
    boolean showDistance = true;
    boolean laserEnabled = true;
    String language = "en";
    float scale = 1.0f;
    int markerArgb = 0xEE00F5FF;
    int backgroundArgb = 0xB0101420;

    void sanitize() {
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        if (!"pl".equals(language)) language = "en";
    }
}
