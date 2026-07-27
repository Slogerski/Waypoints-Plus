package pl.slogerski.waypointsplus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WaypointConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type WAYPOINT_LIST = new TypeToken<List<Waypoint>>() { }.getType();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("waypointsplus");
    private final Path waypointFile = directory.resolve("waypoints.json");
    private final Path settingsFile = directory.resolve("settings.json");
    private final Path profilesFile = directory.resolve("profiles.json");
    private List<Waypoint> waypoints = new ArrayList<>();
    private Map<String, ServerProfiles> profiles = new LinkedHashMap<>();
    private WaypointSettings settings = new WaypointSettings();
    private PlayerSnapshot playerSnapshot;
    private long lastWaypointCheck;
    private long waypointModified;

    List<Waypoint> waypoints() { return waypoints; }
    WaypointSettings settings() { return settings; }

    void addWaypoint(String name, String serverKey, String dimension, int x, int y, int z, String colorArgb) {
        addWaypointToProfile(name, serverKey, activeProfile(serverKey), dimension, x, y, z, colorArgb);
    }

    void addWaypointToProfile(String name, String serverKey, String profile, String dimension,
                              int x, int y, int z, String colorArgb) {
        ServerProfiles state = profileState(serverKey);
        if (!state.names.contains(profile)) {
            state.names.add(profile);
            saveProfiles();
        }
        waypoints.add(new Waypoint(UUID.randomUUID(), name, serverKey, profile,
                dimension, x, y, z, colorArgb));
        saveWaypoints();
    }

    List<String> profiles(String serverKey) { return List.copyOf(profileState(serverKey).names); }
    String activeProfile(String serverKey) {
        ServerProfiles state = profileState(serverKey);
        return state.names.get(state.activeIndex);
    }
    int activeProfileIndex(String serverKey) { return profileState(serverKey).activeIndex; }
    void selectProfile(String serverKey, int index) {
        ServerProfiles state = profileState(serverKey);
        state.activeIndex = Math.max(0, Math.min(index, state.names.size() - 1));
        saveProfiles();
    }
    void shiftProfile(String serverKey, int delta) {
        ServerProfiles state = profileState(serverKey);
        state.activeIndex = Math.floorMod(state.activeIndex + delta, state.names.size());
        saveProfiles();
    }
    void addProfile(String serverKey, String name) {
        String clean = name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException();
        ServerProfiles state = profileState(serverKey);
        if (state.names.stream().anyMatch(existing -> existing.equalsIgnoreCase(clean))) {
            throw new IllegalArgumentException();
        }
        state.names.add(clean);
        state.activeIndex = state.names.size() - 1;
        saveProfiles();
    }

    void renameProfile(String serverKey, String oldName, String newName) {
        String clean = newName.trim();
        if (clean.isEmpty() || "Default".equals(oldName)) throw new IllegalArgumentException();
        ServerProfiles state = profileState(serverKey);
        int index = state.names.indexOf(oldName);
        if (index < 0 || state.names.stream().anyMatch(existing ->
                !existing.equals(oldName) && existing.equalsIgnoreCase(clean))) throw new IllegalArgumentException();
        state.names.set(index, clean);
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (serverKey.equals(waypoint.serverKey()) && oldName.equals(waypoint.profile())) {
                waypoints.set(i, new Waypoint(waypoint.id(), waypoint.name(), waypoint.serverKey(), clean,
                        waypoint.dimension(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb()));
            }
        }
        saveProfiles();
        saveWaypoints();
    }

    void removeProfile(String serverKey, String name) {
        if ("Default".equals(name)) return;
        ServerProfiles state = profileState(serverKey);
        int index = state.names.indexOf(name);
        if (index < 0) return;
        state.names.remove(index);
        if (state.activeIndex > index) state.activeIndex--;
        else if (state.activeIndex == index) state.activeIndex = Math.max(0, index - 1);
        waypoints.removeIf(waypoint -> serverKey.equals(waypoint.serverKey()) && name.equals(waypoint.profile()));
        saveProfiles();
        saveWaypoints();
    }

    void claimLegacy(String serverKey) {
        boolean changed = false;
        List<Waypoint> migrated = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints) {
            if (waypoint.serverKey() == null || waypoint.serverKey().isBlank()) {
                migrated.add(new Waypoint(waypoint.id(), waypoint.name(), serverKey, waypoint.profile(), waypoint.dimension(),
                        waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb()));
                changed = true;
            } else migrated.add(waypoint);
        }
        if (changed) { waypoints = migrated; saveWaypoints(); }
    }

    void updateWaypoint(Waypoint updated) {
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).id().equals(updated.id())) {
                waypoints.set(i, updated);
                saveWaypoints();
                return;
            }
        }
    }

    void removeWaypoint(UUID id) {
        if (waypoints.removeIf(waypoint -> waypoint.id().equals(id))) saveWaypoints();
    }

    void load() {
        try {
            Files.createDirectories(directory);
            if (Files.exists(waypointFile)) {
                JsonElement root = JsonParser.parseString(Files.readString(waypointFile));
                List<Waypoint> loaded;
                if (root.isJsonArray()) {
                    loaded = GSON.fromJson(root, WAYPOINT_LIST);
                } else {
                    JsonObject object = root.getAsJsonObject();
                    loaded = object.has("waypoints") ? GSON.fromJson(object.get("waypoints"), WAYPOINT_LIST) : null;
                    playerSnapshot = readPlayerSnapshot(object.get("Player"));
                }
                waypoints = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
            } else {
                saveWaypoints();
            }
            if (Files.exists(settingsFile)) {
                WaypointSettings loaded = GSON.fromJson(Files.readString(settingsFile), WaypointSettings.class);
                if (loaded != null) settings = loaded;
            }
            if (Files.exists(profilesFile)) {
                Type type = new TypeToken<Map<String, ServerProfiles>>() { }.getType();
                Map<String, ServerProfiles> loaded = GSON.fromJson(Files.readString(profilesFile), type);
                if (loaded != null) profiles = new LinkedHashMap<>(loaded);
            }
            for (Waypoint waypoint : waypoints) {
                ServerProfiles state = profileState(waypoint.serverKey());
                if (!state.names.contains(waypoint.profile())) state.names.add(waypoint.profile());
            }
            settings.sanitize();
            saveSettings();
            saveProfiles();
            saveWaypoints();
            waypointModified = Files.getLastModifiedTime(waypointFile).toMillis();
        } catch (IOException ignored) {
            waypoints = new ArrayList<>();
        }
    }

    void reloadWaypointsIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastWaypointCheck < 1000L) return;
        lastWaypointCheck = now;
        try {
            long modified = Files.getLastModifiedTime(waypointFile).toMillis();
            if (modified != waypointModified) load();
        } catch (IOException ignored) { }
    }

    void saveSettings() {
        settings.sanitize();
        write(settingsFile, GSON.toJson(settings));
    }

    void reloadWithPlayerPosition(int x, int y, int z) {
        load();
        savePlayerPosition(x, y, z);
    }

    void savePlayerPosition(int x, int y, int z) {
        playerSnapshot = new PlayerSnapshot(x, y, z);
        saveWaypoints();
    }

    private void saveWaypoints() {
        JsonObject root = new JsonObject();
        if (playerSnapshot != null) {
            JsonObject player = new JsonObject();
            player.addProperty("X", playerSnapshot.x());
            player.addProperty("Y", playerSnapshot.y());
            player.addProperty("Z", playerSnapshot.z());
            root.add("Player", player);
        }
        root.add("waypoints", GSON.toJsonTree(waypoints, WAYPOINT_LIST));
        write(waypointFile, GSON.toJson(root));
    }
    private void saveProfiles() { write(profilesFile, GSON.toJson(profiles)); }

    private ServerProfiles profileState(String serverKey) {
        String key = serverKey == null || serverKey.isBlank() ? "unknown" : serverKey;
        ServerProfiles state = profiles.computeIfAbsent(key, ignored -> new ServerProfiles());
        if (state.names == null) state.names = new ArrayList<>();
        state.names.removeIf(name -> name == null || name.isBlank());
        if (state.names.isEmpty()) state.names.add("Default");
        state.activeIndex = Math.max(0, Math.min(state.activeIndex, state.names.size() - 1));
        return state;
    }

    private static final class ServerProfiles {
        List<String> names = new ArrayList<>(List.of("Default"));
        int activeIndex;
    }

    private static PlayerSnapshot readPlayerSnapshot(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        try {
            JsonObject player = element.getAsJsonObject();
            return new PlayerSnapshot(player.get("X").getAsInt(), player.get("Y").getAsInt(), player.get("Z").getAsInt());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record PlayerSnapshot(int x, int y, int z) { }

    private static void write(Path file, String json) {
        try { Files.writeString(file, json, StandardCharsets.UTF_8); } catch (IOException ignored) { }
    }
}
