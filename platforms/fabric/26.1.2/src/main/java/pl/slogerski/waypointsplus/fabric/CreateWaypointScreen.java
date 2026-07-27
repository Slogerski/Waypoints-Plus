package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class CreateWaypointScreen extends WaypointFormScreen {
    CreateWaypointScreen(Screen parent) {
        super(parent, Component.literal(UiText.get("Create waypoint", "Utwórz waypoint")), "",
                currentPos().getX(), currentPos().getY(), currentPos().getZ(),
                String.format("%08X", WaypointsPlusClient.config().settings().markerArgb));
    }

    private static BlockPos currentPos() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
    }

    @Override protected void persist(String name, int x, int y, int z, String color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) throw new IllegalStateException();
        WaypointsPlusClient.config().addWaypoint(name, ServerScope.current(),
                minecraft.level.dimension().identifier().toString(), x, y, z, color);
    }
}
