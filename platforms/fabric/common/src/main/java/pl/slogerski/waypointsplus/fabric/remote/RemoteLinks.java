package pl.slogerski.waypointsplus.fabric.remote;

import java.util.Objects;

public record RemoteLinks(String modrinth, String curseForge, String source, String coffee, String discord) {
    public static final RemoteLinks FALLBACK = new RemoteLinks(
            "https://modrinth.com/mod/waypoints-plus",
            "https://www.curseforge.com/members/slogerski/projects",
            "https://github.com/Slogerski/Waypoints-Plus",
            "https://buymeacoffee.com/slogerski", "");

    public RemoteLinks {
        modrinth = url(modrinth, false);
        curseForge = url(curseForge, false);
        source = url(source, false);
        coffee = url(coffee, false);
        discord = url(discord, true);
    }

    static String url(String value, boolean allowEmpty) {
        Objects.requireNonNull(value, "url");
        if (allowEmpty && value.isBlank()) {
            return "";
        }
        if (value.length() > 2_048 || !value.startsWith("https://")) {
            throw new IllegalArgumentException("url");
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("url");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("url", exception);
        }
        return value;
    }
}
