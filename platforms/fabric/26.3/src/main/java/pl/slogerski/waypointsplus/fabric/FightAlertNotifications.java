package pl.slogerski.waypointsplus.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

final class FightAlertNotifications {
    private static final long SLIDE_NANOS = 300_000_000L;
    private static final long HOLD_NANOS = 3_000_000_000L;
    private static final long FADE_NANOS = 1_000_000_000L;
    private static final long LIFETIME_NANOS = HOLD_NANOS + FADE_NANOS;
    private static final Deque<Notification> NOTIFICATIONS = new ArrayDeque<>(3);

    private FightAlertNotifications() { }

    static void register() {
        HudElementRegistry.addLast(Identifier.parse("waypointsplus:fight_alert_notifications"),
                (graphics, tickCounter) -> render(graphics));
    }

    static void add(String alertName, boolean success) {
        enqueue(success ? UiText.get("Alert sent: ", "Wysłano alert: ") + alertName
                : UiText.get("Alert failed: ", "Błąd alertu: ") + alertName, success);
    }

    static void queued(String alertName) {
        enqueue(UiText.get("MSG queued: ", "MSG w kolejce: ") + alertName, true);
    }

    private static void enqueue(String text, boolean success) {
        NOTIFICATIONS.addFirst(new Notification(text, success, System.nanoTime()));
        while (NOTIFICATIONS.size() > 3) NOTIFICATIONS.removeLast();
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        long now = System.nanoTime();
        NOTIFICATIONS.removeIf(notification -> now - notification.createdAt() >= LIFETIME_NANOS);
        int index = 0;
        for (Notification notification : NOTIFICATIONS) {
            long age = now - notification.createdAt();
            double exit = age <= HOLD_NANOS ? 0.0 : smoothStep(Math.min(1.0, (age - HOLD_NANOS) / (double) FADE_NANOS));
            int alpha = Math.max(0, (int) Math.round(255.0 * (1.0 - exit)));
            int width = Math.max(116, client.font.width(notification.text()) + 18);
            double enter = smoothStep(Math.min(1.0, age / (double) SLIDE_NANOS));
            float x = (float) (-width + (width + 8) * enter);
            int y = 8 + index * 27;
            int bottom = y + 23;
            int accent = notification.success() ? 0xFF39D353 : 0xFFFF5B5B;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, 0.0f);
            graphics.fill(1, y, width - 1, bottom, withAlpha(0xDD17171D, alpha));
            graphics.fill(0, y + 1, width, bottom - 1, withAlpha(0xDD17171D, alpha));
            graphics.fill(1, y, 4, bottom, withAlpha(accent, alpha));
            graphics.fill(0, y + 1, 4, bottom - 1, withAlpha(accent, alpha));
            graphics.text(client.font, notification.text(), 9, y + 8, withAlpha(accent, alpha), true);
            graphics.pose().popMatrix();
            index++;
        }
    }

    private static double smoothStep(double value) { return value * value * (3.0 - 2.0 * value); }
    private static int withAlpha(int color, int opacity) {
        return ((color >>> 24) * opacity / 255) << 24 | color & 0x00FFFFFF;
    }
    private record Notification(String text, boolean success, long createdAt) { }
}
