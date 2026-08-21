package pl.slogerski.waypointsplus.core;

public final class WaypointDimensionProjection {
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    private WaypointDimensionProjection() { }

    public static double scale(String currentDimension, String waypointDimension, boolean enabled) {
        if (currentDimension.equals(waypointDimension)) return 1.0;
        if (!enabled || END.equals(currentDimension) || END.equals(waypointDimension)) return Double.NaN;
        if (NETHER.equals(currentDimension)) return 0.125;
        if (NETHER.equals(waypointDimension)) return 8.0;
        return 1.0;
    }
}
