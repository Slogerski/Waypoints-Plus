package pl.slogerski.waypointsplus.fabric;

final class WaypointVisibility {
    private static volatile boolean hidden;

    private WaypointVisibility() { }

    static boolean hidden() {
        return hidden;
    }

    static void toggle() {
        hidden = !hidden;
    }

    static void reset() {
        hidden = false;
    }
}
