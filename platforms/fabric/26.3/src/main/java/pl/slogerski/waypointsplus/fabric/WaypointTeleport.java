package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import pl.slogerski.waypointsplus.core.Waypoint;

final class WaypointTeleport {
    private WaypointTeleport() { }

    static boolean available(Minecraft client, Waypoint waypoint) {
        if (client.player == null || client.level == null || client.getConnection() == null) return false;
        if (!client.level.dimension().identifier().toString().equals(waypoint.dimension())) return false;
        var root = client.getConnection().getCommands().getRoot();
        return root.getChild("teleport") != null || root.getChild("tp") != null;
    }

    static void teleport(Minecraft client, Waypoint waypoint) {
        if (!available(client, waypoint)) return;
        String command = client.getConnection().getCommands().getRoot().getChild("teleport") != null
                ? "teleport" : "tp";
        client.getConnection().sendCommand(command + " @s "
                + waypoint.x() + " " + waypoint.y() + " " + waypoint.z());
    }
}
