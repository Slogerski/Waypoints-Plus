package pl.slogerski.waypointsplus.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

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
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    static void add(String alertName, boolean success) {
        String text = success
                ? UiText.get("Alert sent: ", "Wysłano alert: ") + alertName
                : UiText.get("Alert failed: ", "Błąd alertu: ") + alertName;
        enqueue(text, success);
    }

    static void queued(String alertName) {
        enqueue(UiText.get("MSG queued: ", "MSG w kolejce: ") + alertName, true);
    }

    private static void enqueue(String text, boolean success) {
        NOTIFICATIONS.addFirst(new Notification(text, success, System.nanoTime()));
        while (NOTIFICATIONS.size() > 3) NOTIFICATIONS.removeLast();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        long now = System.nanoTime();
        NOTIFICATIONS.removeIf(notification -> now - notification.createdAt() >= LIFETIME_NANOS);
        int index = 0;
        for (Notification notification : NOTIFICATIONS) {
            long age = now - notification.createdAt();
            double exit = age <= HOLD_NANOS ? 0.0
                    : smoothStep(Math.min(1.0, (age - HOLD_NANOS) / (double)FADE_NANOS));
            int alpha = Math.max(0, (int)Math.round(255.0 * (1.0 - exit)));
            int width = Math.max(116, client.textRenderer.getWidth(notification.text()) + 18);
            double progress = Math.min(1.0, age / (double)SLIDE_NANOS);
            double enter = smoothStep(progress);
            float x = (float)(-width + (width + 8) * enter);
            int y = 8 + index * 27;
            int bottom = y + 23;
            int accent = notification.success() ? 0xFF39D353 : 0xFFFF5B5B;
            context.getMatrices().push();
            context.getMatrices().translate(x, 0.0f, 0.0f);
            context.fill(1, y, width - 1, bottom, withAlpha(0xDD17171D, alpha));
            context.fill(0, y + 1, width, bottom - 1, withAlpha(0xDD17171D, alpha));
            context.fill(1, y, 4, bottom, withAlpha(accent, alpha));
            context.fill(0, y + 1, 4, bottom - 1, withAlpha(accent, alpha));
            context.drawTextWithShadow(client.textRenderer, notification.text(), 9, y + 8,
                    withAlpha(accent, alpha));
            context.getMatrices().pop();
            index++;
        }
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static int withAlpha(int color, int opacity) {
        int alpha = (color >>> 24) * opacity / 255;
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private record Notification(String text, boolean success, long createdAt) { }
}
