package pl.slogerski.waypointsplus.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AlertDataSafety {
    public static final int MAX_FILE_BYTES = 1_048_576;
    public static final int MAX_MESSAGE_LENGTH = 32_768;

    private AlertDataSafety() { }

    public static String read(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Alert file exceeds size limit");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Alert file exceeds size limit");
        String json = new String(bytes, StandardCharsets.UTF_8);
        validateDepth(json);
        return json;
    }

    public static void validateDepth(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '[' || c == '{') {
                if (++depth > 8) throw new IllegalArgumentException("Alert JSON exceeds nesting limit");
            } else if (c == ']' || c == '}') {
                if (--depth < 0) throw new IllegalArgumentException("Unbalanced alert JSON");
            }
        }
        if (depth != 0 || quoted) throw new IllegalArgumentException("Incomplete alert JSON");
    }

    public static void write(Path path, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Alert file exceeds size limit");
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "fight-alerts-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static String truncate(String text, int limit) {
        StringBuilder result = new StringBuilder(Math.min(text.length(), limit));
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0xD800 && cp <= 0xDFFF) continue;
            if (result.length() + Character.charCount(cp) > limit) break;
            result.appendCodePoint(cp);
        }
        return result.toString();
    }

    public static String chatLine(String text, int limit) {
        StringBuilder result = new StringBuilder(Math.min(text.length(), limit));
        boolean space = false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)) {
                space = result.length() > 0;
                continue;
            }
            if (cp == 0xA7 || cp >= 0xD800 && cp <= 0xDFFF) continue;
            int length = Character.charCount(cp) + (space ? 1 : 0);
            if (result.length() + length > limit) break;
            if (space) result.append(' ');
            result.appendCodePoint(cp);
            space = false;
        }
        return result.toString();
    }
}
