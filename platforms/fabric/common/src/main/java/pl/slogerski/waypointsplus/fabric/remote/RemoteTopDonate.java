package pl.slogerski.waypointsplus.fabric.remote;

import java.util.List;
import java.util.Objects;

public record RemoteTopDonate(List<TopDonateEntry> entries) {
    public static final RemoteTopDonate EMPTY = new RemoteTopDonate(List.of());

    public RemoteTopDonate {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > 8) {
            throw new IllegalArgumentException("entries");
        }
    }
}
