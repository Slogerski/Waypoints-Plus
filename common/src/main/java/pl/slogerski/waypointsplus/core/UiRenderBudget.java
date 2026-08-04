package pl.slogerski.waypointsplus.core;

import java.util.Map;
import java.util.WeakHashMap;

public final class UiRenderBudget {
    private static final long RESIZE_COOLDOWN_NANOS = 180_000_000L;
    private static final Map<Object, State> STATES = new WeakHashMap<>();

    private UiRenderBudget() { }

    public static synchronized boolean shouldRenderBlur(Object screen, int width, int height, boolean enabled) {
        long now = System.nanoTime();
        State state = STATES.get(screen);
        if (state == null) {
            STATES.put(screen, new State(width, height));
            return enabled;
        }

        boolean resized = state.width != width || state.height != height;
        if (resized) {
            state.width = width;
            state.height = height;
            state.blurResumeNanos = now + RESIZE_COOLDOWN_NANOS;
        }
        return enabled && now >= state.blurResumeNanos;
    }

    public static synchronized boolean isResizeActive(Object screen) {
        State state = STATES.get(screen);
        return state != null && System.nanoTime() < state.blurResumeNanos;
    }

    private static final class State {
        private int width;
        private int height;
        private long blurResumeNanos;

        private State(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
