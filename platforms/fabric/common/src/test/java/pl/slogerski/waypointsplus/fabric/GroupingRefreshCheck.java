package pl.slogerski.waypointsplus.fabric;

public final class GroupingRefreshCheck {
    private GroupingRefreshCheck() {
    }

    public static void main(String[] args) {
        SmartWaypointRefresh refresh = new SmartWaypointRefresh();
        require(refresh.due(true, 0), "Initial refresh");
        require(!refresh.due(true, 999_999_999L), "No early periodic refresh");
        refresh.membershipChanged();
        require(refresh.due(true, 1_000_000_000L), "Immediate membership refresh");
        require(!refresh.due(true, 1_000_000_001L), "Membership event consumed");
        refresh.membershipChanged();
        require(refresh.due(true, 9_000_000_000L), "Further membership refresh");
        require(refresh.due(true, 10_000_000_000L), "Periodic deadline is not postponed");
        require(!refresh.due(true, 19_999_999_999L), "Next periodic deadline");
        refresh.reset();
        require(refresh.due(true, 20_000_000_000L), "Explicit reset");
        require(!refresh.due(false, 21_000_000_000L), "Disabled grouping");
        require(refresh.due(true, 22_000_000_000L), "Re-enabled grouping");
        require(refresh.due(true, 32_000_000_000L), "Re-enabled periodic deadline");
    }

    private static void require(boolean result, String scenario) {
        if (!result) throw new AssertionError(scenario);
    }
}
