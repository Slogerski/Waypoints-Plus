package pl.slogerski.waypointsplus.fabric;

final class WaypointDistanceTextCheck {
    private WaypointDistanceTextCheck() {
    }

    static void run() {
        WaypointDistanceText text = new WaypointDistanceText();
        expect(text, 0, false, false, "0 m");
        expect(text, 999.4, false, false, "999 m");
        require(!text.update(999.49, false, false), "Reuse cached meters");
        expect(text, 999.6, false, false, "1000 m");
        expect(text, 1000, false, false, "1.0 km");
        expect(text, 10000, false, false, "10.0 km");
        require(text.update(10000, true, false), "Changing units must refresh the label");
        require(text.text().equals("10000 m"), "Meters-only mode at 10 km");
        require(!text.update(10000.2, true, false), "Reuse cached rounded meters");
        expect(text, 10000.6, true, false, "10001 m");
        expect(text, 1234, false, false, "1.2 km");
        require(!text.update(1249, false, false), "Reuse cached rounded kilometers");
        expect(text, 1234, false, true, "1,2 km");
        expect(text, 1234, true, true, "1234 m");
        expect(text, 30000000, true, false, "30000000 m");
        expect(text, 100, false, false, "100 m");
        require(!text.update(100, true, false), "Unit mode must not rebuild unchanged short distances");
    }

    private static void expect(WaypointDistanceText text, double distance, boolean metersOnly, boolean polish, String expected) {
        text.update(distance, metersOnly, polish);
        require(text.text().equals(expected), "Expected " + expected + ", got " + text.text());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
