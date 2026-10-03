package pl.slogerski.waypointsplus.core;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointGroupingTest {
    @Test void combinesDenseIconsAndPlainLabels() {
        WaypointGrouping groups = begin(100);
        for (int i = 0; i < 100; i++) add(groups, 100, 100, 20, 20, 300 + i, i == 12 || i == 20);
        groups.finish();
        for (int i = 0; i < 100; i++) {
            assertTrue(groups.collapsed(i));
            assertEquals(100, groups.groupSize(i));
            assertEquals(0, groups.nearestIndex(i));
            assertEquals(12, groups.representativeIndex(i));
        }
    }

    @Test void acceptsFullLabelsWiderThan96Pixels() {
        WaypointGrouping groups = begin(3);
        for (int i = 0; i < 3; i++) assertEquals(i, add(groups, 100 + i * 20, 100, 200, 30, 300 + i, true));
        groups.finish();
        assertTrue(groups.collapsed(0));
        assertEquals(3, groups.groupSize(0));
    }

    @Test void limitsThinChainsToFourEvenWhenNearestIsInTheMiddle() {
        for (int nearest = 0; nearest < 8; nearest++) {
            WaypointGrouping groups = begin(8);
            for (int i = 0; i < 8; i++) add(groups, 100 + i * 18, 100, 20, 20, i == nearest ? 300 : 310 + i, true);
            groups.finish();
            for (int i = 0; i < 8; i++) assertTrue(groups.groupSize(i) <= 4);
            for (int i = 0; i < 8; i++) {
                if (groups.collapsed(i) && groups.representative(i)) groups.includeStackBounds(i, 50, 80, 350, 160);
            }
            groups.finish();
            for (int i = 0; i < 8; i++) assertTrue(groups.groupSize(i) <= 4);
        }
    }

    @Test void keepsGroupCentersWithin192PixelArea() {
        WaypointGrouping groups = begin(4);
        add(groups, 0, 100, 200, 20, 300, true);
        add(groups, 80, 100, 200, 20, 301, true);
        add(groups, 90, 100, 200, 20, 302, true);
        add(groups, 200, 100, 200, 20, 303, true);
        groups.finish();
        assertEquals(3, groups.groupSize(0));
        assertFalse(groups.collapsed(3));
    }

    @Test void excludesOnlyCloseMembersAndRegroupsAfterMovingAway() {
        WaypointGrouping groups = begin(4);
        for (int i = 0; i < 4; i++) add(groups, 100, 100, 20, 20, 300 + i, true);
        groups.finish();
        assertEquals(4, groups.groupSize(0));
        groups.begin(800, 600, 4, 250);
        assertEquals(-1, add(groups, 100, 100, 20, 20, 250, true));
        for (int i = 0; i < 3; i++) add(groups, 100, 100, 20, 20, 300 + i, true);
        groups.finish();
        assertTrue(groups.collapsed(0));
        assertEquals(3, groups.groupSize(0));
        groups.begin(800, 600, 4, 250);
        for (int i = 0; i < 4; i++) add(groups, 100, 100, 20, 20, 300 + i, true);
        groups.finish();
        assertEquals(4, groups.groupSize(0));
    }

    @Test void picksNearestIconSeparatelyFromNearestMember() {
        WaypointGrouping groups = begin(4);
        add(groups, 100, 100, 20, 20, 500, true);
        add(groups, 100, 100, 20, 20, 300, false);
        add(groups, 100, 100, 20, 20, 400, true);
        add(groups, 100, 100, 20, 20, 600, true);
        groups.finish();
        assertEquals(1, groups.nearestIndex(0));
        assertEquals(2, groups.representativeIndex(0));
    }

    @Test void mergesDenseStacksWithinTheSameArea() {
        WaypointGrouping groups = begin(6);
        for (int i = 0; i < 3; i++) add(groups, 100, 100, 10, 10, 300 + i, true);
        for (int i = 0; i < 3; i++) add(groups, 150, 100, 10, 10, 310 + i, true);
        groups.finish();
        assertEquals(3, groups.groupSize(0));
        assertEquals(3, groups.groupSize(3));
        groups.includeStackBounds(0, 95, 95, 165, 120);
        groups.finish();
        assertEquals(6, groups.groupSize(0));
    }

    @Test void handlesVeryLargeBoundsWithoutDroppingThem() {
        WaypointGrouping groups = begin(3);
        for (int i = 0; i < 3; i++) assertEquals(i, add(groups, 0, 0, 800, 600, 300 + i, true));
        groups.finish();
        assertEquals(3, groups.groupSize(0));
    }

    @Test void requiresThreeMembersAndAnIcon() {
        WaypointGrouping groups = begin(3);
        for (int i = 0; i < 3; i++) add(groups, 100, 100, 20, 20, 300 + i, false);
        groups.finish();
        assertFalse(groups.collapsed(0));
        groups.begin(800, 600, 3, 250);
        for (int i = 0; i < 2; i++) add(groups, 100, 100, 20, 20, 300 + i, true);
        groups.finish();
        assertFalse(groups.collapsed(0));
    }

    @Test void rejectsInvalidAndOffscreenBoundsAndCanRestartAfterClear() {
        WaypointGrouping groups = begin(3);
        assertEquals(-1, add(groups, Float.NaN, 0, 20, 20, 300, true));
        assertEquals(-1, add(groups, 900, 0, 20, 20, 300, true));
        assertEquals(-1, add(groups, 10, 10, 0, 20, 300, true));
        assertEquals(-1, add(groups, 10, 10, 20, 20, Double.POSITIVE_INFINITY, true));
        groups.finish();
        groups.clear();
        groups.begin(800, 600, 3, 250);
        for (int i = 0; i < 3; i++) add(groups, 100, 100, 20, 20, 300, true);
        groups.finish();
        assertTrue(groups.collapsed(0));
    }

    @Test void handlesTenThousandOverlappingWaypoints() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            WaypointGrouping groups = begin(10_000);
            for (int i = 0; i < 10_000; i++) add(groups, 100, 100, 20, 20, 300 + i, true);
            groups.finish();
            assertEquals(10_000, groups.groupSize(0));
            groups.finish();
            assertEquals(10_000, groups.groupSize(0));
        });
    }

    @Test void keepsExistingDenseStackThroughRepeatedRefreshes() {
        WaypointGrouping groups = begin(1);
        for (int refresh = 0; refresh < 20; refresh++) {
            groups.begin(800, 600, 1, 250);
            int stack = groups.add(100 + refresh, 100, 130 + refresh, 140, 300 * 300, true, 100, true);
            groups.memberBounds(stack, 90, 90, 150, 150);
            groups.finish();
            assertTrue(groups.collapsed(stack));
            assertEquals(100, groups.groupSize(stack));
            assertTrue(groups.denseGroup(stack));
            groups.finish();
            assertEquals(100, groups.groupSize(stack));
        }
    }

    @Test void addsNewlyDistantWaypointWithoutSplittingExistingStack() {
        WaypointGrouping groups = begin(2);
        for (int refresh = 0; refresh < 3; refresh++) {
            groups.begin(800, 600, 2, 250);
            int stack = groups.add(100, 100, 140, 140, 300 * 300, true, 100, true);
            int newcomer = add(groups, 120, 100, 20, 20, refresh == 0 ? 250 : 251, true);
            groups.finish();
            assertTrue(groups.collapsed(stack));
            assertEquals(refresh == 0 ? 100 : 101, groups.groupSize(stack));
            if (newcomer >= 0) assertEquals(newcomer, groups.nearestIndex(stack));
        }
    }

    @Test void keepsUnrelatedStackWhenAnotherWaypointCrossesDistanceThreshold() {
        WaypointGrouping groups = begin(2);
        for (int refresh = 0; refresh < 3; refresh++) {
            groups.begin(800, 600, 2, 250);
            int stack = groups.add(100, 100, 140, 140, 300 * 300, true, 100, true);
            add(groups, 500, 100, 20, 20, refresh == 1 ? 251 : 250, true);
            groups.finish();
            assertTrue(groups.collapsed(stack));
            assertEquals(100, groups.groupSize(stack));
        }
    }

    @Test void combinesExistingStacksWithoutCountingMembersTwice() {
        WaypointGrouping groups = begin(2);
        int first = groups.add(100, 100, 140, 140, 300 * 300, true, 100, true);
        int second = groups.add(120, 100, 160, 140, 301 * 301, true, 50, true);
        groups.memberBounds(first, 90, 90, 140, 140);
        groups.memberBounds(second, 120, 90, 160, 140);
        groups.finish();
        assertEquals(150, groups.groupSize(first));
        assertEquals(first, groups.representativeIndex(second));
        groups.includeStackBounds(first, 90, 90, 160, 150);
        groups.finish();
        assertEquals(150, groups.groupSize(second));
    }

    @Test void cannotExtendRetainedStackBeyondItsMemberArea() {
        WaypointGrouping groups = begin(2);
        int stack = groups.add(100, 100, 140, 140, 300 * 300, true, 100, true);
        groups.memberBounds(stack, 10, 90, 150, 150);
        int newcomer = add(groups, 100, 100, 20, 20, 251, true);
        groups.finish();
        assertEquals(100, groups.groupSize(stack));
        assertFalse(groups.collapsed(newcomer));
    }

    @Test void cannotExtendSparseStackPastFourMembersWithoutDenseOverlap() {
        WaypointGrouping groups = begin(2);
        int stack = groups.add(100, 100, 120, 120, 300 * 300, true, 4, false);
        int newcomer = add(groups, 118, 100, 20, 20, 301, true);
        groups.finish();
        assertEquals(4, groups.groupSize(stack));
        assertFalse(groups.collapsed(newcomer));
        assertFalse(groups.denseGroup(stack));
    }

    @Test void retainedStacksStillRequireThreeDistantMembersAndAnIcon() {
        WaypointGrouping groups = begin(3);
        int valid = groups.add(100, 100, 140, 140, 251 * 251, true, 3, true);
        int tooSmall = groups.add(300, 100, 340, 140, 251 * 251, true, 2, true);
        int noIcon = groups.add(500, 100, 540, 140, 251 * 251, false, 100, true);
        assertEquals(-1, groups.add(100, 100, 140, 140, 250 * 250, true, 100, true));
        groups.finish();
        assertTrue(groups.collapsed(valid));
        assertFalse(groups.collapsed(tooSmall));
        assertFalse(groups.collapsed(noIcon));
    }

    private static WaypointGrouping begin(int capacity) {
        WaypointGrouping groups = new WaypointGrouping();
        groups.begin(800, 600, capacity, 250);
        return groups;
    }

    private static int add(WaypointGrouping groups, float x, float y, float width, float height, double distance, boolean icon) {
        return groups.add(x, y, x + width, y + height, distance * distance, icon);
    }
}
