package pl.slogerski.waypointsplus.fabric;

final class WaypointScreenRouter {
    void openCreateWaypoint() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        client.setScreen(new CreateWaypointScreen(client.currentScreen));
    }

    void openWaypointManager() {
        net.minecraft.client.MinecraftClient.getInstance().setScreen(new WaypointManagerScreen(null));
    }

    void openAdvancedSettings() {
        net.minecraft.client.MinecraftClient.getInstance().setScreen(new WaypointSettingsScreen(null));
    }
}
