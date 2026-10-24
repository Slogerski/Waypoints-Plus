package pl.slogerski.waypointsplus.fabric;

import com.ogulniega.OgulniegaModApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class LanguageSettingsCheck {
    private static Path directory;

    public static void main(String[] args) throws IOException {
        directory = Files.createTempDirectory("waypointsplus-language-test-");
        try {
            defaultsAndManualChoices();
            removedAndRestored();
            startupSample();
            delayedStartup();
            windowRefresh();
            startupFailures();
            damagedFiles();
            failedWrite();
            reflectionWithoutMod();
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static LanguageSettings create(String name, String previous, boolean saved, boolean present) {
        OgulniegaModApi.reset();
        return new LanguageSettings(directory.resolve(name + ".json"), previous, saved, new OgulniegaLanguage(present));
    }

    private static void defaultsAndManualChoices() {
        LanguageSettings absent = create("absent", "en", false, false);
        equal("en_us", absent.mode(), "English without Ogulniega");
        require(absent.isStartupComplete(), "No startup polling without Ogulniega");
        absent.cycle();
        equal("pl_pl", absent.mode(), "English to Polish without Ogulniega");
        absent.cycle();
        equal("en_us", absent.mode(), "No unavailable choice in cycle");
        absent.reset();
        equal("en_us", absent.mode(), "Reset without Ogulniega");

        LanguageSettings present = create("present", "en", false, true);
        equal("ogulniega", present.mode(), "Ogulniega for a fresh installation");
        require(OgulniegaModApi.calls == 0, "No API call during initialization");
        present.cycle();
        equal("pl_pl", present.mode(), "Ogulniega to Polish");
        present.cycle();
        equal("en_us", present.mode(), "Polish to English");
        present.cycle();
        equal("ogulniega", present.mode(), "English to Ogulniega");

        LanguageSettings oldPolish = create("old-polish", "pl", true, true);
        equal("pl_pl", oldPolish.mode(), "Existing Polish choice is preserved");
        LanguageSettings oldEnglish = create("old-english", "en", true, true);
        equal("en_us", oldEnglish.mode(), "Existing English choice is preserved");
    }

    private static void removedAndRestored() {
        Path file = directory.resolve("removed.json");
        LanguageSettings first = create("removed", "en", false, true);
        OgulniegaModApi.language = "POLISH";
        require(first.readAtStartup(0), "Initial Polish API sample");
        LanguageSettings absent = new LanguageSettings(file, "en", true, new OgulniegaLanguage(false));
        equal("ogulniega", absent.mode(), "Stored mode survives mod removal");
        equal("pl", absent.language(), "Last language survives mod removal");
        require(!absent.isOgulniegaPresent(), "Mod absence is exposed to UI");
        LanguageSettings returned = new LanguageSettings(file, "en", true, new OgulniegaLanguage(true));
        equal("ogulniega", returned.mode(), "Stored mode survives mod return");
        absent.cycle();
        equal("pl_pl", absent.mode(), "Unavailable Ogulniega cycles to Polish");
        absent.cycle();
        equal("en_us", absent.mode(), "Unavailable Ogulniega is skipped");
        LanguageSettings manual = new LanguageSettings(file, "pl", true, new OgulniegaLanguage(true));
        equal("en_us", manual.mode(), "Manual mode survives mod return");
    }

    private static void startupSample() {
        LanguageSettings settings = create("sample", "en", false, true);
        OgulniegaModApi.language = "POLISH";
        require(settings.readAtStartup(0), "Sample changes the effective language");
        equal("pl", settings.language(), "Polish sample applied");
        require(settings.isStartupComplete(), "Valid sample ends startup");
        OgulniegaModApi.language = "ENGLISH";
        settings.cycle();
        settings.cycle();
        settings.cycle();
        equal("pl", settings.language(), "Reselecting Ogulniega uses the startup sample");
        settings.readAtStartup(30_000_000_000L);
        require(OgulniegaModApi.calls == 1, "No further startup reads after a valid sample");
        settings.reset();
        equal("ogulniega", settings.mode(), "Reset with Ogulniega");
        equal("pl", settings.language(), "Reset uses the cached sample");

        LanguageSettings manual = create("manual-before-ready", "en", false, true);
        manual.cycle();
        OgulniegaModApi.language = "ENGLISH";
        require(!manual.readAtStartup(0), "API does not override a manual choice");
        equal("pl", manual.language(), "Manual language remains effective");
        manual.cycle();
        manual.cycle();
        equal("en", manual.language(), "Manual mode still caches an API sample");
        require(OgulniegaModApi.calls == 1, "Selecting Ogulniega does not call API again");
    }

    private static void delayedStartup() {
        LanguageSettings settings = create("delayed", "en", false, true);
        OgulniegaModApi.language = null;
        require(!settings.readAtStartup(0), "Null language is not ready");
        settings.readAtStartup(249_999_999L);
        require(OgulniegaModApi.calls == 1, "At least 250 ms between attempts");
        OgulniegaModApi.failure = new NullPointerException();
        settings.readAtStartup(250_000_000L);
        require(!settings.isStartupComplete(), "NullPointerException is retryable");
        OgulniegaModApi.failure = null;
        OgulniegaModApi.language = "POLISH";
        require(settings.readAtStartup(500_000_000L), "Delayed settings become ready");
        require(OgulniegaModApi.calls == 3, "Only eligible retries call API");

        LanguageSettings timeout = create("timeout", "pl", false, true);
        OgulniegaModApi.language = null;
        timeout.readAtStartup(0);
        timeout.readAtStartup(10_000_000_000L);
        require(timeout.isStartupComplete(), "Timeout ends startup");
        equal("pl", timeout.language(), "Timeout preserves the last language");
        OgulniegaModApi.language = "ENGLISH";
        timeout.readAtStartup(20_000_000_000L);
        require(OgulniegaModApi.calls == 1, "No API calls after timeout");
    }

    private static void startupFailures() {
        for (int i = 0; i < 3; i++) {
            LanguageSettings settings = create("failure-" + i, "pl", false, true);
            if (i == 0) OgulniegaModApi.language = "UNKNOWN";
            if (i == 1) OgulniegaModApi.failure = new IllegalStateException();
            if (i == 2) OgulniegaModApi.error = new LinkageError();
            settings.readAtStartup(0);
            require(settings.isStartupComplete(), "Unsupported API response ends startup " + i);
            equal("pl", settings.language(), "API failure preserves language " + i);
            settings.readAtStartup(1_000_000_000L);
            require(OgulniegaModApi.calls == 1, "No retry for permanent errors " + i);
        }
    }

    private static void windowRefresh() throws IOException {
        LanguageSettings settings = create("window-refresh", "en", false, true);
        settings.readAtStartup(0);
        OgulniegaModApi.language = "POLISH";
        require(settings.refreshOnOpen(), "Opening a screen applies an updated API language");
        equal("pl", settings.language(), "Updated language is effective immediately");
        Path file = directory.resolve("window-refresh.json");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(0));
        require(!settings.refreshOnOpen(), "An unchanged language does not need applying");
        require(Files.getLastModifiedTime(file).toMillis() == 0, "An unchanged language does not rewrite the file");

        settings.cycle();
        OgulniegaModApi.language = "ENGLISH";
        require(!settings.refreshOnOpen(), "A screen refresh preserves manual language selection");
        equal("pl", settings.language(), "Manual Polish remains selected");
        settings.cycle();
        settings.cycle();
        equal("en", settings.language(), "Ogulniega uses the latest screen-opening sample");
        require(OgulniegaModApi.calls == 4, "Cycling modes does not add API calls");
        OgulniegaModApi.language = null;
        require(!settings.refreshOnOpen(), "An unavailable opening sample is ignored");
        equal("en", settings.language(), "An unavailable opening sample preserves language");
        OgulniegaModApi.failure = new IllegalStateException();
        require(!settings.refreshOnOpen(), "Opening-time API errors are contained");
        equal("en", settings.language(), "Opening-time API errors preserve language");
        OgulniegaModApi.failure = null;
        OgulniegaModApi.language = "POLISH";
        require(settings.refreshOnOpen(), "A later screen opening can recover from an API error");

        LanguageSettings early = create("early-opening", "en", false, true);
        OgulniegaModApi.language = "POLISH";
        require(early.refreshOnOpen(), "A screen can obtain the first valid sample");
        require(early.isStartupComplete(), "A valid opening sample completes initialization");
        early.readAtStartup(0);
        require(OgulniegaModApi.calls == 1, "Startup does not repeat a successful opening read");

        LanguageSettings absent = create("absent-opening", "en", false, false);
        require(!absent.refreshOnOpen(), "Opening without Ogulniega needs no refresh");
        require(OgulniegaModApi.calls == 0, "Opening without Ogulniega does not call API");
    }

    private static void damagedFiles() throws IOException {
        List<String> invalid = List.of("{", "null", "[]", " ".repeat(4097),
                "{\"schemaVersion\":2,\"mode\":\"ogulniega\",\"lastLanguage\":\"pl_pl\"}",
                "{\"schemaVersion\":1,\"mode\":\"ogulniega\",\"lastLanguage\":\"fr_fr\"}",
                "{\"schemaVersion\":1,\"mode\":\"pl_pl\",\"lastLanguage\":\"en_us\"}",
                "{\"schemaVersion\":1,\"mode\":\"ogulniega\"}");
        for (int i = 0; i < invalid.size(); i++) {
            Path file = directory.resolve("invalid-" + i + ".json");
            Files.writeString(file, invalid.get(i));
            LanguageSettings settings = new LanguageSettings(file, "en", false, new OgulniegaLanguage(true));
            equal("en_us", settings.mode(), "Invalid file does not enable automatic language " + i);
            settings.cycle();
            settings.reset();
            settings.retryPendingSave();
            equal(invalid.get(i), Files.readString(file), "Invalid file is preserved " + i);
        }
    }

    private static void failedWrite() throws IOException {
        Path blocker = directory.resolve("blocked");
        Files.writeString(blocker, "file");
        Path file = blocker.resolve("language.json");
        LanguageSettings settings = new LanguageSettings(file, "pl", true, new OgulniegaLanguage(false));
        equal("pl", settings.language(), "Write failure does not prevent language selection");
        Files.delete(blocker);
        Files.createDirectory(blocker);
        settings.retryPendingSave();
        require(Files.isRegularFile(file), "Failed writes can be retried");
        LanguageSettings restored = new LanguageSettings(file, "en", true, new OgulniegaLanguage(false));
        equal("pl", restored.language(), "Retried write persists language");
    }

    private static void reflectionWithoutMod() {
        ClassLoader missing = new ClassLoader(null) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };
        OgulniegaLanguage absent = new OgulniegaLanguage(false, missing);
        require(absent.read() == OgulniegaLanguage.Result.UNAVAILABLE, "Absent mod needs no API class");
        LanguageSettings missingApi = new LanguageSettings(directory.resolve("missing-api.json"), "pl", false,
                new OgulniegaLanguage(true, missing));
        missingApi.readAtStartup(0);
        require(missingApi.isStartupComplete(), "Missing API class ends startup");
        equal("pl", missingApi.language(), "Missing API preserves language");
    }

    private static void equal(String expected, String actual, String scenario) {
        require(expected.equals(actual), scenario + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String scenario) {
        if (!condition) throw new AssertionError(scenario);
    }
}
