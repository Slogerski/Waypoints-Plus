package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import pl.slogerski.waypointsplus.core.Waypoint;

final class EditWaypointScreen extends WaypointFormScreen {
    private final Waypoint waypoint;

    EditWaypointScreen(Screen parent, Waypoint waypoint) {
        super(parent, Text.literal(UiText.get("Edit Waypoint", "Edytuj waypoint")), waypoint.name(),
                waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb(), waypoint.dimension());
        this.waypoint = waypoint;
    }

    @Override protected void persist(String name, int x, int y, int z, String color, String dimension) {
        WaypointsPlusClient.config().updateWaypoint(new Waypoint(waypoint.id(), name, waypoint.serverKey(),
                waypoint.profile(), dimension, x, y, z, color));
    }
}
