package pl.slogerski.waypointsplus.fabric;

import pl.slogerski.waypointsplus.core.AlertDataSafety;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FightAlertManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("waypointsplus");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ALERT_LIST = new TypeToken<List<FightAlert>>() { }.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("waypointsplus").resolve("fight-alerts.json");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final List<FightAlert> ALERTS = new ArrayList<>();
    private static final Map<String, Boolean> PRESSED = new HashMap<>();
    private static final Map<String, MultiPressState> MULTI_PRESS = new HashMap<>();
    private static final Map<String, Long> LAST_SENT = new HashMap<>();
    private static final Map<UUID, OpponentHit> OPPONENTS = new LinkedHashMap<>();
    private static final Deque<QueuedMessage> MESSAGE_QUEUE = new ArrayDeque<>();
    private static long nextMessageAt;
    private static Object messageConnection;
    private static final java.util.concurrent.Semaphore WEBHOOK_SLOTS = new java.util.concurrent.Semaphore(4);
    private static boolean tracksOpponents;
    private static String opponentScope = "";
    private static boolean writesBlocked;
    private static String storageNotice;

    private FightAlertManager() { }

    static void load() {
        try {
            List<FightAlert> restored = new ArrayList<>();
            if (Files.exists(FILE)) {
                List<FightAlert> loaded = GSON.fromJson(AlertDataSafety.read(FILE), ALERT_LIST);
                if (loaded == null || loaded.size() > MAX_ALERTS || loaded.stream().anyMatch(alert -> !validStoredAlert(alert))
                        || loaded.stream().map(FightAlert::id).distinct().count() != loaded.size()) {
                    throw new IllegalArgumentException("Invalid alert list");
                }
                loaded.stream().map(FightAlertManager::normalize).forEach(restored::add);
            }
            ALERTS.clear();
            ALERTS.addAll(restored);
            writesBlocked = false;
        } catch (IOException error) {
            writesBlocked = true;
            storageNotice = UiText.get("Cannot read fight-alerts.json. Saving is blocked to protect your alerts.",
                    "Nie można odczytać fight-alerts.json. Zapis zablokowano, aby chronić alerty.");
            LOGGER.warn("Cannot read fight-alerts.json; writes blocked ({})", error.getClass().getSimpleName());
        } catch (RuntimeException error) {
            writesBlocked = true;
            storageNotice = UiText.get("Invalid fight-alerts.json. Saving is blocked to protect your alerts.",
                    "Błędny fight-alerts.json. Zapis zablokowano, aby chronić alerty.");
            LOGGER.warn("Invalid fight-alerts.json; writes blocked ({})", error.getClass().getSimpleName());
        }
        refreshOpponentTracking();
    }

    static List<FightAlert> alerts() {
        return List.copyOf(ALERTS);
    }

    static final int MAX_ALERTS = 32;

    static boolean canAdd() {
        return ALERTS.size() < MAX_ALERTS;
    }

    static boolean save(FightAlert alert) {
        if (!validStoredAlert(alert)) return false;
        List<FightAlert> next = new ArrayList<>(ALERTS);
        int index = -1;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(alert.id())) {
                index = i;
                break;
            }
        }
        if (index >= 0) next.set(index, normalize(alert));
        else if (canAdd()) next.add(normalize(alert));
        else return false;
        if (!persist(next)) return false;
        MESSAGE_QUEUE.removeIf(message -> message.alertId().equals(alert.id()));
        PRESSED.remove(alert.id());
        MULTI_PRESS.remove(alert.id());
        return true;
    }

    static boolean toggleEnabled(String id) {
        for (FightAlert alert : ALERTS) {
            if (!alert.id().equals(id)) continue;
            boolean enabled = !alert.enabled();
            boolean saved = save(new FightAlert(alert.id(), alert.service(), alert.method(), alert.triggerType(),
                    alert.keyCode(), alert.pressCount(), alert.windowMs(), alert.webhookUrl(), alert.message(),
                    enabled, alert.combinationKeys(), alert.name(), alert.popupEnabled(), alert.recipients(),
                    alert.messageIntervalMs()));
            return saved ? enabled : alert.enabled();
        }
        return false;
    }

    static boolean togglePopup(String id) {
        for (FightAlert alert : ALERTS) {
            if (!alert.id().equals(id)) continue;
            boolean enabled = !popupEnabled(alert);
            boolean saved = save(new FightAlert(alert.id(), alert.service(), alert.method(), alert.triggerType(),
                    alert.keyCode(), alert.pressCount(), alert.windowMs(), alert.webhookUrl(), alert.message(),
                    alert.enabled(), alert.combinationKeys(), alert.name(), enabled, alert.recipients(),
                    alert.messageIntervalMs()));
            return saved ? enabled : popupEnabled(alert);
        }
        return false;
    }

    static boolean popupEnabled(FightAlert alert) {
        return Boolean.TRUE.equals(alert.popupEnabled());
    }

    static void remove(String id) {
        List<FightAlert> next = new ArrayList<>(ALERTS);
        if (!next.removeIf(alert -> alert.id().equals(id)) || !persist(next)) return;
        PRESSED.remove(id);
        MULTI_PRESS.remove(id);
        LAST_SENT.remove(id);
        MESSAGE_QUEUE.removeIf(message -> message.alertId().equals(id));
    }

    static void tick(Minecraft client) {
        if (client.player != null && storageNotice != null) {
            client.player.sendSystemMessage(Component.literal(storageNotice));
            storageNotice = null;
        }
        Object connection = client.getConnection();
        if (messageConnection != connection) {
            MESSAGE_QUEUE.clear();
            PRESSED.clear();
            MULTI_PRESS.clear();
            LAST_SENT.clear();
            clearOpponents();
            nextMessageAt = 0;
            messageConnection = connection;
        }
        if (client.player == null || client.level == null || connection == null) return;
        drainMessages(client);
        if (client.player == null || client.screen != null) {
            PRESSED.clear();
            if (client.player == null) {
                clearOpponents();
                MESSAGE_QUEUE.clear();
                nextMessageAt = 0;
                MULTI_PRESS.clear();
            }
            return;
        }
        updateOpponentScope(client);
        long window = client.getWindow().handle();
        for (FightAlert alert : ALERTS) {
            if (!alert.enabled()) continue;
            boolean combination = "COMBINATION".equals(alert.triggerType());
            if ((combination && alert.combinationKeys().isEmpty())
                    || (!combination && alert.keyCode() < 0)) continue;
            boolean down = combination
                    ? alert.combinationKeys().stream().allMatch(key -> GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS)
                    : GLFW.glfwGetKey(window, alert.keyCode()) == GLFW.GLFW_PRESS;
            boolean rising = down && !PRESSED.getOrDefault(alert.id(), false);
            PRESSED.put(alert.id(), down);
            if (!rising) continue;
            if ("MULTI_PRESS".equals(alert.triggerType())) {
                handleMultiPress(client, alert);
            } else {
                dispatch(client, alert);
            }
        }
    }

    private static void handleMultiPress(Minecraft client, FightAlert alert) {
        long now = System.nanoTime();
        MultiPressState state = MULTI_PRESS.get(alert.id());
        long duration = Math.max(10, Math.min(50_000, alert.windowMs())) * 1_000_000L;
        boolean starting = state == null || now > state.deadline();
        int count = starting ? 1 : state.count() + 1;
        long deadline = starting ? now + duration : state.deadline();
        if (count >= Math.max(2, Math.min(8, alert.pressCount()))) {
            MULTI_PRESS.remove(alert.id());
            dispatch(client, alert);
        } else {
            MULTI_PRESS.put(alert.id(), new MultiPressState(count, deadline));
        }
    }

    private static void dispatch(Minecraft client, FightAlert alert) {
        long now = System.nanoTime();
        long last = LAST_SENT.getOrDefault(alert.id(), 0L);
        if (now - last < 1_000_000_000L) return;
        LAST_SENT.put(alert.id(), now);
        String player = client.player.getName().getString();
        String message = expand(alert.message(), client, player);
        if (message.isBlank()) return;
        if ("MSG".equals(alert.service())) {
            boolean queued = queueMessages(client, alert.id(), alert.recipients(), message, messageInterval(alert));
            if (popupEnabled(alert)) {
                if (queued) FightAlertNotifications.queued(alert.name());
                else FightAlertNotifications.add(alert.name(), false);
            }
            return;
        }
        if (!validWebhook(alert.webhookUrl())) return;
        message = AlertDataSafety.truncate(message, 2000);
        sendWebhook(alert.webhookUrl(), "WaypointsPlus Alert - " + player, message,
                client, popupEnabled(alert) ? alert.name() : null);
    }

    static java.util.concurrent.CompletableFuture<Boolean> testWebhook(Minecraft client, String webhookUrl) {
        if (client.player == null || !validWebhook(webhookUrl))
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        String player = client.player.getName().getString();
        return sendWebhook(webhookUrl, "WaypointsPlus Alert - " + player,
                "# WaypointsPlus - TestApi - (" + player + ")", client, null);
    }

    static void recordOpponent(Player attacker) {
        if (!tracksOpponents || attacker == null) return;
        UUID id = attacker.getUUID();
        OPPONENTS.remove(id);
        OPPONENTS.put(id, new OpponentHit(System.currentTimeMillis(), attacker.getName().getString()));
        while (OPPONENTS.size() > 64) OPPONENTS.remove(OPPONENTS.keySet().iterator().next());
    }

    private static java.util.concurrent.CompletableFuture<Boolean> sendWebhook(String webhookUrl, String username, String message,
                                    Minecraft client, String notificationName) {
        java.util.concurrent.CompletableFuture<Boolean> result;
        Object connection = client.getConnection();
        if (!WEBHOOK_SLOTS.tryAcquire()) {
            result = java.util.concurrent.CompletableFuture.completedFuture(false);
        } else {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("username", username);
                payload.addProperty("content", AlertDataSafety.truncate(message, 2000));
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                        .build();
                result = HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .handle((response, error) -> {
                            WEBHOOK_SLOTS.release();
                            return error == null && response != null
                                    && response.statusCode() >= 200 && response.statusCode() < 300;
                        });
            } catch (RuntimeException error) {
                WEBHOOK_SLOTS.release();
                result = java.util.concurrent.CompletableFuture.completedFuture(false);
            }
        }
        return result.thenApply(success -> {
            if (notificationName != null) {
                client.execute(() -> {
                    if (client.player != null && client.getConnection() == connection)
                        FightAlertNotifications.add(notificationName, success);
                });
            }
            return success;
        });
    }

    private static boolean queueMessages(Minecraft client, String alertId, List<String> recipients, String message,
                                         int intervalMs) {
        if (client.getConnection() == null || recipients == null || recipients.isEmpty()) return false;
        if (MESSAGE_QUEUE.stream().anyMatch(queued -> queued.alertId().equals(alertId))) return false;
        List<String> valid = recipients.stream().filter(FightAlertManager::validRecipient)
                .distinct().limit(10).toList();
        if (valid.isEmpty() || MESSAGE_QUEUE.size() + valid.size() > 40) return false;
        String scope = ServerScope.current();
        for (String recipient : valid) {
            int limit = Math.max(1, 256 - recipient.length() - 5);
            String singleLine = AlertDataSafety.chatLine(message, limit);
            if (singleLine.isBlank()) return false;
            MESSAGE_QUEUE.addLast(new QueuedMessage(alertId, scope, recipient, singleLine, intervalMs));
        }
        return true;
    }

    private static void drainMessages(Minecraft client) {
        if (client.player == null || client.getConnection() == null || MESSAGE_QUEUE.isEmpty()) return;
        long now = System.nanoTime();
        if (now < nextMessageAt) return;
        QueuedMessage queued = MESSAGE_QUEUE.removeFirst();
        if (queued.scope().equals(ServerScope.current())) {
            client.getConnection().sendCommand("msg " + queued.recipient() + " " + queued.message());
            nextMessageAt = now + queued.intervalMs() * 1_000_000L;
        }
    }

    private static String expand(String template, Minecraft client, String player) {
        var position = client.player.blockPosition();
        String server = ServerScope.current();
        int separator = server.indexOf(':');
        if (separator >= 0) server = server.substring(separator + 1);
        String dimension = displayDimension(client.level.dimension().identifier().toString());
        List<String> opponents = recentOpponents();
        Map<String, String> variables = Map.ofEntries(
                Map.entry("$X", Integer.toString(position.getX())),
                Map.entry("$Y", Integer.toString(position.getY())),
                Map.entry("$Z", Integer.toString(position.getZ())),
                Map.entry("$SERVER", server),
                Map.entry("$SERWER", server),
                Map.entry("$DIMENSION", dimension),
                Map.entry("$PLAYER", player),
                Map.entry("$GRACZ", player),
                Map.entry("$HP", health(client)),
                Map.entry("$OPPONENT_COUNT", Integer.toString(opponents.size())),
                Map.entry("$OPPONENTS", "`{" + String.join(", ", opponents) + "}`"),
                Map.entry("$GAPPLES", Integer.toString(itemCount(client, Items.GOLDEN_APPLE))),
                Map.entry("$NOTCH", Integer.toString(itemCount(client, Items.ENCHANTED_GOLDEN_APPLE))),
                Map.entry("$PEARLS", Integer.toString(itemCount(client, Items.ENDER_PEARL))),
                Map.entry("$TOTEMS", Integer.toString(itemCount(client, Items.TOTEM_OF_UNDYING))),
                Map.entry("$DURABILITY", armorDurability(client))
        );
        StringBuilder result = new StringBuilder();
        boolean fastWaypoint = false;
        for (int i = 0; i < template.length();) {
            if (template.charAt(i) == '\\' && i + 1 < template.length() && template.charAt(i + 1) == '$') {
                result.append('$');
                i += 2;
                continue;
            }
            if (template.regionMatches(true, i, "$FAST_WAYPOINT", 0, "$FAST_WAYPOINT".length())) {
                fastWaypoint = true;
                i += "$FAST_WAYPOINT".length();
                continue;
            }
            String match = null;
            for (String variable : variables.keySet()) {
                if (template.regionMatches(true, i, variable, 0, variable.length())) {
                    match = variable;
                    break;
                }
            }
            if (match == null) {
                result.append(template.charAt(i++));
            } else {
                result.append(variables.get(match));
                i += match.length();
            }
        }
        if (fastWaypoint) {
            while (result.length() > 0 && Character.isWhitespace(result.charAt(result.length() - 1))) {
                result.setLength(result.length() - 1);
            }
            if (result.length() > 0) result.append("\n\n");
            result.append(UiText.get(
                    "> Fast Waypoint (Copy the line below and press the assigned keybind to create it. "
                            + "You can configure the keybind in Controls.)",
                    "> Szybki waypoint (Skopiuj poniższy wiersz i naciśnij przypisany klawisz, aby go utworzyć. "
                            + "Skrót możesz ustawić w Sterowaniu.)"));
            result.append('\n').append("> ").append(position.getX()).append(' ')
                    .append(position.getY()).append(' ').append(position.getZ()).append(' ').append(player);
        }
        return result.toString();
    }

    private static String displayDimension(String identifier) {
        int separator = identifier.indexOf(':');
        String path = separator >= 0 ? identifier.substring(separator + 1) : identifier;
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether", "nether" -> "Nether";
            case "the_end", "end" -> "End";
            default -> path;
        };
    }

    private static String health(Minecraft client) {
        int current = Math.round(client.player.getHealth() + client.player.getAbsorptionAmount());
        int maximum = Math.round(client.player.getMaxHealth());
        return current + "/" + maximum;
    }

    private static int itemCount(Minecraft client, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static String armorDurability(Minecraft client) {
        List<String> armor = new ArrayList<>(4);
        addDurability(armor, UiText.get("Helmet", "Hełm"),
                client.player.getItemBySlot(EquipmentSlot.HEAD));
        addDurability(armor, UiText.get("Chestplate", "Napierśnik"),
                client.player.getItemBySlot(EquipmentSlot.CHEST));
        addDurability(armor, UiText.get("Leggings", "Spodnie"),
                client.player.getItemBySlot(EquipmentSlot.LEGS));
        addDurability(armor, UiText.get("Boots", "Buty"),
                client.player.getItemBySlot(EquipmentSlot.FEET));
        return "{" + String.join(", ", armor) + "}";
    }

    private static void addDurability(List<String> armor, String name, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        armor.add(name + ": " + (stack.getMaxDamage() - stack.getDamageValue()) + "/" + stack.getMaxDamage());
    }

    private static List<String> recentOpponents() {
        long cutoff = System.currentTimeMillis() - 60_000L;
        OPPONENTS.entrySet().removeIf(entry -> entry.getValue().timestamp() < cutoff);
        return OPPONENTS.values().stream().map(OpponentHit::name).toList();
    }

    private static void refreshOpponentTracking() {
        tracksOpponents = ALERTS.stream().filter(FightAlert::enabled).map(FightAlert::message)
                .anyMatch(message -> {
                    String upper = message.toUpperCase(java.util.Locale.ROOT);
                    return upper.contains("$OPPONENT_COUNT") || upper.contains("$OPPONENTS");
                });
        if (!tracksOpponents) clearOpponents();
    }

    private static void updateOpponentScope(Minecraft client) {
        if (!tracksOpponents) return;
        String scope = ServerScope.current() + "|" + client.level.dimension().identifier();
        if (!scope.equals(opponentScope) || client.player.isDeadOrDying()) {
            OPPONENTS.clear();
            opponentScope = scope;
        }
    }

    private static void clearOpponents() {
        OPPONENTS.clear();
        opponentScope = "";
    }

    private static boolean validStoredAlert(FightAlert alert) {
        return alert != null && alert.id() != null && alert.service() != null
                && alert.method() != null && alert.triggerType() != null
                && alert.webhookUrl() != null && alert.message() != null
                && alert.id().length() <= 128 && alert.service().length() <= 32
                && alert.method().length() <= 32 && alert.triggerType().length() <= 32
                && alert.webhookUrl().length() <= 2048
                && alert.message().length() <= AlertDataSafety.MAX_MESSAGE_LENGTH
                && (alert.name() == null || alert.name().length() <= 256);
    }

    private static FightAlert normalize(FightAlert alert) {
        List<Integer> keys = alert.combinationKeys() == null ? List.of() : alert.combinationKeys().stream()
                .filter(key -> key != null && key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST)
                .distinct().limit(4).toList();
        List<String> recipients = alert.recipients() == null ? List.of() : alert.recipients().stream()
                .filter(FightAlertManager::validRecipient).distinct().limit(10).toList();
        return new FightAlert(alert.id(), alert.service(), alert.method(), alert.triggerType(),
                alert.keyCode() >= GLFW.GLFW_KEY_SPACE && alert.keyCode() <= GLFW.GLFW_KEY_LAST ? alert.keyCode() : -1,
                Math.max(2, Math.min(8, alert.pressCount())), Math.max(10, Math.min(50_000, alert.windowMs())),
                alert.webhookUrl(), alert.message(), alert.enabled(), keys,
                alert.name() == null || alert.name().isBlank() ? "Alert" : alert.name(),
                popupEnabled(alert), recipients, messageInterval(alert));
    }

    static boolean validWebhook(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("discord.com") || host.equalsIgnoreCase("discordapp.com"))
                    && uri.getPath() != null && uri.getPath().startsWith("/api/webhooks/");
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean validRecipient(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    static int messageInterval(FightAlert alert) {
        Integer value = alert.messageIntervalMs();
        return value == null ? 1500 : Math.max(100, Math.min(20_000, value));
    }

    static FightAlert createDefault() {
        return new FightAlert(UUID.randomUUID().toString(), "", "WEBHOOK", "SINGLE_PRESS",
                -1, 3, 2000, "",
                "## $PLAYER ($HPHearts) needs help at $X $Y $Z $DIMENSION on $SERVER @here\n\n"
                        + "Opponents: $OPPONENT_COUNT\n$OPPONENTS",
                true, List.of(), "Alert", false, List.of(), 1500);
    }

    private static boolean persist(List<FightAlert> next) {
        if (writesBlocked) {
            storageNotice = UiText.get("Alerts could not be saved. The original file is protected.",
                    "Nie zapisano alertów. Oryginalny plik jest chroniony.");
            return false;
        }
        try {
            AlertDataSafety.write(FILE, GSON.toJson(next));
        } catch (IOException | RuntimeException error) {
            storageNotice = UiText.get("Could not save alerts to disk.", "Nie udało się zapisać alertów na dysku.");
            LOGGER.warn("Cannot save fight-alerts.json ({})", error.getClass().getSimpleName());
            return false;
        }
        ALERTS.clear();
        ALERTS.addAll(next);
        storageNotice = null;
        refreshOpponentTracking();
        return true;
    }

    record FightAlert(String id, String service, String method, String triggerType,
                      int keyCode, int pressCount, int windowMs,
                      String webhookUrl, String message, boolean enabled, List<Integer> combinationKeys,
                      String name, Boolean popupEnabled, List<String> recipients, Integer messageIntervalMs) { }

    private record MultiPressState(int count, long deadline) { }

    private record OpponentHit(long timestamp, String name) { }

    private record QueuedMessage(String alertId, String scope, String recipient, String message, int intervalMs) { }
}
