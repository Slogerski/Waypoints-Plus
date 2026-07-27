package pl.slogerski.waypointsplus.core;

import java.util.List;

/** Storage boundary: platform-specific persistence belongs behind this interface. */
public interface WaypointRepository {
    List<Waypoint> list();

    void save(Waypoint waypoint);

    boolean remove(Waypoint waypoint);
}
