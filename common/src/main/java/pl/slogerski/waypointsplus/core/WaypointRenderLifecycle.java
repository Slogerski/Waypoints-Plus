package pl.slogerski.waypointsplus.core;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WaypointRenderLifecycle {
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean invalidated = new AtomicBoolean();

    public void invalidate() {
        invalidated.set(true);
    }

    public boolean beginFrame(Runnable cleanup) {
        if (!active.compareAndSet(false, true)) return false;
        try {
            clearPending(cleanup);
            return true;
        } catch (RuntimeException | Error failure) {
            active.set(false);
            throw failure;
        }
    }

    public void endFrame() {
        active.set(false);
    }

    public void clearIfIdle(Runnable cleanup) {
        if (!invalidated.get() || !active.compareAndSet(false, true)) return;
        try {
            clearPending(cleanup);
        } finally {
            active.set(false);
        }
    }

    private void clearPending(Runnable cleanup) {
        if (!invalidated.get() || !invalidated.getAndSet(false)) return;
        try {
            cleanup.run();
        } catch (RuntimeException | Error failure) {
            invalidated.set(true);
            throw failure;
        }
    }
}
