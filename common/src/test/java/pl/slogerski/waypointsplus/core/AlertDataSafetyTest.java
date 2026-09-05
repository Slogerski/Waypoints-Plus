package pl.slogerski.waypointsplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AlertDataSafetyTest {
    @TempDir Path directory;

    @Test void chatUsesOneLineAndPreservesUnicode() {
        String text = "  Hello\r\n\tworld\u00a0\u2028\u0000😀 §!  ";
        assertEquals("Hello world 😀 !", AlertDataSafety.chatLine(text, 256));
        assertEquals("abc", AlertDataSafety.chatLine("abc😀end", 4));
        assertEquals("abc😀", AlertDataSafety.chatLine("abc😀end", 5));
        assertEquals("ab", AlertDataSafety.chatLine("a\uD800b\uDC00", 256));
    }

    @Test void signedCommandSurvivesUtf8EncodingAtEveryBoundary() {
        String text = "a😀b\nżółć𐐀".repeat(40);
        for (int limit = 1; limit <= 256; limit++) {
            String result = AlertDataSafety.chatLine(text, limit);
            assertTrue(result.length() <= limit);
            assertEquals(result, new String(result.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            String discord = AlertDataSafety.truncate(text, limit);
            assertEquals(discord, new String(discord.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        }
    }

    @Test void deeplyNestedAndOversizedFilesAreRejected() throws IOException {
        Path file = directory.resolve("alerts.json");
        Files.writeString(file, "[".repeat(10000) + "]".repeat(10000));
        assertThrows(IllegalArgumentException.class, () -> AlertDataSafety.read(file));
        Files.write(file, new byte[AlertDataSafety.MAX_FILE_BYTES + 1]);
        assertThrows(IOException.class, () -> AlertDataSafety.read(file));
    }

    @Test void bracesAndEscapesInsideMessagesDoNotCountAsNesting() {
        assertDoesNotThrow(() -> AlertDataSafety.validateDepth("[{\"message\":\"\\\"[[[[[[[[[[{{{{{{\\\\\"}]"));
        assertThrows(IllegalArgumentException.class, () -> AlertDataSafety.validateDepth("[{\"message\":\"unfinished"));
    }

    @Test void failedWritePreservesOriginalData() throws IOException {
        Path file = directory.resolve("alerts.json");
        AlertDataSafety.write(file, "[]");
        assertThrows(IOException.class, () -> AlertDataSafety.write(file, "x".repeat(AlertDataSafety.MAX_FILE_BYTES + 1)));
        assertEquals("[]", Files.readString(file));
        Path blocker = directory.resolve("not-a-directory");
        Files.writeString(blocker, "keep");
        assertThrows(IOException.class, () -> AlertDataSafety.write(blocker.resolve("alerts.json"), "[]"));
        assertEquals("keep", Files.readString(blocker));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
