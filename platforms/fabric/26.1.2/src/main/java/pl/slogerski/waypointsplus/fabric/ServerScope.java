package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;

import java.util.Locale;

final class ServerScope {
    private ServerScope() { }

    static String current() {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getCurrentServer();
        if (server != null) return "server:" + normalize(server.ip);
        if (minecraft.getSingleplayerServer() != null) {
            return "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
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
