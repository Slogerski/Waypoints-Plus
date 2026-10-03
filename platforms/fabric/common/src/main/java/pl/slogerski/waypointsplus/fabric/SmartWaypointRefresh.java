package pl.slogerski.waypointsplus.fabric;

final class SmartWaypointRefresh {
    private static final long INTERVAL = 10_000_000_000L;
    private long next;
    private boolean pending = true;
    private boolean membershipChanged;

    boolean due(boolean enabled, long now) {
        if (!enabled) { pending = true; membershipChanged = false; return false; }
        if (!pending && now - next < 0) {
            if (!membershipChanged) return false;
            membershipChanged = false;
            return true;
        }
        pending = false;
        membershipChanged = false;
        next = now + INTERVAL;
        return true;
    }

    void reset() { pending = true; }
    void membershipChanged() { membershipChanged = true; }
}
