package pl.slogerski.waypointsplus.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WaypointDimensionFilter {
    public static final String ALL = "";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    private WaypointDimensionFilter() { }

    public static boolean matches(String filter, String dimension) {
        return filter == null || filter.isEmpty() || filter.equals(dimension);
    }

    public static List<String> available(List<String> dimensions) {
        Set<String> unique = new LinkedHashSet<>(dimensions);
        List<String> result = new ArrayList<>();
        result.add(ALL);
        if (unique.remove(OVERWORLD)) result.add(OVERWORLD);
        if (unique.remove(NETHER)) result.add(NETHER);
        if (unique.remove(END)) result.add(END);
        unique.stream().filter(value -> value != null && !value.isBlank())
                .sorted(Comparator.comparing(WaypointDimensionFilter::label, String.CASE_INSENSITIVE_ORDER))
                .forEach(result::add);
        return List.copyOf(result);
    }

    public static String next(String current, List<String> available) {
        if (available.size() <= 1) return ALL;
        int index = available.indexOf(current);
        return available.get((Math.max(index, 0) + 1) % available.size());
    }

    public static String label(String dimension) {
        if (dimension == null || dimension.isEmpty()) return "All";
        if (OVERWORLD.equals(dimension)) return "Overworld";
        if (NETHER.equals(dimension)) return "Nether";
        if (END.equals(dimension)) return "End";
        int separator = dimension.indexOf(':');
        return separator >= 0 && separator + 1 < dimension.length()
                ? dimension.substring(separator + 1)
                : dimension;
    }

    public static int labelColor(String dimension) {
        if (OVERWORLD.equals(dimension)) return 0xC8D8D8D8;
        if (NETHER.equals(dimension)) return 0xC8B70E0E;
        if (END.equals(dimension)) return 0xC8C43AD2;
        return 0xC84EBBFF;
    }
}
