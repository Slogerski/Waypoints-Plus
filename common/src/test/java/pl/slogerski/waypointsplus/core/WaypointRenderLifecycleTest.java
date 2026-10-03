package pl.slogerski.waypointsplus.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointRenderLifecycleTest {
    @Test void defersWorldSwitchCleanupUntilTheActiveFrameEnds() throws Exception {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        List<Integer> visible = new ArrayList<>(List.of(1, 2, 3, 4));
        assertTrue(lifecycle.beginFrame(visible::clear));
        var worker = Executors.newSingleThreadExecutor();
        try {
            int drawn = 0;
            for (int waypoint : visible) {
                worker.submit(() -> {
                    lifecycle.invalidate();
                    lifecycle.clearIfIdle(visible::clear);
                }).get(2, TimeUnit.SECONDS);
                drawn += waypoint;
            }
            assertEquals(10, drawn);
            assertEquals(4, visible.size());
        } finally {
            worker.shutdownNow();
            lifecycle.endFrame();
        }
        lifecycle.clearIfIdle(visible::clear);
        assertTrue(visible.isEmpty());
    }

    @Test void skipsNestedAndConcurrentFrames() throws Exception {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        Runnable cleanup = () -> { };
        assertTrue(lifecycle.beginFrame(cleanup));
        var worker = Executors.newSingleThreadExecutor();
        try {
            assertFalse(lifecycle.beginFrame(cleanup));
            assertFalse(worker.submit(() -> lifecycle.beginFrame(cleanup)).get(2, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
            lifecycle.endFrame();
        }
        assertTrue(lifecycle.beginFrame(cleanup));
        lifecycle.endFrame();
    }

    @Test void cleansPendingResourcesBeforeTheNextFrame() {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        AtomicInteger cleanups = new AtomicInteger();
        lifecycle.invalidate();
        assertTrue(lifecycle.beginFrame(cleanups::incrementAndGet));
        assertEquals(1, cleanups.get());
        lifecycle.endFrame();
        assertTrue(lifecycle.beginFrame(cleanups::incrementAndGet));
        assertEquals(1, cleanups.get());
        lifecycle.endFrame();
    }

    @Test void releasesCacheInMenusWithoutRequiringAnotherWorldRender() {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        AtomicInteger cleanups = new AtomicInteger();
        lifecycle.invalidate();
        lifecycle.invalidate();
        lifecycle.clearIfIdle(cleanups::incrementAndGet);
        lifecycle.clearIfIdle(cleanups::incrementAndGet);
        assertEquals(1, cleanups.get());
    }

    @Test void preservesInvalidationRaisedDuringCleanup() {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        AtomicInteger cleanups = new AtomicInteger();
        lifecycle.invalidate();
        assertTrue(lifecycle.beginFrame(() -> {
            cleanups.incrementAndGet();
            lifecycle.invalidate();
            assertFalse(lifecycle.beginFrame(cleanups::incrementAndGet));
            lifecycle.clearIfIdle(cleanups::incrementAndGet);
        }));
        lifecycle.endFrame();
        lifecycle.clearIfIdle(cleanups::incrementAndGet);
        assertEquals(2, cleanups.get());
    }

    @Test void releasesFrameGuardAndPreservesRequestAfterCleanupFailure() {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        lifecycle.invalidate();
        assertThrows(IllegalStateException.class, () -> lifecycle.beginFrame(() -> {
            throw new IllegalStateException("cleanup");
        }));
        AtomicInteger cleanups = new AtomicInteger();
        assertTrue(lifecycle.beginFrame(cleanups::incrementAndGet));
        lifecycle.endFrame();
        assertEquals(1, cleanups.get());
    }

    @Test void releasesIdleGuardAndPreservesRequestAfterCleanupFailure() {
        WaypointRenderLifecycle lifecycle = new WaypointRenderLifecycle();
        lifecycle.invalidate();
        assertThrows(IllegalStateException.class, () -> lifecycle.clearIfIdle(() -> {
            throw new IllegalStateException("cleanup");
        }));
        AtomicInteger cleanups = new AtomicInteger();
        lifecycle.clearIfIdle(cleanups::incrementAndGet);
        assertEquals(1, cleanups.get());
    }
}
