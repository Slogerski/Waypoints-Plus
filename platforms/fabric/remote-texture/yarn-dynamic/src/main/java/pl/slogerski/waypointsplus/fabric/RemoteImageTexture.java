package pl.slogerski.waypointsplus.fabric;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import pl.slogerski.waypointsplus.fabric.remote.RemoteImagePolicy;

final class RemoteImageTexture implements AutoCloseable {
    private static final ThreadPoolExecutor DECODER = decoder();

    private final MinecraftClient client = MinecraftClient.getInstance();
    private volatile int generation;
    private Future<?> pending;
    private Identifier identifier;
    private int width;
    private int height;

    void load(Path file) {
        int request = ++generation;
        if (pending != null) pending.cancel(false);
        pending = DECODER.submit(() -> decode(file, request));
    }

    Identifier identifier() {
        return identifier;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    private void decode(Path file, int request) {
        if (request != generation || !RemoteImagePolicy.isSafeFile(file)) return;
        try (InputStream stream = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(stream);
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            if (request != generation || !RemoteImagePolicy.allows(imageWidth, imageHeight)) {
                image.close();
                return;
            }
            try {
                client.execute(() -> install(image, imageWidth, imageHeight, request));
            } catch (RuntimeException exception) {
                image.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void install(NativeImage image, int imageWidth, int imageHeight, int request) {
        if (request != generation) {
            image.close();
            return;
        }
        releaseTexture();
        NativeImageBackedTexture texture = null;
        try {
            texture = new NativeImageBackedTexture(image);
            identifier = client.getTextureManager().registerDynamicTexture("waypointsplus_about_ad", texture);
            width = imageWidth;
            height = imageHeight;
        } catch (RuntimeException exception) {
            if (texture == null) {
                image.close();
            } else {
                texture.close();
            }
        }
    }

    private void releaseTexture() {
        if (identifier == null) {
            return;
        }
        client.getTextureManager().destroyTexture(identifier);
        identifier = null;
        width = 0;
        height = 0;
    }

    @Override
    public void close() {
        generation++;
        if (pending != null) pending.cancel(false);
        releaseTexture();
    }

    private static ThreadPoolExecutor decoder() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "Waypoints Plus image decoder");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }
}
