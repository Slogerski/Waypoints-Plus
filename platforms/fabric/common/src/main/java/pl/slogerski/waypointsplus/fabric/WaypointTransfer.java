package pl.slogerski.waypointsplus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class WaypointTransfer {
    private static final int MAX_WAYPOINTS = 10_000;
    private static final int MAX_IMPORT_CHARS = 4 * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_DIMENSION_LENGTH = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WaypointTransfer() { }

    static String exportText(List<Waypoint> waypoints) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "waypointsplus");
        root.addProperty("schemaVersion", 1);
        JsonArray values = new JsonArray();
        for (Waypoint waypoint : waypoints) {
            JsonObject value = new JsonObject();
            value.addProperty("name", waypoint.name());
            value.addProperty("x", waypoint.x());
            value.addProperty("y", waypoint.y());
            value.addProperty("z", waypoint.z());
            value.addProperty("dimension", waypoint.dimension());
            value.addProperty("color", waypoint.colorArgb());
            values.add(value);
        }
        root.add("waypoints", values);
        return GSON.toJson(root);
    }

    static List<Entry> importText(String text) {
        String input = text == null ? "" : text.trim();
        if (input.isEmpty() || input.length() > MAX_IMPORT_CHARS || hasExcessiveNesting(input)) {
            throw new IllegalArgumentException("Invalid input size");
        }
        JsonElement parsed = JsonParser.parseString(input);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid root");
        JsonObject root = parsed.getAsJsonObject();
        if (!"waypointsplus".equals(string(root, "format"))
                || integer(root, "schemaVersion") != 1
                || !root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
            throw new IllegalArgumentException("Unsupported format");
        }
        JsonArray values = root.getAsJsonArray("waypoints");
        if (values.isEmpty() || values.size() > MAX_WAYPOINTS) throw new IllegalArgumentException("Invalid size");
        List<Entry> entries = new ArrayList<>(values.size());
        for (JsonElement element : values) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid waypoint");
            JsonObject value = element.getAsJsonObject();
            String name = string(value, "name").trim();
            String dimension = string(value, "dimension").trim();
            String color = string(value, "color").replace("#", "").toUpperCase(Locale.ROOT);
            if (name.isEmpty() || name.length() > 128 || containsControlCharacter(name)
                    || dimension.length() > MAX_DIMENSION_LENGTH
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || !color.matches("[0-9A-F]{8}")) {
                throw new IllegalArgumentException("Invalid waypoint values");
            }
            entries.add(new Entry(name, integer(value, "x"), integer(value, "y"), integer(value, "z"),
                    dimension, color));
        }
        return List.copyOf(entries);
    }

    static List<String> customDimensions(List<Entry> entries) {
        Set<String> dimensions = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (!isVanillaDimension(entry.dimension())) dimensions.add(entry.dimension());
        }
        return List.copyOf(dimensions);
    }

    static List<Entry> remapDimensions(List<Entry> entries, Map<String, String> replacements) {
        List<Entry> remapped = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            String dimension = replacements.getOrDefault(entry.dimension(), entry.dimension());
            remapped.add(new Entry(entry.name(), entry.x(), entry.y(), entry.z(), dimension, entry.colorArgb()));
        }
        return List.copyOf(remapped);
    }

    private static boolean isVanillaDimension(String dimension) {
        return "minecraft:overworld".equals(dimension)
                || "minecraft:the_nether".equals(dimension)
                || "minecraft:the_end".equals(dimension);
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }

    private static boolean hasExcessiveNesting(String value) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
                continue;
            }
            if (character == '"') quoted = true;
            else if (character == '{' || character == '[') {
                if (++depth > MAX_JSON_DEPTH) return true;
            } else if ((character == '}' || character == ']') && --depth < 0) {
                return true;
            }
        }
        return false;
    }

    private static String string(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) throw new IllegalArgumentException("Missing " + name);
        return object.get(name).getAsString();
    }

    private static int integer(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) throw new IllegalArgumentException("Missing " + name);
        try {
            return object.get(name).getAsInt();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + name, exception);
        }
    }

    record Entry(String name, int x, int y, int z, String dimension, String colorArgb) { }
}
