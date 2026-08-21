package pl.slogerski.waypointsplus.fabric.remote;

import java.nio.file.Path;
import java.util.Objects;

public record RemoteImage(Path file, int width, int height, long revision) {
    public RemoteImage {
        file = Objects.requireNonNull(file, "file");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("dimensions");
        }
        if (revision < 0) revision = 0;
    }
}
