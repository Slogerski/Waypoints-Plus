package pl.slogerski.waypointsplus.core;

import java.util.Objects;
import java.util.UUID;

public record Waypoint(UUID id, String name, String serverKey, String profile, String dimension,
                       int x, int y, int z, String colorArgb) {
    public Waypoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        profile = profile == null || profile.isBlank() ? "Default" : profile;
        Objects.requireNonNull(dimension, "dimension");
        colorArgb = colorArgb == null ? "EE00F5FF" : colorArgb;
    }

    public Waypoint(UUID id, String name, String serverKey, String dimension,
                    int x, int y, int z, String colorArgb) {
        this(id, name, serverKey, "Default", dimension, x, y, z, colorArgb);
    }
}
