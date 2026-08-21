package pl.slogerski.waypointsplus.fabric.remote;

import java.util.Optional;

public record RemoteAboutAdSnapshot(Optional<RemoteAboutAd> ad, Optional<RemoteImage> image) {
    public RemoteAboutAdSnapshot {
        ad = ad == null ? Optional.empty() : ad;
        image = image == null ? Optional.empty() : image;
    }

    public static RemoteAboutAdSnapshot empty() {
        return new RemoteAboutAdSnapshot(Optional.empty(), Optional.empty());
    }
}
