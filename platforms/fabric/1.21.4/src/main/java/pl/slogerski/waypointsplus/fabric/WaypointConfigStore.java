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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WaypointConfigStore {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Waypoint.class, new WaypointGsonAdapter())
            .setPrettyPrinting()
            .create();
    private static final Type WAYPOINT_LIST = new TypeToken<List<Waypoint>>() { }.getType();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] FILE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("waypointsplus");
    private final Path waypointDirectory = directory.resolve("waypoints");
    private final Path waypointFile = directory.resolve("waypoints.json");
    private final Path settingsFile = directory.resolve("settings.json");
    private final Path profilesFile = directory.resolve("profiles.json");
    private List<Waypoint> waypoints = new ArrayList<>();
    private List<Waypoint> legacyWaypoints = new ArrayList<>();
    private Map<String, ServerProfiles> profiles = new LinkedHashMap<>();
    private WaypointSettings settings = new WaypointSettings();
    private PlayerSnapshot playerSnapshot;
    private String loadedServerKey;
    private Path loadedWaypointFile;
    private long lastWaypointCheck;
    private long waypointModified;

    List<Waypoint> waypoints() {
        ensureServerLoaded(ServerScope.current());
        return waypoints;
    }

    WaypointSettings settings() { return settings; }

    void addWaypoint(String name, String serverKey, String dimension, int x, int y, int z, String colorArgb) {
        addWaypointToProfile(name, serverKey, activeProfile(serverKey), dimension, x, y, z, colorArgb);
    }

    void addWaypointToProfile(String name, String serverKey, String profile, String dimension,
                              int x, int y, int z, String colorArgb) {
        ensureServerLoaded(serverKey);
        ServerProfiles state = profileState(serverKey);
        if (!state.names.contains(profile)) {
            state.names.add(profile);
            saveProfiles();
        }
        waypoints.add(new Waypoint(UUID.randomUUID(), name, normalizeServerKey(serverKey), profile,
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
        ensureServerLoaded(serverKey);
        String clean = newName.trim();
        if (clean.isEmpty() || "Default".equals(oldName)) throw new IllegalArgumentException();
        ServerProfiles state = profileState(serverKey);
        int index = state.names.indexOf(oldName);
        if (index < 0 || state.names.stream().anyMatch(existing ->
                !existing.equals(oldName) && existing.equalsIgnoreCase(clean))) throw new IllegalArgumentException();
        state.names.set(index, clean);
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            if (oldName.equals(waypoint.profile())) {
                waypoints.set(i, new Waypoint(waypoint.id(), waypoint.name(), waypoint.serverKey(), clean,
                        waypoint.dimension(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb()));
            }
        }
        saveProfiles();
        saveWaypoints();
    }

    void removeProfile(String serverKey, String name) {
        ensureServerLoaded(serverKey);
        if ("Default".equals(name)) return;
        ServerProfiles state = profileState(serverKey);
        int index = state.names.indexOf(name);
        if (index < 0) return;
        state.names.remove(index);
        if (state.activeIndex > index) state.activeIndex--;
        else if (state.activeIndex == index) state.activeIndex = Math.max(0, index - 1);
        waypoints.removeIf(waypoint -> name.equals(waypoint.profile()));
        saveProfiles();
        saveWaypoints();
    }

    void claimLegacy(String serverKey) {
        if (legacyWaypoints.isEmpty()) return;
        ensureServerLoaded(serverKey);
        String key = normalizeServerKey(serverKey);
        for (Waypoint waypoint : legacyWaypoints) {
            waypoints.add(new Waypoint(waypoint.id(), waypoint.name(), key, waypoint.profile(), waypoint.dimension(),
                    waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb()));
            ServerProfiles state = profileState(key);
            if (!state.names.contains(waypoint.profile())) state.names.add(waypoint.profile());
        }
        legacyWaypoints.clear();
        saveWaypoints();
        saveProfiles();
        saveMetadata();
    }

    void updateWaypoint(Waypoint updated) {
        ensureServerLoaded(updated.serverKey());
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).id().equals(updated.id())) {
                waypoints.set(i, updated);
                saveWaypoints();
                return;
            }
        }
    }

    void removeWaypoint(UUID id) {
        ensureServerLoaded(ServerScope.current());
        if (waypoints.removeIf(waypoint -> waypoint.id().equals(id))) saveWaypoints();
    }

    void load() {
        String reloadServer = loadedServerKey;
        loadedServerKey = null;
        loadedWaypointFile = null;
        waypoints = new ArrayList<>();
        legacyWaypoints = new ArrayList<>();
        try {
            Files.createDirectories(waypointDirectory);
            WaypointProfileRecovery.restoreMissingFileLinks(waypointDirectory, profilesFile);
            try {
                loadSettings();
            } catch (IOException | RuntimeException ignored) {
                settings = new WaypointSettings();
            }
            try {
                loadProfiles();
            } catch (IOException | RuntimeException ignored) {
                profiles = new LinkedHashMap<>();
            }
            List<Waypoint> oldWaypoints;
            try {
                oldWaypoints = readMetadata();
            } catch (IOException | RuntimeException ignored) {
                oldWaypoints = new ArrayList<>();
            }
            migrateLegacyWaypoints(oldWaypoints);
            settings.sanitize();
            normalizeProfiles();
            saveSettings();
            saveProfiles();
            saveMetadata();
            if (reloadServer != null) ensureServerLoaded(reloadServer);
        } catch (IOException | RuntimeException ignored) {
            waypoints = new ArrayList<>();
            legacyWaypoints = new ArrayList<>();
            loadedServerKey = null;
            loadedWaypointFile = null;
        }
    }

    void reloadWaypointsIfChanged() {
        ensureServerLoaded(ServerScope.current());
        long now = System.currentTimeMillis();
        if (now - lastWaypointCheck < 1000L || loadedWaypointFile == null) return;
        lastWaypointCheck = now;
        try {
            long modified = Files.getLastModifiedTime(loadedWaypointFile).toMillis();
            if (modified != waypointModified) reloadCurrentServerFile();
        } catch (IOException | RuntimeException ignored) {
            replaceBrokenServerFile(loadedServerKey);
        }
    }

    void saveSettings() {
        settings.sanitize();
        write(settingsFile, GSON.toJson(settings));
    }

    void reloadWithPlayerPosition(int x, int y, int z) {
        String serverKey = ServerScope.current();
        loadedServerKey = serverKey;
        load();
        ensureServerLoaded(serverKey);
        savePlayerPosition(x, y, z);
    }

    void savePlayerPosition(int x, int y, int z) {
        playerSnapshot = new PlayerSnapshot(x, y, z);
        saveMetadata();
    }

    private void ensureServerLoaded(String serverKey) {
        String key = normalizeServerKey(serverKey);
        if (key.equals(loadedServerKey) && loadedWaypointFile != null) return;
        ServerProfiles state = profileState(key);
        try {
            Files.createDirectories(waypointDirectory);
            Path file = resolveServerFile(state.file);
            if (file == null || !Files.isRegularFile(file)) {
                createFreshServerFile(key);
                return;
            }
            List<Waypoint> loaded = readServerWaypoints(file);
            if (rekeyWaypoints(loaded, key)) writeServerWaypoints(file, loaded);
            loadedServerKey = key;
            loadedWaypointFile = file;
            waypoints = loaded;
            waypointModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException ignored) {
            replaceBrokenServerFile(key);
        }
    }

    private void reloadCurrentServerFile() {
        try {
            List<Waypoint> loaded = readServerWaypoints(loadedWaypointFile);
            waypoints = loaded;
            waypointModified = Files.getLastModifiedTime(loadedWaypointFile).toMillis();
        } catch (IOException | RuntimeException ignored) {
            replaceBrokenServerFile(loadedServerKey);
        }
    }

    private void replaceBrokenServerFile(String serverKey) {
        try {
            createFreshServerFile(normalizeServerKey(serverKey));
        } catch (RuntimeException ignored) {
            waypoints = new ArrayList<>();
            loadedWaypointFile = null;
        }
    }

    private void createFreshServerFile(String serverKey) {
        String key = normalizeServerKey(serverKey);
        ServerProfiles state = profileState(key);
        state.file = newServerFileName();
        loadedServerKey = key;
        loadedWaypointFile = waypointDirectory.resolve(state.file);
        waypoints = new ArrayList<>();
        saveProfiles();
        saveWaypoints();
    }

    private List<Waypoint> readMetadata() throws IOException {
        if (!Files.isRegularFile(waypointFile)) return new ArrayList<>();
        JsonElement root = JsonParser.parseString(Files.readString(waypointFile));
        if (root.isJsonArray()) {
            List<Waypoint> loaded = GSON.fromJson(root, WAYPOINT_LIST);
            return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        }
        JsonObject object = root.getAsJsonObject();
        playerSnapshot = readPlayerSnapshot(object.get("Player"));
        if (!object.has("waypoints")) return new ArrayList<>();
        List<Waypoint> loaded = GSON.fromJson(object.get("waypoints"), WAYPOINT_LIST);
        return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
    }

    private void loadSettings() throws IOException {
        if (!Files.isRegularFile(settingsFile)) return;
        WaypointSettings loaded = GSON.fromJson(Files.readString(settingsFile), WaypointSettings.class);
        if (loaded != null) settings = loaded;
    }

    private void loadProfiles() throws IOException {
        if (!Files.isRegularFile(profilesFile)) return;
        Type type = new TypeToken<Map<String, ServerProfiles>>() { }.getType();
        Map<String, ServerProfiles> loaded = GSON.fromJson(Files.readString(profilesFile), type);
        if (loaded != null) profiles = new LinkedHashMap<>(loaded);
    }

    private void migrateLegacyWaypoints(List<Waypoint> oldWaypoints) {
        Map<String, List<Waypoint>> byServer = new LinkedHashMap<>();
        for (Waypoint waypoint : oldWaypoints) {
            if (waypoint.serverKey() == null || waypoint.serverKey().isBlank()) {
                legacyWaypoints.add(waypoint);
            } else {
                byServer.computeIfAbsent(normalizeServerKey(waypoint.serverKey()), ignored -> new ArrayList<>())
                        .add(waypoint);
            }
        }
        for (Map.Entry<String, List<Waypoint>> entry : byServer.entrySet()) {
            String key = entry.getKey();
            ServerProfiles state = profileState(key);
            List<Waypoint> merged = new ArrayList<>();
            Path file = resolveServerFile(state.file);
            if (file != null && Files.isRegularFile(file)) {
                try {
                    merged.addAll(readServerWaypoints(file));
                } catch (IOException | RuntimeException ignored) {
                    file = null;
                }
            } else {
                file = null;
            }
            if (file == null) {
                state.file = newServerFileName();
                file = waypointDirectory.resolve(state.file);
            }
            for (Waypoint waypoint : entry.getValue()) {
                if (merged.stream().noneMatch(existing -> existing.id().equals(waypoint.id()))) merged.add(waypoint);
                if (!state.names.contains(waypoint.profile())) state.names.add(waypoint.profile());
            }
            writeServerWaypoints(file, merged);
        }
    }

    private static boolean rekeyWaypoints(List<Waypoint> loaded, String serverKey) {
        boolean changed = false;
        for (int i = 0; i < loaded.size(); i++) {
            Waypoint waypoint = loaded.get(i);
            if (serverKey.equals(waypoint.serverKey())) continue;
            loaded.set(i, new Waypoint(waypoint.id(), waypoint.name(), serverKey, waypoint.profile(),
                    waypoint.dimension(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb()));
            changed = true;
        }
        return changed;
    }

    private List<Waypoint> readServerWaypoints(Path file) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(file));
        JsonElement waypointElement;
        if (root.isJsonArray()) waypointElement = root;
        else {
            JsonObject object = root.getAsJsonObject();
            if (!object.has("waypoints") || !object.get("waypoints").isJsonArray()) {
                throw new IOException("Invalid waypoint file");
            }
            waypointElement = object.get("waypoints");
        }
        List<Waypoint> loaded = GSON.fromJson(waypointElement, WAYPOINT_LIST);
        if (loaded == null) throw new IOException("Invalid waypoint list");
        return new ArrayList<>(loaded);
    }

    private void saveWaypoints() {
        if (loadedServerKey == null || loadedWaypointFile == null) {
            ensureServerLoaded(ServerScope.current());
            if (loadedWaypointFile == null) return;
        }
        writeServerWaypoints(loadedWaypointFile, waypoints);
        try {
            waypointModified = Files.getLastModifiedTime(loadedWaypointFile).toMillis();
        } catch (IOException ignored) { }
    }

    private void writeServerWaypoints(Path file, List<Waypoint> values) {
        JsonObject root = new JsonObject();
        root.add("waypoints", GSON.toJsonTree(values, WAYPOINT_LIST));
        write(file, GSON.toJson(root));
    }

    private void saveMetadata() {
        JsonObject root = new JsonObject();
        if (playerSnapshot != null) {
            JsonObject player = new JsonObject();
            player.addProperty("X", playerSnapshot.x());
            player.addProperty("Y", playerSnapshot.y());
            player.addProperty("Z", playerSnapshot.z());
            root.add("Player", player);
        }
        if (!legacyWaypoints.isEmpty()) {
            root.add("waypoints", GSON.toJsonTree(legacyWaypoints, WAYPOINT_LIST));
        }
        write(waypointFile, GSON.toJson(root));
    }

    private void saveProfiles() { write(profilesFile, GSON.toJson(profiles)); }

    private ServerProfiles profileState(String serverKey) {
        String key = normalizeServerKey(serverKey);
        migrateLegacySingleplayerProfile(key);
        ServerProfiles state = profiles.computeIfAbsent(key, ignored -> new ServerProfiles());
        normalizeProfile(state);
        return state;
    }

    private void migrateLegacySingleplayerProfile(String key) {
        if (!key.startsWith("singleplayer:") || !key.equals(ServerScope.current())) return;
        if (profiles.containsKey(key)) return;
        String[] legacyKeys = {"singleplayer:.", ServerScope.legacySingleplayer()};
        for (String legacyKey : legacyKeys) {
            if (legacyKey == null || legacyKey.equals(key)) continue;
            ServerProfiles legacy = profiles.remove(legacyKey);
            if (legacy != null) {
                profiles.put(key, legacy);
                saveProfiles();
                return;
            }
        }
    }

    private void normalizeProfiles() {
        for (ServerProfiles state : profiles.values()) normalizeProfile(state);
    }

    private static void normalizeProfile(ServerProfiles state) {
        if (state.names == null) state.names = new ArrayList<>();
        state.names.removeIf(name -> name == null || name.isBlank());
        if (state.names.isEmpty()) state.names.add("Default");
        state.activeIndex = Math.max(0, Math.min(state.activeIndex, state.names.size() - 1));
    }

    private Path resolveServerFile(String fileName) {
        if (fileName == null || !fileName.matches("[a-z0-9]{16}\\.json")) return null;
        Path resolved = waypointDirectory.resolve(fileName).normalize();
        return waypointDirectory.equals(resolved.getParent()) ? resolved : null;
    }

    private String newServerFileName() {
        for (int attempt = 0; attempt < 1000; attempt++) {
            StringBuilder name = new StringBuilder(21);
            for (int i = 0; i < 16; i++) name.append(FILE_CHARS[RANDOM.nextInt(FILE_CHARS.length)]);
            name.append(".json");
            String candidate = name.toString();
            boolean assigned = profiles.values().stream().anyMatch(state -> candidate.equals(state.file));
            if (!assigned && !Files.exists(waypointDirectory.resolve(candidate))) return candidate;
        }
        throw new IllegalStateException("Could not allocate waypoint file");
    }

    private static String normalizeServerKey(String serverKey) {
        return serverKey == null || serverKey.isBlank() ? "unknown" : serverKey;
    }

    private static final class ServerProfiles {
        List<String> names = new ArrayList<>(List.of("Default"));
        int activeIndex;
        String file;
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
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ignored) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignoredAgain) { }
        }
    }
}
