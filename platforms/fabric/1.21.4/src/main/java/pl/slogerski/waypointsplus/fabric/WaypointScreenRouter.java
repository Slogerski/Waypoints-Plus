package pl.slogerski.waypointsplus.fabric;

/**
 * The only platform/UI boundary used by input. Screens are deliberately not
 * implemented yet; later versions can supply screens without changing core.
 */
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
