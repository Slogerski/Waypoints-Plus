package pl.slogerski.waypointsplus.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Restores per-server file links removed by builds predating split waypoint storage.
 */
final class WaypointProfileRecovery {
    private static final String FILE_PATTERN = "[a-z0-9]{16}\\.json";

    private WaypointProfileRecovery() { }

    static void restoreMissingFileLinks(Path waypointDirectory, Path profilesFile) {
        if (!Files.isRegularFile(profilesFile) || !Files.isDirectory(waypointDirectory)) return;

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(profilesFile));
            if (!parsed.isJsonObject()) return;
            JsonObject profiles = parsed.getAsJsonObject();
            Set<String> assignedFiles = assignedFiles(profiles, waypointDirectory);
            Map<String, Candidate> candidates = findCandidates(waypointDirectory, assignedFiles);
            boolean changed = false;

            for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject profile = entry.getValue().getAsJsonObject();
                String current = stringValue(profile.get("file"));
                if (isUsable(current, waypointDirectory)) continue;

                Candidate candidate = candidates.get(entry.getKey());
                if (candidate != null) {
                    profile.addProperty("file", candidate.fileName());
                    changed = true;
                }
            }
            if (changed) writeAtomically(profilesFile, profiles.toString());
        } catch (IOException | RuntimeException ignored) {
            // Recovery is best-effort; normal loading still handles malformed files.
        }
    }

    private static Set<String> assignedFiles(JsonObject profiles, Path directory) {
        Set<String> assigned = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonObject()) continue;
            String file = stringValue(value.getAsJsonObject().get("file"));
            if (isUsable(file, directory)) assigned.add(file);
        }
        return assigned;
    }

    private static Map<String, Candidate> findCandidates(Path directory, Set<String> assigned) throws IOException {
        Map<String, Candidate> candidates = new HashMap<>();
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> inspect(file, assigned, candidates));
        }
        return candidates;
    }

    private static void inspect(Path file, Set<String> assigned, Map<String, Candidate> candidates) {
        String fileName = file.getFileName().toString();
        if (!fileName.matches(FILE_PATTERN) || assigned.contains(fileName)) return;

        try {
            JsonElement root = JsonParser.parseString(Files.readString(file));
            JsonArray waypoints;
            if (root.isJsonArray()) {
                waypoints = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("waypoints")
                    && root.getAsJsonObject().get("waypoints").isJsonArray()) {
                waypoints = root.getAsJsonObject().getAsJsonArray("waypoints");
            } else {
                return;
            }

            String serverKey = null;
            int count = 0;
            for (JsonElement element : waypoints) {
                if (!element.isJsonObject()) continue;
                String waypointServer = stringValue(element.getAsJsonObject().get("serverKey"));
                if (waypointServer == null || waypointServer.isBlank()) continue;
                if (serverKey != null && !serverKey.equals(waypointServer)) return;
                serverKey = waypointServer;
                count++;
            }
            if (serverKey == null || count == 0) return;

            Candidate candidate = new Candidate(fileName, count, Files.getLastModifiedTime(file).toMillis());
            Candidate previous = candidates.get(serverKey);
            if (previous == null || candidate.isBetterThan(previous)) candidates.put(serverKey, candidate);
        } catch (IOException | RuntimeException ignored) {
            // Ignore invalid orphan files; the store will never overwrite them.
        }
    }

    private static boolean isUsable(String fileName, Path directory) {
        return fileName != null && fileName.matches(FILE_PATTERN)
                && Files.isRegularFile(directory.resolve(fileName));
    }

    private static String stringValue(JsonElement element) {
        try {
            return element == null || element.isJsonNull() ? null : element.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void writeAtomically(Path file, String json) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".recovery.tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Candidate(String fileName, int waypointCount, long modified) {
        boolean isBetterThan(Candidate other) {
            return waypointCount > other.waypointCount
                    || (waypointCount == other.waypointCount && modified > other.modified);
        }
    }
}
