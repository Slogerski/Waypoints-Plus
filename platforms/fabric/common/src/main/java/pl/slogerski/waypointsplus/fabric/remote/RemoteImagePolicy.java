package pl.slogerski.waypointsplus.fabric.remote;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RemoteImagePolicy {
    public static final int MAX_FILE_BYTES = 512 * 1024;
    public static final int MAX_WIDTH = 2_048;
    public static final int MAX_HEIGHT = 1_024;
    public static final long MAX_PIXELS = 294_912L;

    private RemoteImagePolicy() { }

    public static boolean allowsRemoteUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        return host.equals("raw.githubusercontent.com")
                || host.equals("cdn.modrinth.com")
                || host.equals("media.forgecdn.net");
    }

    public static boolean allows(int width, int height) {
        return width >= 16 && height >= 16
                && width <= MAX_WIDTH && height <= MAX_HEIGHT
                && (long) width * height <= MAX_PIXELS;
    }

    public static boolean isSafeFile(Path file) {
        try {
            if (!Files.isRegularFile(file)) return false;
            long size = Files.size(file);
            if (size < 1 || size > MAX_FILE_BYTES) return false;
            try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
                if (input == null) return false;
                java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return false;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    return allows(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }
}
