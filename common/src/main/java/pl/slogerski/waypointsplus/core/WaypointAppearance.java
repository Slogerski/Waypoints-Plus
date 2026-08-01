package pl.slogerski.waypointsplus.core;

public final class WaypointAppearance {
    private static final int DEATH_BACKGROUND_ARGB = 0xE01C1C1C;
    private static final String DEATH_PROFILE = "Death Waypoints";

    private WaypointAppearance() {
    }

    public static int backgroundArgb(Waypoint waypoint, int defaultArgb) {
        int baseArgb = DEATH_PROFILE.equals(waypoint.profile()) ? DEATH_BACKGROUND_ARGB : defaultArgb;
        Integer markerArgb = parseArgb(waypoint.colorArgb());
        return markerArgb == null ? baseArgb : blendMarkerIntoBackground(baseArgb, markerArgb);
    }

    private static int blendMarkerIntoBackground(int backgroundArgb, int markerArgb) {
        int red = blendChannel(backgroundArgb >>> 16, markerArgb >>> 16);
        int green = blendChannel(backgroundArgb >>> 8, markerArgb >>> 8);
        int blue = blendChannel(backgroundArgb, markerArgb);
        return (backgroundArgb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int blendChannel(int background, int marker) {
        return ((background & 0xFF) * 3 + (marker & 0xFF) + 2) / 4;
    }

    private static Integer parseArgb(String value) {
        if (value == null) return null;
        String hex = value.replace("#", "");
        if (hex.length() != 6 && hex.length() != 8) return null;
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
