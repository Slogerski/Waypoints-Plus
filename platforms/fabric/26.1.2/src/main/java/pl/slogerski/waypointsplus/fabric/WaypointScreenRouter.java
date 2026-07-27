package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;

final class WaypointScreenRouter {
    void openCreateWaypoint() {
        var minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CreateWaypointScreen(minecraft.screen));
    }

    void openWaypointManager() {
        Minecraft.getInstance().setScreen(new WaypointManagerScreen(null));
    }

    void openAdvancedSettings() {
        Minecraft.getInstance().setScreen(new WaypointSettingsScreen(null));
    }
}
