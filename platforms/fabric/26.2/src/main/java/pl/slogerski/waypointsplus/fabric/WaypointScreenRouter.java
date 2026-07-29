package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;

final class WaypointScreenRouter {
    void openCreateWaypoint() {
        var minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new CreateWaypointScreen(minecraft.gui.screen()));
    }

    void openWaypointManager() {
        Minecraft.getInstance().gui.setScreen(new WaypointManagerScreen(null));
    }

    void openAdvancedSettings() {
        Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen(null));
    }
}
