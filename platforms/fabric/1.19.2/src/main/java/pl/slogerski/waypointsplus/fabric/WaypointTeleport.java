package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.MinecraftClient;
import pl.slogerski.waypointsplus.core.Waypoint;

final class WaypointTeleport {
    private WaypointTeleport() { }

    static boolean available(MinecraftClient client, Waypoint waypoint) {
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return false;
        if (!client.world.getRegistryKey().getValue().toString().equals(waypoint.dimension())) return false;
        var root = client.getNetworkHandler().getCommandDispatcher().getRoot();
        return root.getChild("teleport") != null || root.getChild("tp") != null;
    }

    static void teleport(MinecraftClient client, Waypoint waypoint) {
        if (!available(client, waypoint)) return;
        String command = client.getNetworkHandler().getCommandDispatcher().getRoot().getChild("teleport") != null
                ? "teleport" : "tp";
        client.player.sendCommand(command + " @s "
                + waypoint.x() + " " + waypoint.y() + " " + waypoint.z());
    }
}
