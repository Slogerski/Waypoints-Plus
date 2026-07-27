package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

final class CreateWaypointScreen extends WaypointFormScreen {
    CreateWaypointScreen(Screen parent) {
        super(parent, Text.literal(UiText.get("Create waypoint", "Utwórz waypoint")), "",
                currentPos().getX(), currentPos().getY(), currentPos().getZ(),
                String.format("%08X", WaypointsPlusClient.config().settings().markerArgb));
    }

    private static BlockPos currentPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? BlockPos.ORIGIN : client.player.getBlockPos();
    }

    @Override protected void persist(String name, int x, int y, int z, String color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) throw new IllegalStateException();
        WaypointsPlusClient.config().addWaypoint(name, ServerScope.current(),
                client.world.getRegistryKey().getValue().toString(), x, y, z, color);
    }
}
