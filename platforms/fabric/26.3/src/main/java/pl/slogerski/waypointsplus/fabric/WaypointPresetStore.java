package pl.slogerski.waypointsplus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class WaypointPresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("waypointsplus/presets.json");
    private static final int MAX_FILE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_PRESETS = 66;
    private static final List<WaypointPreset> PRESETS = new ArrayList<>();
    private static final Map<UUID, String> ASSIGNMENTS = new HashMap<>();
    private static final Map<UUID, String> ITEMS = new HashMap<>();
    private static long revision;
    private static long layoutRevision;
    private static boolean loaded;
    private static boolean writable = true;

    private WaypointPresetStore() { }

    static long revision() { return revision; }
    static long layoutRevision() { return layoutRevision; }

    static void initialize() { load(); }

    static String selected(UUID waypoint) {
        load();
        return ASSIGNMENTS.getOrDefault(waypoint, "default");
    }

    static WaypointPreset find(String id) {
        for (WaypointPreset preset : PRESETS) if (preset.id.equals(id)) return copy(preset);
        return null;
    }

    static boolean assign(UUID waypoint, String preset) {
        return assign(waypoint, preset, selected(waypoint).equals(preset) ? item(waypoint) : "");
    }

    static String item(UUID waypoint) {
        load();
        return ITEMS.getOrDefault(waypoint, "");
    }

    static boolean assign(UUID waypoint, String preset, String item) {
        if (waypoint == null) return false;
        return assignAll(Map.of(waypoint, new Selection(preset, item)));
    }

    static boolean assignAll(Map<UUID, Selection> selections) {
        load();
        if (selections.isEmpty()) return true;
        if (!writable || selections.size() > 32000) return false;
        Map<String, WaypointPreset> definitions = new HashMap<>();
        for (WaypointPreset preset : PRESETS) definitions.put(preset.id, preset);
        Map<UUID, String> next = new HashMap<>(ASSIGNMENTS);
        Map<UUID, String> items = new HashMap<>(ITEMS);
        for (var entry : selections.entrySet()) {
            UUID waypoint = entry.getKey();
            Selection selection = entry.getValue();
            if (waypoint == null || selection == null || selection.preset == null) return false;
            String preset = selection.preset, item = selection.item;
            WaypointPreset definition = definitions.get(preset);
            if (!"default".equals(preset) && definition == null) return false;
            if (definition == null || !definition.icon || definition.pngIcon) item = "";
            if (item == null || (!item.isEmpty() && !validItem(item))) return false;
            if ("default".equals(preset)) next.remove(waypoint);
            else next.put(waypoint, preset);
            if (item.isEmpty()) items.remove(waypoint);
            else items.put(waypoint, item);
        }
        if (next.size() > 32000) return false;
        if (next.equals(ASSIGNMENTS) && items.equals(ITEMS)) return true;
        return write(PRESETS, next, items);
    }

    static void forget(Set<UUID> waypoints) {
        if (waypoints.isEmpty() || !writable) return;
        Map<UUID, String> next = new HashMap<>(ASSIGNMENTS);
        if (next.keySet().removeAll(waypoints)) write(PRESETS, next);
    }

    static List<WaypointPreset> list() {
        load();
        List<WaypointPreset> result = new ArrayList<>();
        WaypointPreset defaults = WaypointPreset.classic();
        defaults.id = "default";
        defaults.name = "Default";
        defaults.favorite = WaypointsPlusClient.config().settings().defaultPresetFavorite;
        result.add(defaults);
        WaypointPreset singleIcon = find(WaypointPreset.SINGLE_ICON_ID);
        if (singleIcon != null) result.add(singleIcon);
        WaypointPreset designed = find(WaypointPreset.DESIGNED_ID);
        if (designed != null) result.add(designed);
        PRESETS.stream().filter(preset -> !WaypointPreset.builtIn(preset.id))
                .sorted(Comparator.comparing((WaypointPreset preset) -> !preset.favorite)
                .thenComparing(preset -> preset.name, String.CASE_INSENSITIVE_ORDER)).map(WaypointPresetStore::copy).forEach(result::add);
        result.sort(Comparator.comparing((WaypointPreset preset) -> !preset.favorite));
        return result;
    }

    static boolean canCreate() {
        load();
        return writable && PRESETS.size() < MAX_PRESETS;
    }

    static boolean save(WaypointPreset preset) {
        load();
        if (!writable || !preset.valid() || WaypointPreset.builtIn(preset.id)) return false;
        List<WaypointPreset> next = new ArrayList<>(PRESETS);
        int index = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(preset.id)) index = i;
        if (index < 0) {
            if (next.size() >= MAX_PRESETS) return false;
            next.add(copy(preset));
        } else next.set(index, copy(preset));
        boolean saved = write(next, ASSIGNMENTS);
        if (saved) layoutRevision++;
        return saved;
    }

    static boolean remove(String id) {
        load();
        if (!writable || WaypointPreset.builtIn(id)) return false;
        List<WaypointPreset> next = new ArrayList<>(PRESETS);
        if (!next.removeIf(preset -> preset.id.equals(id))) return false;
        Map<UUID, String> assignments = new HashMap<>(ASSIGNMENTS);
        assignments.values().removeIf(id::equals);
        boolean saved = write(next, assignments);
        if (saved) layoutRevision++;
        return saved;
    }

    static boolean favorite(String id, boolean value) {
        load();
        for (WaypointPreset preset : PRESETS) {
            if (preset.id.equals(id)) {
                WaypointPreset changed = copy(preset);
                changed.favorite = value;
                List<WaypointPreset> next = new ArrayList<>(PRESETS);
                next.set(next.indexOf(preset), changed);
                return write(next, ASSIGNMENTS);
            }
        }
        return false;
    }

    static WaypointPreset copy(WaypointPreset preset) {
        return GSON.fromJson(GSON.toJson(preset), WaypointPreset.class);
    }

    static String exportPreset(WaypointPreset preset) {
        if (!preset.valid()) throw new IllegalArgumentException();
        JsonObject root = new JsonObject();
        root.addProperty("format", "waypointsplus-preset");
        root.addProperty("schemaVersion", 1);
        root.add("preset", GSON.toJsonTree(preset));
        return GSON.toJson(root);
    }

    static WaypointPreset importPreset(String json) {
        if (json == null || json.length() > 65536 || !shallowJson(json)) throw new IllegalArgumentException();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("format") || !"waypointsplus-preset".equals(root.get("format").getAsString())
                || !root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException();
        WaypointPreset preset = GSON.fromJson(root.get("preset"), WaypointPreset.class);
        if (preset == null || !preset.valid()) throw new IllegalArgumentException();
        validateImage(preset);
        preset.id = UUID.randomUUID().toString();
        preset.favorite = false;
        return preset;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        PRESETS.add(WaypointPreset.singleIcon());
        PRESETS.add(WaypointPreset.designed());
        if (!Files.exists(FILE)) return;
        try {
            if (Files.size(FILE) > MAX_FILE_SIZE) throw new IllegalArgumentException();
            byte[] bytes;
            try (var stream = Files.newInputStream(FILE)) {
                bytes = stream.readNBytes(MAX_FILE_SIZE + 1);
            }
            if (bytes.length > MAX_FILE_SIZE) throw new IllegalArgumentException();
            String json = new String(bytes, StandardCharsets.UTF_8);
            if (!shallowJson(json)) throw new IllegalArgumentException();
            StoredPresets stored = GSON.fromJson(json, StoredPresets.class);
            if (stored == null || stored.schemaVersion != 1 || stored.presets == null
                    || stored.presets.size() > MAX_PRESETS) throw new IllegalArgumentException();
            HashSet<String> ids = new HashSet<>();
            for (WaypointPreset preset : stored.presets) {
                if (preset == null || !preset.valid() || "default".equals(preset.id) || !ids.add(preset.id)) {
                    throw new IllegalArgumentException();
                }
                validateImage(preset);
            }
            PRESETS.clear();
            PRESETS.addAll(stored.presets);
            for (int i = 0; i < PRESETS.size(); i++) {
                WaypointPreset preset = PRESETS.get(i);
                if (WaypointPreset.builtIn(preset.id)) {
                    WaypointPreset current = WaypointPreset.SINGLE_ICON_ID.equals(preset.id)
                            ? WaypointPreset.singleIcon() : WaypointPreset.designed();
                    current.favorite = preset.favorite;
                    PRESETS.set(i, current);
                }
            }
            if (!ids.contains(WaypointPreset.SINGLE_ICON_ID)) {
                if (PRESETS.size() >= MAX_PRESETS) throw new IllegalArgumentException();
                PRESETS.add(WaypointPreset.singleIcon());
                ids.add(WaypointPreset.SINGLE_ICON_ID);
            }
            if (!ids.contains(WaypointPreset.DESIGNED_ID)) {
                if (PRESETS.size() >= MAX_PRESETS) throw new IllegalArgumentException();
                PRESETS.add(WaypointPreset.designed());
                ids.add(WaypointPreset.DESIGNED_ID);
            }
            if (stored.assignments != null) {
                if (stored.assignments.size() > 32000) throw new IllegalArgumentException();
                for (var entry : stored.assignments.entrySet()) {
                    UUID waypoint = UUID.fromString(entry.getKey());
                    if (ids.contains(entry.getValue())) ASSIGNMENTS.put(waypoint, entry.getValue());
                }
            }
            if (stored.items != null) {
                if (stored.items.size() > 32000) throw new IllegalArgumentException();
                for (var entry : stored.items.entrySet()) {
                    UUID waypoint = UUID.fromString(entry.getKey());
                    String presetId = ASSIGNMENTS.get(waypoint);
                    boolean usesItem = PRESETS.stream().anyMatch(preset -> preset.id.equals(presetId) && preset.icon && !preset.pngIcon);
                    if (usesItem && validItem(entry.getValue())) ITEMS.put(waypoint, entry.getValue());
                }
            }
        } catch (IOException | RuntimeException exception) {
            PRESETS.clear();
            PRESETS.add(WaypointPreset.singleIcon());
            PRESETS.add(WaypointPreset.designed());
            ASSIGNMENTS.clear();
            ITEMS.clear();
            writable = false;
            LoggerFactory.getLogger("waypointsplus").warn("Cannot read presets.json; writes blocked", exception);
        }
    }

    private static boolean write(List<WaypointPreset> presets, Map<UUID, String> assignments) {
        return write(presets, assignments, ITEMS);
    }

    static boolean validItem(String item) {
        Identifier id = item == null || item.length() > 256 ? null : Identifier.tryParse(item);
        return id != null && !"minecraft:air".equals(id.toString()) && BuiltInRegistries.ITEM.containsKey(id);
    }

    private static boolean write(List<WaypointPreset> presets, Map<UUID, String> assignments, Map<UUID, String> items) {
        Path temporary = FILE.resolveSibling("presets-" + UUID.randomUUID() + ".tmp");
        try {
            Map<String, String> serialized = new java.util.TreeMap<>();
            assignments.forEach((id, preset) -> serialized.put(id.toString(), preset));
            Map<UUID, String> savedItems = new HashMap<>(items);
            Set<String> itemPresets = new HashSet<>();
            for (WaypointPreset preset : presets) if (preset.icon && !preset.pngIcon) itemPresets.add(preset.id);
            savedItems.keySet().removeIf(id -> !itemPresets.contains(assignments.get(id)));
            Map<String, String> serializedItems = new java.util.TreeMap<>();
            savedItems.forEach((id, item) -> serializedItems.put(id.toString(), item));
            String json = GSON.toJson(new StoredPresets(1, presets, serialized, serializedItems));
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE) return false;
            Files.createDirectories(FILE.getParent());
            Files.writeString(temporary, json);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            List<WaypointPreset> saved = new ArrayList<>(presets);
            Map<UUID, String> savedAssignments = new HashMap<>(assignments);
            PRESETS.clear();
            PRESETS.addAll(saved);
            ASSIGNMENTS.clear();
            ASSIGNMENTS.putAll(savedAssignments);
            ITEMS.clear();
            ITEMS.putAll(savedItems);
            revision++;
            return true;
        } catch (IOException | RuntimeException exception) {
            LoggerFactory.getLogger("waypointsplus").warn("Cannot save presets.json", exception);
            return false;
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private static void validateImage(WaypointPreset preset) {
        if (preset.png.isEmpty()) return;
        try (NativeImage image = NativeImage.read(Base64.getDecoder().decode(preset.png))) {
            int size = image.getWidth();
            if (size != image.getHeight() || (size != 16 && size != 32 && size != 64)) throw new IllegalArgumentException();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid preset PNG", exception);
        }
    }

    private static boolean shallowJson(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') quoted = true;
            else if (character == '{' || character == '[') { if (++depth > 12) return false; }
            else if (character == '}' || character == ']') { if (--depth < 0) return false; }
        }
        return depth == 0 && !quoted;
    }

    private static final class StoredPresets {
        private int schemaVersion;
        private List<WaypointPreset> presets;
        private Map<String, String> assignments;
        private Map<String, String> items;

        private StoredPresets() { }

        private StoredPresets(int schemaVersion, List<WaypointPreset> presets,
                              Map<String, String> assignments, Map<String, String> items) {
            this.schemaVersion = schemaVersion;
            this.presets = presets;
            this.assignments = assignments;
            this.items = items;
        }
    }

    record Selection(String preset, String item) { }
}
