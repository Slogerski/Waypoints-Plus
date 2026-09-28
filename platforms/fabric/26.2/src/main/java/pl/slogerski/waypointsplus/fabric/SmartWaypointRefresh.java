package pl.slogerski.waypointsplus.fabric;

final class SmartWaypointRefresh {
    private static final long INTERVAL = 30_000_000_000L;
    private long next;
    private boolean pending = true;

    boolean due(boolean enabled, long now) {
        if (!enabled) { pending = true; return false; }
        if (!pending && now - next < 0) return false;
        pending = false;
        next = now + INTERVAL;
        return true;
    }

    void reset() { pending = true; }
}
