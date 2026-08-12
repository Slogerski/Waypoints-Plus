package pl.slogerski.waypointsplus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WaypointTransfer {
    private static final int MAX_WAYPOINTS = 10_000;
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
        JsonElement parsed = JsonParser.parseString(text == null ? "" : text.trim());
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
            if (name.isEmpty() || name.length() > 128
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || !color.matches("[0-9A-F]{8}")) {
                throw new IllegalArgumentException("Invalid waypoint values");
            }
            entries.add(new Entry(name, integer(value, "x"), integer(value, "y"), integer(value, "z"),
                    dimension, color));
        }
        return List.copyOf(entries);
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
