package pl.slogerski.waypointsplus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WaypointPresetTransfer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_CHARS = 4 * 1024 * 1024;

    private WaypointPresetTransfer() { }

    static String exportText(List<Waypoint> waypoints) {
        if (waypoints.isEmpty() || waypoints.size() > 10000) throw new IllegalArgumentException();
        JsonObject root = JsonParser.parseString(WaypointTransfer.exportText(waypoints)).getAsJsonObject();
        JsonObject presets = new JsonObject();
        Map<String, WaypointPreset> definitions = new LinkedHashMap<>();
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            String id = WaypointPresetStore.selected(waypoint.id());
            if ("default".equals(id)) continue;
            WaypointPreset preset = definitions.computeIfAbsent(id, WaypointPresetStore::find);
            if (preset == null) continue;
            presets.addProperty(id, preset.name);
            JsonObject value = root.getAsJsonArray("waypoints").get(i).getAsJsonObject();
            value.addProperty("preset", id);
            String item = WaypointPresetStore.item(waypoint.id());
            if (preset.icon && !preset.pngIcon && !item.isEmpty()) value.addProperty("item", item);
        }
        if (presets.size() != 0) root.add("presets", presets);
        String text = GSON.toJson(root);
        if (text.length() > MAX_CHARS) throw new IllegalArgumentException();
        return text;
    }

    static Payload importText(String text) {
        List<WaypointTransfer.Entry> entries = WaypointTransfer.importText(text);
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        Map<String, String> names = new LinkedHashMap<>();
        if (root.has("presets")) {
            if (!root.get("presets").isJsonObject()) throw new IllegalArgumentException();
            JsonObject presets = root.getAsJsonObject("presets");
            if (presets.size() > 66) throw new IllegalArgumentException();
            for (var entry : presets.entrySet()) {
                String name = string(entry.getValue());
                if (!validId(entry.getKey()) || name.isBlank() || name.length() > 64
                        || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
                names.put(entry.getKey(), name);
            }
        }
        List<WaypointPresetStore.Selection> selections = new ArrayList<>(entries.size());
        for (JsonElement element : root.getAsJsonArray("waypoints")) {
            JsonObject value = element.getAsJsonObject();
            String preset = value.has("preset") ? string(value.get("preset")) : "default";
            if (!validId(preset)) throw new IllegalArgumentException();
            String item = value.has("item") ? string(value.get("item")) : "";
            if (item.length() > 256) throw new IllegalArgumentException();
            if (!item.isEmpty() && !WaypointPresetStore.validItem(item)) item = "";
            if (!"default".equals(preset)) names.putIfAbsent(preset, preset);
            if (names.size() > 66) throw new IllegalArgumentException();
            selections.add(new WaypointPresetStore.Selection(preset, item));
        }
        return new Payload(entries, List.copyOf(selections), Map.copyOf(names));
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static boolean validId(String id) {
        return id != null && id.matches("default|[0-9a-fA-F-]{36}");
    }

    record Payload(List<WaypointTransfer.Entry> entries, List<WaypointPresetStore.Selection> selections,
                   Map<String, String> names) {
        Payload withEntries(List<WaypointTransfer.Entry> values) {
            if (values.size() != selections.size()) throw new IllegalArgumentException();
            return new Payload(values, selections, names);
        }

        List<String> missingPresets() {
            java.util.Set<String> missing = new java.util.LinkedHashSet<>();
            Map<String, Boolean> known = new LinkedHashMap<>();
            for (WaypointPresetStore.Selection selection : selections) {
                String preset = selection.preset();
                if (!"default".equals(preset) && !known.computeIfAbsent(preset, id -> WaypointPresetStore.find(id) != null)) {
                    missing.add(preset);
                }
            }
            return List.copyOf(missing);
        }

        Payload remapPresets(Map<String, String> replacements) {
            List<WaypointPresetStore.Selection> resolved = new ArrayList<>(selections.size());
            Map<String, WaypointPreset> definitions = new LinkedHashMap<>();
            for (WaypointPresetStore.Selection selection : selections) {
                String id = replacements.getOrDefault(selection.preset(), selection.preset());
                WaypointPreset preset = definitions.computeIfAbsent(id, WaypointPresetStore::find);
                if (!"default".equals(id) && preset == null) throw new IllegalArgumentException();
                String item = preset != null && preset.icon && !preset.pngIcon ? selection.item() : "";
                resolved.add(new WaypointPresetStore.Selection(id, item));
            }
            return new Payload(entries, List.copyOf(resolved), names);
        }
    }
}
