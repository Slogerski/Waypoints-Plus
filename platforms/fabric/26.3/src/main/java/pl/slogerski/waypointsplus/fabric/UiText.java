package pl.slogerski.waypointsplus.fabric;

final class UiText {
    private UiText() { }
    static String get(String english, String polish) {
        return "pl".equals(WaypointsPlusClient.config().settings().language) ? polish : english;
    }
}
