package pl.slogerski.waypointsplus.fabric;

record WaypointSettingsSnapshot(
        boolean enabled,
        boolean background,
        boolean showCoordinates,
        boolean showDistance,
        boolean laserEnabled,
        boolean menuBackground,
        boolean crossDimensionWaypoints,
        String language,
        float scale,
        int markerArgb,
        int textArgb,
        boolean matchTextToBorder,
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
                settings.crossDimensionWaypoints,
                settings.language,
                settings.scale,
                settings.markerArgb,
                settings.textArgb,
                settings.matchTextToBorder,
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
        settings.crossDimensionWaypoints = crossDimensionWaypoints;
        settings.language = language;
        settings.scale = scale;
        settings.markerArgb = markerArgb;
        settings.textArgb = textArgb;
        settings.matchTextToBorder = matchTextToBorder;
        settings.backgroundArgb = backgroundArgb;
        settings.markerTintPercent = markerTintPercent;
    }
}
