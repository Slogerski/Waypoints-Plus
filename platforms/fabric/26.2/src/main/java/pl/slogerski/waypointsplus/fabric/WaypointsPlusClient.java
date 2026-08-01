package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import pl.slogerski.waypointsplus.core.WaypointNames;

public final class WaypointsPlusClient implements ClientModInitializer {
    private static final String DEATH_COLOR = "EEFF405D";
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.parse("waypointsplus:waypoints"));
    private static WaypointConfigStore config;
    private final WaypointScreenRouter screens = new WaypointScreenRouter();
    private KeyMapping createWaypointKey;
    private KeyMapping manageWaypointsKey;
    private KeyMapping reloadWaypointsKey;
    private KeyMapping previousProfileKey;
    private KeyMapping nextProfileKey;
    private int lastPlayerX, lastPlayerY, lastPlayerZ;
    private boolean hasPlayerPosition;
    private boolean wasPlayerDead;

    static WaypointConfigStore config() { return config; }

    @Override
    public void onInitializeClient() {
        config = new WaypointConfigStore();
        config.load();
        WaypointHudRenderer.register();
        createWaypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointsplus.create", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        manageWaypointsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointsplus.manage", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, CATEGORY));
        reloadWaypointsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointsplus.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        previousProfileKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointsplus.profile_previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        nextProfileKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointsplus.profile_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                var position = client.player.blockPosition();
                lastPlayerX = position.getX();
                lastPlayerY = position.getY();
                lastPlayerZ = position.getZ();
                hasPlayerPosition = true;
                boolean playerDead = client.player.getHealth() <= 0.0F;
                if (playerDead && !wasPlayerDead && client.level != null) {
                    config.addWaypointToProfile(WaypointNames.death(config.settings().language),
                            ServerScope.current(), "Death Waypoints",
                            client.level.dimension().identifier().toString(),
                            position.getX(), position.getY(), position.getZ(), DEATH_COLOR);
                }
                wasPlayerDead = playerDead;
            } else {
                wasPlayerDead = false;
            }
            while (createWaypointKey.consumeClick()) screens.openCreateWaypoint();
            while (manageWaypointsKey.consumeClick()) screens.openWaypointManager();
            while (reloadWaypointsKey.consumeClick()) {
                if (client.player == null) {
                    config.load();
                } else {
                    var position = client.player.blockPosition();
                    config.reloadWithPlayerPosition(position.getX(), position.getY(), position.getZ());
                }
            }
            while (previousProfileKey.consumeClick()) config.shiftProfile(ServerScope.current(), -1);
            while (nextProfileKey.consumeClick()) config.shiftProfile(ServerScope.current(), 1);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (hasPlayerPosition) {
                config.savePlayerPosition(lastPlayerX, lastPlayerY, lastPlayerZ);
                hasPlayerPosition = false;
            }
        });
    }
}
