package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class CreateWaypointScreen extends WaypointFormScreen {
    CreateWaypointScreen(Screen parent) {
        super(parent, Component.literal(UiText.get("Create Waypoint", "Utwórz waypoint")), "",
                currentPos().getX(), currentPos().getY(), currentPos().getZ(),
                String.format("%08X", WaypointsPlusClient.config().settings().markerArgb), currentDimension());
    }

    private static BlockPos currentPos() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
    }

    private static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().identifier().toString();
    }

    @Override protected void persist(String name, int x, int y, int z, String color, String dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) throw new IllegalStateException();
        WaypointsPlusClient.config().addWaypoint(name, ServerScope.current(), dimension, x, y, z, color);
    }
}
