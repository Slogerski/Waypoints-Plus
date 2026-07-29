package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.util.Locale;

final class ServerScope {
    private ServerScope() { }

    static String current() {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getCurrentServer();
        if (server != null) return "server:" + normalize(server.ip);
        if (minecraft.getSingleplayerServer() != null) {
            var folder = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getFileName();
            return "singleplayer:" + (folder == null
                    ? minecraft.getSingleplayerServer().getWorldData().getLevelName()
                    : folder);
        }
        return "unknown";
    }

    static String legacySingleplayer() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : "singleplayer:" + server.getWorldData().getLevelName();
    }

    private static String normalize(String address) {
        if (address == null || address.isBlank()) return "unknown";
        String value = address.trim().toLowerCase(Locale.ROOT).replace("minecraft://", "").replace("mc://", "");
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value.contains(":") ? value : value + ":25565";
    }
}
