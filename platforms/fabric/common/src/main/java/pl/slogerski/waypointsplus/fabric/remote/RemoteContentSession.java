package pl.slogerski.waypointsplus.fabric.remote;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class RemoteContentSession<T> implements AutoCloseable {
    private T snapshot;
    private CompletableFuture<T> refresh;

    RemoteContentSession(T snapshot, CompletableFuture<T> refresh) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    public T snapshot() {
        if (snapshot == null) {
            throw new IllegalStateException("session is closed");
        }
        return snapshot;
    }

    public CompletableFuture<T> refresh() {
        if (refresh == null) {
            throw new IllegalStateException("session is closed");
        }
        return refresh;
    }

    @Override
    public void close() {
        if (refresh != null) {
            refresh.cancel(true);
        }
        refresh = null;
        snapshot = null;
    }
}
