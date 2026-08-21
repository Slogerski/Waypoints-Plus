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
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WaypointConfigStore {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("Waypoints Plus");
    private static final Set<Path> REPORTED_WRITE_FAILURES = new HashSet<>();
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
    private long waypointRevision;
    private boolean settingsWritable = true;
    private boolean profilesWritable = true;
    private boolean metadataWritable = true;
    private boolean waypointWritable = true;
    private boolean settingsSavePending;
    private boolean profilesSavePending;
    private boolean metadataSavePending;
    private boolean waypointSavePending;
    private long lastPersistenceRetry;
    private boolean lastReadNeededSchemaUpgrade;

    List<Waypoint> waypoints() {
        ensureServerLoaded(ServerScope.current());
        return waypoints;
    }

    WaypointSettings settings() { return settings; }

    long waypointRevision() { return waypointRevision; }

    void addWaypoint(String name, String serverKey, String dimension, int x, int y, int z, String colorArgb) {
        addWaypointToProfile(name, serverKey, activeProfile(serverKey), dimension, x, y, z, colorArgb);
    }

    void addWaypointToProfile(String name, String serverKey, String profile, String dimension,
                              int x, int y, int z, String colorArgb) {
        ensureServerLoaded(serverKey);
        if (!waypointWritable || !profilesWritable) return;
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
        if (!profilesWritable) return;
        ServerProfiles state = profileState(serverKey);
        state.activeIndex = Math.max(0, Math.min(index, state.names.size() - 1));
        saveProfiles();
    }

    void shiftProfile(String serverKey, int delta) {
        if (!profilesWritable) return;
        ServerProfiles state = profileState(serverKey);
        state.activeIndex = Math.floorMod(state.activeIndex + delta, state.names.size());
        saveProfiles();
    }

    void addProfile(String serverKey, String name) {
        if (!profilesWritable) return;
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
        if (!waypointWritable || !profilesWritable) return;
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
        if (!waypointWritable || !profilesWritable) return;
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
        if (!waypointWritable || !profilesWritable || !metadataWritable) return;
        String key = normalizeServerKey(serverKey);
        for (Waypoint waypoint : legacyWaypoints) {
            if (waypoints.stream().noneMatch(existing -> existing.id().equals(waypoint.id()))) {
                waypoints.add(new Waypoint(waypoint.id(), waypoint.name(), key, waypoint.profile(), waypoint.dimension(),
                        waypoint.x(), waypoint.y(), waypoint.z(), waypoint.colorArgb()));
            }
            ServerProfiles state = profileState(key);
            if (!state.names.contains(waypoint.profile())) state.names.add(waypoint.profile());
        }
        if (saveWaypoints() && saveProfiles()) {
            legacyWaypoints.clear();
            saveMetadata();
        }
    }

    void updateWaypoint(Waypoint updated) {
        ensureServerLoaded(updated.serverKey());
        if (!waypointWritable) return;
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
        if (!waypointWritable) return;
        if (waypoints.removeIf(waypoint -> waypoint.id().equals(id))) saveWaypoints();
    }

    void removeWaypoints(java.util.Set<UUID> ids) {
        ensureServerLoaded(ServerScope.current());
        if (!waypointWritable) return;
        if (!ids.isEmpty() && waypoints.removeIf(waypoint -> ids.contains(waypoint.id()))) saveWaypoints();
    }

    int importWaypoints(String serverKey, String profile, List<WaypointTransfer.Entry> imported) {
        ensureServerLoaded(serverKey);
        if (!waypointWritable) return 0;
        java.util.Set<WaypointTransfer.Entry> existingEntries = new java.util.HashSet<>();
        for (Waypoint existing : waypoints) {
            if (profile.equals(existing.profile())) {
                existingEntries.add(new WaypointTransfer.Entry(existing.name(), existing.x(), existing.y(), existing.z(),
                        existing.dimension(), existing.colorArgb().replace("#", "").toUpperCase(java.util.Locale.ROOT)));
            }
        }
        int added = 0;
        for (WaypointTransfer.Entry entry : imported) {
            if (!existingEntries.add(entry)) continue;
            waypoints.add(new Waypoint(UUID.randomUUID(), entry.name(), normalizeServerKey(serverKey), profile,
                    entry.dimension(), entry.x(), entry.y(), entry.z(), entry.colorArgb()));
            added++;
        }
        if (added > 0) saveWaypoints();
        return added;
    }

    boolean addQuickWaypoint(String serverKey, WaypointTransfer.Entry entry) {
        ensureServerLoaded(serverKey);
        if (!waypointWritable) return false;
        String profile = activeProfile(serverKey);
        for (Waypoint existing : waypoints) {
            if (profile.equals(existing.profile()) && entry.name().equals(existing.name())
                    && entry.x() == existing.x() && entry.y() == existing.y() && entry.z() == existing.z()
                    && entry.dimension().equals(existing.dimension())) return false;
        }
        waypoints.add(new Waypoint(UUID.randomUUID(), entry.name(), normalizeServerKey(serverKey), profile,
                entry.dimension(), entry.x(), entry.y(), entry.z(), entry.colorArgb()));
        saveWaypoints();
        return true;
    }

    void load() {
        String reloadServer = loadedServerKey;
        loadedServerKey = null;
        loadedWaypointFile = null;
        waypoints = new ArrayList<>();
        waypointRevision++;
        legacyWaypoints = new ArrayList<>();
        settingsWritable = true;
        profilesWritable = true;
        metadataWritable = true;
        waypointWritable = true;
        try {
            Files.createDirectories(waypointDirectory);
            WaypointProfileRecovery.restoreMissingFileLinks(waypointDirectory, profilesFile);
            try {
                loadSettings();
            } catch (IOException | RuntimeException ignored) {
                settings = new WaypointSettings();
                settingsWritable = false;
            }
            try {
                loadProfiles();
            } catch (IOException | RuntimeException ignored) {
                profiles = new LinkedHashMap<>();
                profilesWritable = false;
            }
            List<Waypoint> oldWaypoints;
            try {
                oldWaypoints = readMetadata();
            } catch (IOException | RuntimeException ignored) {
                oldWaypoints = new ArrayList<>();
                metadataWritable = false;
            }
            if (profilesWritable) migrateLegacyWaypoints(oldWaypoints);
            else legacyWaypoints.addAll(oldWaypoints);
            settings.sanitize();
            normalizeProfiles();
            if (settingsWritable) saveSettings();
            boolean profilesSaved = !profilesWritable || saveProfiles();
            if (metadataWritable && profilesSaved) saveMetadata();
            else if (metadataWritable) metadataSavePending = true;
            if (reloadServer != null) ensureServerLoaded(reloadServer);
        } catch (IOException | RuntimeException ignored) {
            waypoints = new ArrayList<>();
            legacyWaypoints = new ArrayList<>();
            loadedServerKey = null;
            loadedWaypointFile = null;
            settingsWritable = false;
            profilesWritable = false;
            metadataWritable = false;
            waypointWritable = false;
        }
    }

    void reloadWaypointsIfChanged() {
        ensureServerLoaded(ServerScope.current());
        long now = System.currentTimeMillis();
        if (now - lastWaypointCheck < 1000L) return;
        lastWaypointCheck = now;
        retryPendingWrites(now);
        if (loadedWaypointFile == null) return;
        try {
            if (!waypointWritable) {
                reloadCurrentServerFile();
                return;
            }
            long modified = Files.getLastModifiedTime(loadedWaypointFile).toMillis();
            if (modified != waypointModified) reloadCurrentServerFile();
        } catch (IOException | RuntimeException ignored) { }
    }

    void saveSettings() {
        settings.sanitize();
        if (!settingsWritable) return;
        settingsSavePending = !write(settingsFile, GSON.toJson(settings));
    }

    void reloadWithPlayerPosition(int x, int y, int z) {
        String serverKey = ServerScope.current();
        loadedServerKey = serverKey;
        load();
        ensureServerLoaded(serverKey);
        savePlayerPosition(x, y, z);
    }

    void savePlayerPosition(int x, int y, int z) {
        if (!metadataWritable) return;
        playerSnapshot = new PlayerSnapshot(x, y, z);
        saveMetadata();
    }

    private void ensureServerLoaded(String serverKey) {
        String key = normalizeServerKey(serverKey);
        if (key.equals(loadedServerKey) && loadedWaypointFile != null) return;
        if (!profilesWritable) {
            loadedServerKey = key;
            loadedWaypointFile = null;
            waypoints = new ArrayList<>();
            waypointWritable = false;
            waypointRevision++;
            return;
        }
        ServerProfiles state = profileState(key);
        try {
            Files.createDirectories(waypointDirectory);
            Path file = resolveServerFile(state.file);
            if (file == null) {
                createFreshServerFile(key);
                return;
            }
            if (Files.notExists(file)) {
                loadedServerKey = key;
                loadedWaypointFile = file;
                waypoints = new ArrayList<>();
                waypointWritable = true;
                waypointRevision++;
                saveWaypoints();
                return;
            }
            if (!Files.isRegularFile(file)) {
                bindUnreadableServerFile(key, file);
                return;
            }
            List<Waypoint> loaded = readServerWaypoints(file);
            boolean rewritePending = false;
            if (rekeyWaypoints(loaded, key) || lastReadNeededSchemaUpgrade) {
                rewritePending = !writeServerWaypoints(file, loaded);
            }
            loadedServerKey = key;
            loadedWaypointFile = file;
            waypoints = loaded;
            waypointWritable = true;
            waypointSavePending = rewritePending;
            waypointRevision++;
            waypointModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException ignored) {
            bindUnreadableServerFile(key, resolveServerFile(state.file));
        }
    }

    private void reloadCurrentServerFile() {
        try {
            List<Waypoint> loaded = readServerWaypoints(loadedWaypointFile);
            boolean rewritePending = false;
            if (lastReadNeededSchemaUpgrade) {
                rewritePending = !writeServerWaypoints(loadedWaypointFile, loaded);
            }
            waypoints = loaded;
            waypointWritable = true;
            waypointSavePending = rewritePending;
            waypointRevision++;
            waypointModified = Files.getLastModifiedTime(loadedWaypointFile).toMillis();
        } catch (IOException | RuntimeException ignored) {
            waypointWritable = false;
        }
    }

    private void bindUnreadableServerFile(String serverKey, Path file) {
        loadedServerKey = normalizeServerKey(serverKey);
        loadedWaypointFile = file;
        waypoints = new ArrayList<>();
        waypointWritable = false;
        waypointRevision++;
    }

    private void createFreshServerFile(String serverKey) {
        String key = normalizeServerKey(serverKey);
        ServerProfiles state = profileState(key);
        state.file = newServerFileName();
        loadedServerKey = key;
        loadedWaypointFile = waypointDirectory.resolve(state.file);
        waypoints = new ArrayList<>();
        waypointWritable = true;
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
        requireSupportedSchema(object);
        playerSnapshot = readPlayerSnapshot(object.get("Player"));
        if (!object.has("waypoints")) return new ArrayList<>();
        List<Waypoint> loaded = GSON.fromJson(object.get("waypoints"), WAYPOINT_LIST);
        return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
    }

    private void loadSettings() throws IOException {
        if (!Files.isRegularFile(settingsFile)) return;
        WaypointSettings loaded = GSON.fromJson(Files.readString(settingsFile), WaypointSettings.class);
        if (loaded != null) {
            if (loaded.schemaVersion > CURRENT_SCHEMA_VERSION) throw new IOException("Unsupported settings schema");
            settings = loaded;
        }
    }

    private void loadProfiles() throws IOException {
        if (!Files.isRegularFile(profilesFile)) return;
        Type type = new TypeToken<Map<String, ServerProfiles>>() { }.getType();
        Map<String, ServerProfiles> loaded = GSON.fromJson(Files.readString(profilesFile), type);
        if (loaded != null) {
            for (ServerProfiles state : loaded.values()) {
                if (state != null && state.schemaVersion > CURRENT_SCHEMA_VERSION) {
                    throw new IOException("Unsupported profiles schema");
                }
            }
            profiles = new LinkedHashMap<>(loaded);
        }
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
                    legacyWaypoints.addAll(entry.getValue());
                    continue;
                }
            } else {
                file = null;
            }
            String previousFile = state.file;
            if (file == null) {
                state.file = newServerFileName();
                file = waypointDirectory.resolve(state.file);
            }
            for (Waypoint waypoint : entry.getValue()) {
                if (merged.stream().noneMatch(existing -> existing.id().equals(waypoint.id()))) merged.add(waypoint);
                if (!state.names.contains(waypoint.profile())) state.names.add(waypoint.profile());
            }
            if (!writeServerWaypoints(file, merged)) {
                state.file = previousFile;
                legacyWaypoints.addAll(entry.getValue());
            }
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
        lastReadNeededSchemaUpgrade = root.isJsonArray();
        JsonElement waypointElement;
        if (root.isJsonArray()) waypointElement = root;
        else {
            JsonObject object = root.getAsJsonObject();
            requireSupportedSchema(object);
            lastReadNeededSchemaUpgrade = !object.has("schemaVersion");
            if (!object.has("waypoints") || !object.get("waypoints").isJsonArray()) {
                throw new IOException("Invalid waypoint file");
            }
            waypointElement = object.get("waypoints");
        }
        List<Waypoint> loaded = GSON.fromJson(waypointElement, WAYPOINT_LIST);
        if (loaded == null) throw new IOException("Invalid waypoint list");
        return new ArrayList<>(loaded);
    }

    private boolean saveWaypoints() {
        if (loadedServerKey == null || loadedWaypointFile == null) {
            ensureServerLoaded(ServerScope.current());
            if (loadedWaypointFile == null) return false;
        }
        if (!waypointWritable) return false;
        waypointSavePending = !writeServerWaypoints(loadedWaypointFile, waypoints);
        waypointRevision++;
        if (waypointSavePending) return false;
        try {
            waypointModified = Files.getLastModifiedTime(loadedWaypointFile).toMillis();
        } catch (IOException ignored) { }
        return true;
    }

    private boolean writeServerWaypoints(Path file, List<Waypoint> values) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
        root.add("waypoints", GSON.toJsonTree(values, WAYPOINT_LIST));
        return write(file, GSON.toJson(root));
    }

    private void saveMetadata() {
        if (!metadataWritable) return;
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
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
        metadataSavePending = !write(waypointFile, GSON.toJson(root));
    }

    private boolean saveProfiles() {
        if (!profilesWritable) return false;
        profilesSavePending = !write(profilesFile, GSON.toJson(profiles));
        return !profilesSavePending;
    }

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
        if (state.schemaVersion <= 0) state.schemaVersion = CURRENT_SCHEMA_VERSION;
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
        int schemaVersion = CURRENT_SCHEMA_VERSION;
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

    private void retryPendingWrites(long now) {
        if (now - lastPersistenceRetry < 1000L) return;
        lastPersistenceRetry = now;
        if (settingsSavePending && settingsWritable) saveSettings();
        if (profilesSavePending && profilesWritable) saveProfiles();
        if (metadataSavePending && metadataWritable && !profilesSavePending) saveMetadata();
        if (waypointSavePending && waypointWritable && loadedWaypointFile != null) saveWaypoints();
    }

    private static void requireSupportedSchema(JsonObject object) throws IOException {
        if (!object.has("schemaVersion")) return;
        try {
            int version = object.get("schemaVersion").getAsInt();
            if (version > CURRENT_SCHEMA_VERSION) throw new IOException("Unsupported data schema");
        } catch (RuntimeException exception) {
            throw new IOException("Invalid data schema", exception);
        }
    }

    private static boolean write(Path file, String json) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            synchronized (REPORTED_WRITE_FAILURES) {
                REPORTED_WRITE_FAILURES.remove(file);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignoredAgain) { }
            }
            synchronized (REPORTED_WRITE_FAILURES) {
                if (REPORTED_WRITE_FAILURES.add(file)) {
                    LOGGER.error("Could not save Waypoints Plus data to {}", file, exception);
                }
            }
            return false;
        }
    }
}
