package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.util.Locale;

final class ServerScope {
    private ServerScope() { }

    static String current() {
        MinecraftClient client = MinecraftClient.getInstance();
        var server = client.getCurrentServerEntry();
        if (server != null) return "server:" + normalize(server.address);
        if (client.getServer() != null) {
            var folder = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName();
            return "singleplayer:" + (folder == null ? client.getServer().getSaveProperties().getLevelName() : folder);
        }
        return "unknown";
    }

    static String legacySingleplayer() {
        var server = MinecraftClient.getInstance().getServer();
        return server == null ? null : "singleplayer:" + server.getSaveProperties().getLevelName();
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
