package pl.slogerski.waypointsplus.fabric;

final class WaypointCountText {
    private WaypointCountText() { }

    static String format(int count) {
        return UiText.get(
                count + (count == 1 ? " waypoint" : " waypoints"),
                count + " " + polishForm(count)
        );
    }

    private static String polishForm(int count) {
        int absolute = Math.abs(count);
        if (absolute == 1) return "waypoint";
        int lastTwo = absolute % 100;
        int last = absolute % 10;
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) return "waypointy";
        return "waypointów";
    }
}
