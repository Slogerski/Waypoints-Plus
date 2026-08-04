package pl.slogerski.waypointsplus.core;

public final class WaypointAppearance {
    private static final int DEATH_BACKGROUND_ARGB = 0xE01C1C1C;
    private static final String DEATH_PROFILE = "Death Waypoints";

    private WaypointAppearance() {
    }

    public static int backgroundArgb(Waypoint waypoint, int defaultArgb, int markerArgb, int markerTintPercent) {
        int baseArgb = DEATH_PROFILE.equals(waypoint.profile()) ? DEATH_BACKGROUND_ARGB : defaultArgb;
        if (markerTintPercent <= 0) return baseArgb;
        return blendMarkerIntoBackground(baseArgb, markerArgb, Math.min(markerTintPercent, 100));
    }

    private static int blendMarkerIntoBackground(int backgroundArgb, int markerArgb, int percent) {
        int red = blendChannel(backgroundArgb >>> 16, markerArgb >>> 16, percent);
        int green = blendChannel(backgroundArgb >>> 8, markerArgb >>> 8, percent);
        int blue = blendChannel(backgroundArgb, markerArgb, percent);
        return (backgroundArgb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int blendChannel(int background, int marker, int percent) {
        return ((background & 0xFF) * (100 - percent) + (marker & 0xFF) * percent + 50) / 100;
    }
}
