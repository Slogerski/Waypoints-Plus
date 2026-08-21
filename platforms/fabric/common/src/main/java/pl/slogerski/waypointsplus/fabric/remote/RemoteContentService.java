package pl.slogerski.waypointsplus.fabric.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RemoteContentService {
    private static final URI ROOT = URI.create(
            "https://raw.githubusercontent.com/Slogerski/Waypoints-Plus/main/remote/");
    private static final long TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long BACKOFF_MIN_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final long BACKOFF_MAX_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final int JSON_LIMIT = 32 * 1024;
    private static final int IMAGE_LIMIT = RemoteImagePolicy.MAX_FILE_BYTES;
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(3, daemonFactory());
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .executor(NETWORK)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Path cacheDirectory;
    private final HttpClient client;
    private final Map<String, CompletableFuture<?>> inFlight = new HashMap<>();
    private final Object imageStateLock = new Object();
    private long latestImageGeneration;
    private String latestImageUrl = "";

    public RemoteContentService(Path cacheDirectory) {
        this(cacheDirectory, CLIENT);
    }

    RemoteContentService(Path cacheDirectory, HttpClient client) {
        this.cacheDirectory = cacheDirectory.toAbsolutePath().normalize();
        this.client = client;
    }

    public static RemoteContentService getDefault() {
        return DefaultHolder.INSTANCE;
    }

    public static RemoteContentService createDefault() {
        return getDefault();
    }

    public RemoteContentSession<RemoteLinks> openLinks() {
        return openJson("links", "links.json", RemoteLinks.FALLBACK, this::parseLinks);
    }

    public RemoteContentSession<RemoteTopDonate> openTopDonate() {
        return openJson("top-donate", "top-donate.json", RemoteTopDonate.EMPTY, this::parseTopDonate);
    }

    public RemoteContentSession<RemoteAboutAdSnapshot> openAboutAd() {
        Optional<RemoteAboutAd> cached = readJson("about-ad", this::parseAboutAd);
        RemoteAboutAd cachedAd = cached.orElse(RemoteAboutAd.FALLBACK);
        RemoteAboutAdSnapshot snapshot = snapshot(cachedAd, readImage(cachedAd));
        CompletableFuture<RemoteAboutAdSnapshot> refresh = refreshJson("about-ad", "about-ad.json", cachedAd,
                cached.isPresent(), this::parseAboutAd)
                .thenCompose(ad -> refreshImage(ad).thenApply(image -> snapshot(ad, image)))
                .exceptionally(error -> snapshot);
        return new RemoteContentSession<>(snapshot, refresh);
    }

    private <T> RemoteContentSession<T> openJson(String cacheName, String endpoint, T fallback,
                                                   Function<JsonObject, T> parser) {
        Optional<T> cached = readJson(cacheName, parser);
        T snapshot = cached.orElse(fallback);
        return new RemoteContentSession<>(snapshot,
                refreshJson(cacheName, endpoint, snapshot, cached.isPresent(), parser).exceptionally(error -> snapshot));
    }

    private <T> CompletableFuture<T> refreshJson(String cacheName, String endpoint, T current, boolean hasValidCache,
                                                   Function<JsonObject, T> parser) {
        CacheMeta meta = readMeta(cacheName);
        long now = System.currentTimeMillis();
        if (now < meta.nextAttempt || hasValidCache
                && meta.checkedAt > 0 && now - meta.checkedAt < TTL_MILLIS) {
            return CompletableFuture.completedFuture(current);
        }
        String etag = hasValidCache ? meta.etag : "";
        return sharedRefresh("json:" + cacheName, () -> request(ROOT.resolve(endpoint), etag, JSON_LIMIT)
                .thenApply(response -> {
                    if (response.status == 304) {
                        if (!hasValidCache) {
                            throw new IllegalStateException("HTTP 304 without a valid cache");
                        }
                        writeMeta(cacheName, meta.touch(now));
                        return current;
                    }
                    if (response.status != 200) {
                        throw new IllegalStateException("HTTP " + response.status);
                    }
                    T parsed = parser.apply(jsonObject(response.body));
                    writeBytes(dataFile(cacheName, ".json"), response.body);
                    writeMeta(cacheName, CacheMeta.success(response.etag, now, null));
                    return parsed;
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        writeMeta(cacheName, readMeta(cacheName).failure(System.currentTimeMillis()));
                    }
                }));
    }

    private CompletableFuture<Optional<RemoteImage>> refreshImage(RemoteAboutAd ad) {
        if (ad == null || ad.imageUrl().isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        URI imageUri = URI.create(ad.imageUrl());
        if (!RemoteImagePolicy.allowsRemoteUri(imageUri)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String name = "about-ad-image";
        CacheMeta meta = readMeta(name);
        if (!ad.imageUrl().equals(meta.sourceUrl)) {
            meta = CacheMeta.EMPTY;
        }
        CacheMeta requestMeta = meta;
        long now = System.currentTimeMillis();
        Optional<RemoteImage> current = readImage(ad);
        if (now < requestMeta.nextAttempt || current.isPresent()
                && requestMeta.checkedAt > 0 && now - requestMeta.checkedAt < TTL_MILLIS) {
            return CompletableFuture.completedFuture(current);
        }
        String etag = current.isPresent() ? requestMeta.etag : "";
        return sharedRefresh("image:" + ad.imageUrl(),
                () -> {
                    ImageRequest imageRequest = beginImageRequest(ad.imageUrl());
                    return request(imageUri, etag, IMAGE_LIMIT)
                        .thenApply(response -> {
                            if (!isLatest(imageRequest)) {
                                return readImage(ad);
                            }
                            if (response.status == 304 && current.isPresent()) {
                                return touchImageIfLatest(imageRequest, name, requestMeta, now, ad);
                            }
                            if (response.status != 200) {
                                throw new IllegalStateException("HTTP " + response.status);
                            }
                            ImageData image = verifyImage(response.body);
                            String extension = image.jpeg ? ".jpg" : ".png";
                            return writeImageIfLatest(imageRequest, name, ad, response, image, extension, now);
                        })
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                recordImageFailureIfLatest(imageRequest, name);
                            }
                        })
                        .exceptionally(error -> isLatest(imageRequest) ? current : readImage(ad));
                })
                .exceptionally(error -> current);
    }

    private ImageRequest beginImageRequest(String imageUrl) {
        synchronized (imageStateLock) {
            latestImageUrl = imageUrl;
            latestImageGeneration++;
            return new ImageRequest(latestImageGeneration, imageUrl);
        }
    }

    private boolean isLatest(ImageRequest imageRequest) {
        synchronized (imageStateLock) {
            return latestImageGeneration == imageRequest.generation && latestImageUrl.equals(imageRequest.url);
        }
    }

    private Optional<RemoteImage> touchImageIfLatest(ImageRequest imageRequest, String name, CacheMeta meta,
                                                       long now, RemoteAboutAd ad) {
        synchronized (imageStateLock) {
            if (!isLatestLocked(imageRequest)) {
                return readImage(ad);
            }
            writeMeta(name, meta.touch(now));
            return readImage(ad);
        }
    }

    private Optional<RemoteImage> writeImageIfLatest(ImageRequest imageRequest, String name, RemoteAboutAd ad,
                                                       Response response, ImageData image, String extension, long now) {
        synchronized (imageStateLock) {
            if (!isLatestLocked(imageRequest)) {
                return readImage(ad);
            }
            writeBytes(dataFile(name, extension), response.body);
            deleteQuietly(dataFile(name, image.jpeg ? ".png" : ".jpg"));
            writeMeta(name, CacheMeta.imageSuccess(response.etag, now, ad.imageUrl(),
                    image.width, image.height, extension));
            return Optional.of(new RemoteImage(dataFile(name, extension), image.width, image.height, now));
        }
    }

    private void recordImageFailureIfLatest(ImageRequest imageRequest, String name) {
        synchronized (imageStateLock) {
            if (isLatestLocked(imageRequest)) {
                CacheMeta meta = readMeta(name);
                if (!imageRequest.url.equals(meta.sourceUrl)) {
                    meta = CacheMeta.forImage(imageRequest.url);
                }
                writeMeta(name, meta.failure(System.currentTimeMillis()));
            }
        }
    }

    private boolean isLatestLocked(ImageRequest imageRequest) {
        return latestImageGeneration == imageRequest.generation && latestImageUrl.equals(imageRequest.url);
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> sharedRefresh(String key, Supplier<CompletableFuture<T>> supplier) {
        synchronized (inFlight) {
            CompletableFuture<?> existing = inFlight.get(key);
            if (existing != null) {
                return (CompletableFuture<T>) existing;
            }
            CompletableFuture<T> created = supplier.get();
            inFlight.put(key, created);
            created.whenComplete((value, error) -> {
                synchronized (inFlight) {
                    inFlight.remove(key, created);
                }
            });
            return created;
        }
    }

    private CompletableFuture<Response> request(URI uri, String etag, int limit) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Only HTTPS endpoints are allowed"));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json, image/png, image/jpeg").GET();
        if (etag != null && !etag.isBlank()) builder.header("If-None-Match", etag);
        CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(builder.build(),
                responseInfo -> new LimitedBodySubscriber(limit,
                        responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1)));
        CompletableFuture<Response> result = exchange.thenApply(response -> new Response(response.statusCode(),
                response.headers().firstValue("ETag").orElse(null), response.body()));
        result.orTimeout(6, TimeUnit.SECONDS).whenComplete((value, error) -> {
            if (error != null) exchange.cancel(true);
        });
        return result;
    }

    private Optional<RemoteImage> readImage(RemoteAboutAd ad) {
        if (ad == null || ad.imageUrl().isEmpty()) {
            return Optional.empty();
        }
        CacheMeta meta = readMeta("about-ad-image");
        if (!ad.imageUrl().equals(meta.sourceUrl)) {
            return Optional.empty();
        }
        if (meta.imageWidth < 1 || meta.imageHeight < 1
                || !(".png".equals(meta.imageExtension) || ".jpg".equals(meta.imageExtension))) {
            return Optional.empty();
        }
        Path file = dataFile("about-ad-image", meta.imageExtension);
        try {
            if (RemoteImagePolicy.isSafeFile(file)) {
                return Optional.of(new RemoteImage(file, meta.imageWidth, meta.imageHeight,
                        Files.getLastModifiedTime(file).toMillis()));
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    private RemoteAboutAdSnapshot snapshot(RemoteAboutAd ad, Optional<RemoteImage> image) {
        return new RemoteAboutAdSnapshot(Optional.ofNullable(ad), image);
    }

    private <T> Optional<T> readJson(String cacheName, Function<JsonObject, T> parser) {
        Path file = dataFile(cacheName, ".json");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > JSON_LIMIT) {
                return Optional.empty();
            }
            return Optional.of(parser.apply(jsonObject(Files.readAllBytes(file))));
        } catch (IOException ignored) {
            return Optional.empty();
        } catch (RuntimeException ignored) {
            deleteQuietly(file);
            deleteQuietly(dataFile(cacheName, ".meta.json"));
            return Optional.empty();
        }
    }

    private RemoteLinks parseLinks(JsonObject object) {
        requireSchema(object);
        return new RemoteLinks(string(object, "modrinth"), string(object, "curseForge"), string(object, "source"),
                string(object, "coffee"), optionalString(object, "discord"));
    }

    private RemoteAboutAd parseAboutAd(JsonObject object) {
        requireSchema(object);
        return new RemoteAboutAd(string(object, "id"), localized(object, "title"), localized(object, "text"),
                string(object, "targetUrl"), string(object, "imageUrl"));
    }

    private RemoteTopDonate parseTopDonate(JsonObject object) {
        requireSchema(object);
        JsonElement entriesElement = object.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            throw new IllegalArgumentException("entries");
        }
        JsonArray entries = entriesElement.getAsJsonArray();
        if (entries.size() > 8) {
            throw new IllegalArgumentException("entries");
        }
        List<TopDonateEntry> parsed = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                throw new IllegalArgumentException("entry");
            }
            JsonObject item = entry.getAsJsonObject();
            parsed.add(new TopDonateEntry(string(item, "name"), string(item, "amount"), string(item, "currency"),
                    optionalString(item, "url"), optionalString(item, "color")));
        }
        return new RemoteTopDonate(parsed);
    }

    private static LocalizedText localized(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(name);
        }
        JsonObject object = element.getAsJsonObject();
        return new LocalizedText(string(object, "en"), string(object, "pl"));
    }

    private static void requireSchema(JsonObject object) {
        if (intValue(object, "schema") != 1) {
            throw new IllegalArgumentException("schema");
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name);
        }
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String name) {
        return object.has(name) ? string(object, name) : "";
    }

    private static JsonObject jsonObject(byte[] bytes) {
        JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return root.getAsJsonObject();
    }

    private static ImageData verifyImage(byte[] bytes) {
        boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        if (!png && !jpeg) {
            throw new IllegalArgumentException("Unsupported image format");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Invalid image");
            }
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Invalid image");
            }
            ImageReader reader = readers.next();
            int width;
            int height;
            try {
                reader.setInput(input, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
            if (width < 16 || height < 16 || !RemoteImagePolicy.allows(width, height)) {
                throw new IllegalArgumentException("Invalid image dimensions");
            }
            return new ImageData(width, height, jpeg);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid image", exception);
        }
    }

    private CacheMeta readMeta(String name) {
        Path file = dataFile(name, ".meta.json");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 2_048) {
                return CacheMeta.EMPTY;
            }
            JsonObject object = jsonObject(Files.readAllBytes(file));
            return new CacheMeta(optionalString(object, "etag"), longValue(object, "checkedAt"),
                    longValue(object, "nextAttempt"), intValue(object, "failures"), optionalString(object, "sourceUrl"),
                    dimensionValue(object, "imageWidth"), dimensionValue(object, "imageHeight"),
                    optionalString(object, "imageExtension"));
        } catch (IOException ignored) {
            return CacheMeta.EMPTY;
        } catch (RuntimeException ignored) {
            deleteQuietly(file);
            return CacheMeta.EMPTY;
        }
    }

    private void writeMeta(String name, CacheMeta meta) {
        String json = "{\"etag\":\"" + escape(meta.etag) + "\",\"checkedAt\":" + meta.checkedAt
                + ",\"nextAttempt\":" + meta.nextAttempt + ",\"failures\":" + meta.failures
                + ",\"sourceUrl\":\"" + escape(meta.sourceUrl) + "\",\"imageWidth\":" + meta.imageWidth
                + ",\"imageHeight\":" + meta.imageHeight + ",\"imageExtension\":\""
                + escape(meta.imageExtension) + "\"}";
        try {
            writeBytes(dataFile(name, ".meta.json"), json.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException ignored) {
        }
    }

    private static long longValue(JsonObject object, String name) {
        try {
            return Math.max(0L, object.get(name).getAsLong());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static int intValue(JsonObject object, String name) {
        try {
            return Math.max(0, Math.min(8, object.get(name).getAsInt()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int dimensionValue(JsonObject object, String name) {
        try {
            return Math.max(0, Math.min(4_096, object.get(name).getAsInt()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Path dataFile(String name, String extension) {
        return cacheDirectory.resolve(name + extension);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static void writeBytes(Path target, byte[] data) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update remote cache", exception);
        }
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "Waypoints Plus remote content");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class DefaultHolder {
        private static final RemoteContentService INSTANCE = new RemoteContentService(FabricLoader.getInstance().getConfigDir()
                .resolve("waypointsplus").resolve("cache").resolve("remote"));
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output;
        private final int limit;
        private Flow.Subscription subscription;
        private int received;

        private LimitedBodySubscriber(int limit, long contentLength) {
            this.limit = limit;
            if (contentLength > limit) {
                output = null;
                body.completeExceptionally(new IOException("Remote response is too large"));
            } else {
                output = new ByteArrayOutputStream(contentLength > 0 ? (int) contentLength : Math.min(limit, 8_192));
            }
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (body.isDone()) {
                subscription.cancel();
            } else {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (received > limit - length) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Remote response is too large"));
                    return;
                }
                byte[] bytes = new byte[length];
                buffer.get(bytes);
                output.write(bytes, 0, bytes.length);
                received += length;
            }
        }

        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            if (!body.isDone()) body.complete(output.toByteArray());
        }
    }

    private record Response(int status, String etag, byte[] body) {
    }

    private record ImageData(int width, int height, boolean jpeg) {
    }

    private record ImageRequest(long generation, String url) {
    }

    private record CacheMeta(String etag, long checkedAt, long nextAttempt, int failures, String sourceUrl,
                             int imageWidth, int imageHeight, String imageExtension) {
        private static final CacheMeta EMPTY = new CacheMeta("", 0, 0, 0, "", 0, 0, "");

        private static CacheMeta success(String etag, long now, String sourceUrl) {
            return new CacheMeta(etag == null ? "" : etag, now, 0, 0,
                    sourceUrl == null ? "" : sourceUrl, 0, 0, "");
        }

        private static CacheMeta imageSuccess(String etag, long now, String sourceUrl,
                                              int width, int height, String extension) {
            return new CacheMeta(etag == null ? "" : etag, now, 0, 0, sourceUrl, width, height, extension);
        }

        private static CacheMeta forImage(String sourceUrl) {
            return new CacheMeta("", 0, 0, 0, sourceUrl, 0, 0, "");
        }

        private CacheMeta touch(long now) {
            return new CacheMeta(etag, now, 0, 0, sourceUrl, imageWidth, imageHeight, imageExtension);
        }

        private CacheMeta failure(long now) {
            int nextFailures = Math.min(8, failures + 1);
            long delay = Math.min(BACKOFF_MAX_MILLIS, BACKOFF_MIN_MILLIS * (1L << (nextFailures - 1)));
            return new CacheMeta(etag, checkedAt, now + delay, nextFailures, sourceUrl,
                    imageWidth, imageHeight, imageExtension);
        }
    }
}
