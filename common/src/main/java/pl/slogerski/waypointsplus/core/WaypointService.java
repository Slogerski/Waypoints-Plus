package pl.slogerski.waypointsplus.core;

import java.util.List;
import java.util.UUID;

/** Shared application logic. It intentionally has no Minecraft or Fabric imports. */
public final class WaypointService {
    private final WaypointRepository repository;

    public WaypointService(WaypointRepository repository) {
        this.repository = repository;
    }

    public Waypoint create(String name, String serverKey, String dimension, int x, int y, int z, String colorArgb) {
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), name, serverKey, dimension, x, y, z, colorArgb);
        repository.save(waypoint);
        return waypoint;
    }

    public List<Waypoint> list() {
        return repository.list();
    }
}
