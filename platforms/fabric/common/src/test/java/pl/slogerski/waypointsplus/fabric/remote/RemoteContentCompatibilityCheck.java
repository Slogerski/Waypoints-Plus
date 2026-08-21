package pl.slogerski.waypointsplus.fabric.remote;

import java.util.concurrent.CompletableFuture;

public final class RemoteContentCompatibilityCheck {
    private RemoteContentCompatibilityCheck() {
    }

    public static void run() {
        RemoteLinks fallback = RemoteLinks.FALLBACK;
        require(fallback.discord().isEmpty(), "Discord fallback must be empty");
        require(fallback.source().equals("https://github.com/Slogerski/Waypoints-Plus"), "Source fallback changed");
        require(new LocalizedText("English", "Polski").forLanguage("pl_pl").equals("Polski"),
                "Polish localization was not selected");
        require(new RemoteTopDonate(java.util.List.of()).entries().isEmpty(), "Empty top donate changed");
        require(new TopDonateEntry("Donor", "443", "PLN", "", "").formattedAmount().equals("443 PLN"),
                "Currency code was not formatted");
        require(new TopDonateEntry("Donor", "52,50", "$", "", "").formattedAmount().equals("52,50$"),
                "Currency symbol was not formatted");
        require(RemoteImagePolicy.allows(1120, 256), "Recommended ad size was rejected");
        require(!RemoteImagePolicy.allows(4096, 4096), "Oversized decoded image was accepted");
        require(RemoteImagePolicy.allowsRemoteUri(java.net.URI.create(
                        "https://raw.githubusercontent.com/Slogerski/Waypoints-Plus/main/ad.png")),
                "GitHub image URL was rejected");
        require(!RemoteImagePolicy.allowsRemoteUri(java.net.URI.create("https://127.0.0.1/ad.png")),
                "Private image endpoint was accepted");
        try {
            new RemoteLinks("http://example.com", fallback.curseForge(), fallback.source(), fallback.coffee(), "");
            throw new AssertionError("HTTP URL was accepted");
        } catch (IllegalArgumentException expected) {
        }
        RemoteContentSession<RemoteLinks> session = new RemoteContentSession<>(fallback,
                CompletableFuture.completedFuture(fallback));
        require(session.snapshot() == fallback, "Session snapshot changed");
        session.close();
        try {
            session.snapshot();
            throw new AssertionError("Closed session retained its snapshot");
        } catch (IllegalStateException expected) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
