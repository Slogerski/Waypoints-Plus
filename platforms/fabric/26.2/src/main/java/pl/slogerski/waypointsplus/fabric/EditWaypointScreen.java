package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.slogerski.waypointsplus.core.Waypoint;

final class EditWaypointScreen extends WaypointFormScreen {
    private final Waypoint waypoint;

    EditWaypointScreen(Screen parent, Waypoint waypoint) {
        super(parent, Component.literal(UiText.get("Edit Waypoint", "Edytuj waypoint")), waypoint.name(),
                waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb());
        this.waypoint = waypoint;
    }

    @Override protected void persist(String name, int x, int y, int z, String color) {
        WaypointsPlusClient.config().updateWaypoint(new Waypoint(waypoint.id(), name, waypoint.serverKey(),
                waypoint.profile(), waypoint.dimension(), x, y, z, color));
    }
}
