package pl.slogerski.waypointsplus.fabric;

record WaypointSettingsSnapshot(
        boolean enabled,
        boolean background,
        boolean showCoordinates,
        boolean showDistance,
        boolean laserEnabled,
        boolean menuBackground,
        String language,
        float scale,
        int markerArgb,
        int backgroundArgb,
        int markerTintPercent
) {
    static WaypointSettingsSnapshot capture(WaypointSettings settings) {
        return new WaypointSettingsSnapshot(
                settings.enabled,
                settings.background,
                settings.showCoordinates,
                settings.showDistance,
                settings.laserEnabled,
                settings.menuBackground,
                settings.language,
                settings.scale,
                settings.markerArgb,
                settings.backgroundArgb,
                settings.markerTintPercent
        );
    }

    void restore(WaypointSettings settings) {
        settings.enabled = enabled;
        settings.background = background;
        settings.showCoordinates = showCoordinates;
        settings.showDistance = showDistance;
        settings.laserEnabled = laserEnabled;
        settings.menuBackground = menuBackground;
        settings.language = language;
        settings.scale = scale;
        settings.markerArgb = markerArgb;
        settings.backgroundArgb = backgroundArgb;
        settings.markerTintPercent = markerTintPercent;
    }
}
