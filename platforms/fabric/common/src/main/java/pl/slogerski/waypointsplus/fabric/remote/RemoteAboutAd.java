package pl.slogerski.waypointsplus.fabric.remote;

import java.util.Objects;

public record RemoteAboutAd(String id, LocalizedText title, LocalizedText text, String targetUrl, String imageUrl) {
    public static final RemoteAboutAd FALLBACK = new RemoteAboutAd("waypoints-plus",
            new LocalizedText("Waypoints Plus:", "Waypoints Plus:"),
            new LocalizedText("Perfect for multiplayer!", "Idealny dla graczy multiplayer!"),
            "https://modrinth.com/mod/waypoints-plus", "");

    public RemoteAboutAd {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("id");
        }
        title = Objects.requireNonNull(title, "title");
        text = Objects.requireNonNull(text, "text");
        targetUrl = RemoteLinks.url(targetUrl, false);
        imageUrl = RemoteLinks.url(imageUrl, true);
    }
}
