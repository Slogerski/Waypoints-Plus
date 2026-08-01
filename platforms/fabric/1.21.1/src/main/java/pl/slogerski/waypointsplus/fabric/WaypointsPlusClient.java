package pl.slogerski.waypointsplus.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import pl.slogerski.waypointsplus.core.WaypointNames;

public final class WaypointsPlusClient implements ClientModInitializer {
    private static final String CATEGORY = "key.category.waypointsplus";
    private static final String DEATH_COLOR = "EEFF405D";
    private final WaypointScreenRouter screens = new WaypointScreenRouter();
    private KeyBinding createWaypointKey;
    private KeyBinding manageWaypointsKey;
    private KeyBinding reloadWaypointsKey;
    private KeyBinding previousProfileKey;
    private KeyBinding nextProfileKey;
    private int lastPlayerX, lastPlayerY, lastPlayerZ;
    private boolean hasPlayerPosition;
    private boolean wasPlayerDead;
    private static WaypointConfigStore config;

    static WaypointConfigStore config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = new WaypointConfigStore();
        config.load();
        WaypointHudRenderer.register();
        createWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointsplus.create", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        manageWaypointsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointsplus.manage", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, CATEGORY));
        reloadWaypointsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointsplus.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        previousProfileKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointsplus.profile_previous", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        nextProfileKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointsplus.profile_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                var position = client.player.getBlockPos();
                lastPlayerX = position.getX();
                lastPlayerY = position.getY();
                lastPlayerZ = position.getZ();
                hasPlayerPosition = true;
                boolean playerDead = client.player.getHealth() <= 0.0F;
                if (playerDead && !wasPlayerDead && client.world != null) {
                    config.addWaypointToProfile(WaypointNames.death(config.settings().language),
                            ServerScope.current(), "Death Waypoints",
                            client.world.getRegistryKey().getValue().toString(),
                            position.getX(), position.getY(), position.getZ(), DEATH_COLOR);
                }
                wasPlayerDead = playerDead;
            } else {
                wasPlayerDead = false;
            }
            while (createWaypointKey.wasPressed()) {
                screens.openCreateWaypoint();
            }
            while (manageWaypointsKey.wasPressed()) {
                screens.openWaypointManager();
            }
            while (reloadWaypointsKey.wasPressed()) {
                if (client.player == null) {
                    config.load();
                } else {
                    var position = client.player.getBlockPos();
                    config.reloadWithPlayerPosition(position.getX(), position.getY(), position.getZ());
                }
            }
            while (previousProfileKey.wasPressed()) config.shiftProfile(ServerScope.current(), -1);
            while (nextProfileKey.wasPressed()) config.shiftProfile(ServerScope.current(), 1);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (hasPlayerPosition) {
                config.savePlayerPosition(lastPlayerX, lastPlayerY, lastPlayerZ);
                hasPlayerPosition = false;
            }
        });
    }
}
