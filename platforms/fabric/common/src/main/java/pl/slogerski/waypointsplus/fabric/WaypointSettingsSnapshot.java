package pl.slogerski.waypointsplus.fabric;

record WaypointSettingsSnapshot(
        boolean enabled,
        boolean background,
        boolean showCoordinates,
        boolean showDistance,
        boolean laserEnabled,
        String language,
        float scale,
        int markerArgb,
        int backgroundArgb
) {
    static WaypointSettingsSnapshot capture(WaypointSettings settings) {
        return new WaypointSettingsSnapshot(
                settings.enabled,
                settings.background,
                settings.showCoordinates,
                settings.showDistance,
                settings.laserEnabled,
                settings.language,
                settings.scale,
                settings.markerArgb,
                settings.backgroundArgb
        );
    }

    void restore(WaypointSettings settings) {
        settings.enabled = enabled;
        settings.background = background;
        settings.showCoordinates = showCoordinates;
        settings.showDistance = showDistance;
        settings.laserEnabled = laserEnabled;
        settings.language = language;
        settings.scale = scale;
        settings.markerArgb = markerArgb;
        settings.backgroundArgb = backgroundArgb;
    }
}
