package pl.slogerski.waypointsplus.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LanguageSettings {
    private static final long RETRY_INTERVAL = 250_000_000L;
    private static final long STARTUP_TIMEOUT = 10_000_000_000L;
    private final Path file;
    private final OgulniegaLanguage api;
    private String mode;
    private String lastLanguage;
    private String ogulniegaLanguage;
    private boolean writable = true;
    private boolean savePending;
    private boolean saveReported;
    private boolean startupComplete;
    private boolean startupStarted;
    private long startupTime;
    private long lastAttempt;

    LanguageSettings(Path file, String legacyLanguage, boolean savedLanguage, OgulniegaLanguage api) {
        this.file = file;
        this.api = api;
        lastLanguage = "pl".equals(legacyLanguage) ? "pl_pl" : "en_us";
        mode = !savedLanguage && api.isPresent() ? "ogulniega" : lastLanguage;
        startupComplete = !api.isPresent();
        try {
            if (Files.notExists(file)) save();
            else load();
        } catch (RuntimeException exception) {
            writable = false;
            mode = lastLanguage;
        }
    }

    String mode() { return mode; }
    String language() { return "pl_pl".equals(lastLanguage) ? "pl" : "en"; }
    boolean isOgulniegaPresent() { return api.isPresent(); }
    boolean isStartupComplete() { return startupComplete; }

    void retryPendingSave() {
        if (savePending) save();
    }

    void cycle() {
        mode = switch (mode) {
            case "pl_pl" -> "en_us";
            case "en_us" -> api.isPresent() ? "ogulniega" : "pl_pl";
            default -> "pl_pl";
        };
        applyMode();
        save();
    }

    void reset() {
        mode = api.isPresent() ? "ogulniega" : "en_us";
        applyMode();
        save();
    }

    private void applyMode() {
        if (!"ogulniega".equals(mode)) lastLanguage = mode;
        else if (ogulniegaLanguage != null) lastLanguage = ogulniegaLanguage;
    }

    boolean readAtStartup(long now) {
        if (startupComplete) return false;
        if (!startupStarted) {
            startupStarted = true;
            startupTime = now;
        } else {
            if (now - startupTime >= STARTUP_TIMEOUT) {
                startupComplete = true;
                return false;
            }
            if (now - lastAttempt < RETRY_INTERVAL) return false;
        }
        lastAttempt = now;
        OgulniegaLanguage.Result result = api.read();
        if (result == OgulniegaLanguage.Result.NOT_READY) return false;
        startupComplete = true;
        return applyResult(result);
    }

    boolean refreshOnOpen() {
        if (!api.isPresent()) return false;
        OgulniegaLanguage.Result result = api.read();
        if (result != OgulniegaLanguage.Result.NOT_READY) startupComplete = true;
        return applyResult(result);
    }

    private boolean applyResult(OgulniegaLanguage.Result result) {
        if (result == OgulniegaLanguage.Result.NOT_READY) return false;
        if (result == OgulniegaLanguage.Result.UNAVAILABLE) return false;
        ogulniegaLanguage = result == OgulniegaLanguage.Result.POLISH ? "pl_pl" : "en_us";
        if (!"ogulniega".equals(mode)) return false;
        boolean changed = !lastLanguage.equals(ogulniegaLanguage);
        lastLanguage = ogulniegaLanguage;
        if (changed) save();
        return changed;
    }

    private void load() {
        try (var input = Files.newInputStream(file)) {
            byte[] data = input.readNBytes(4097);
            if (data.length > 4096) throw new IOException("Language settings exceed size limit");
            JsonObject object = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!object.has("schemaVersion") || !object.get("schemaVersion").isJsonPrimitive()
                    || !object.get("schemaVersion").getAsJsonPrimitive().isNumber()
                    || !"1".equals(object.get("schemaVersion").getAsString())) {
                throw new IOException("Unsupported language settings schema");
            }
            String savedMode = string(object, "mode");
            String savedLanguage = string(object, "lastLanguage");
            if ((!"ogulniega".equals(savedMode) && !localLanguage(savedMode)) || !localLanguage(savedLanguage)
                    || (!"ogulniega".equals(savedMode) && !savedMode.equals(savedLanguage))) {
                throw new IOException("Invalid language settings");
            }
            mode = savedMode;
            lastLanguage = savedLanguage;
        } catch (IOException | RuntimeException exception) {
            writable = false;
            mode = lastLanguage;
        }
    }

    private static String string(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) throw new IOException("Missing language value");
        return object.get(key).getAsString();
    }

    private static boolean localLanguage(String value) {
        return "pl_pl".equals(value) || "en_us".equals(value);
    }

    private void save() {
        if (!writable) return;
        savePending = true;
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "language-", ".tmp");
            JsonObject object = new JsonObject();
            object.addProperty("schemaVersion", 1);
            object.addProperty("mode", mode);
            object.addProperty("lastLanguage", lastLanguage);
            Files.writeString(temporary, object.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            savePending = false;
            saveReported = false;
        } catch (IOException | RuntimeException exception) {
            if (!saveReported) {
                saveReported = true;
                org.slf4j.LoggerFactory.getLogger("Waypoints Plus").warn("Could not save language settings", exception);
            }
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException | RuntimeException ignored) { }
            }
        }
    }
}
