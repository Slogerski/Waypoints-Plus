package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.Consumer;

final class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onApply;
    private float hue, saturation, brightness, alpha;
    private int dragging;

    ColorPickerScreen(Screen parent, String argb, Consumer<String> onApply) {
        super(Component.literal(UiText.get("Color picker", "Wybór koloru")));
        this.parent = parent; this.onApply = onApply;
        long value = Long.parseLong(argb, 16);
        float[] hsb = Color.RGBtoHSB((int)(value >> 16) & 255, (int)(value >> 8) & 255, (int)value & 255, null);
        hue = hsb[0]; saturation = hsb[1]; brightness = hsb[2]; alpha = ((value >> 24) & 255) / 255.0f;
    }

    @Override protected void init() {
        int left = width / 2 - 120;
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Apply", "Zastosuj")), b -> apply())
                .pos(left, 210).size(116, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")), b -> onClose())
                .pos(left + 124, 210).size(116, 20).build());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && beginDrag(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && dragging != 0) { updateSelection(event.x(), event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    private boolean beginDrag(double x, double y) {
        int left = width / 2 - 120;
        if (inside(x, y, left, 46, 180, 100)) dragging = 1;
        else if (inside(x, y, left, 153, 180, 12)) dragging = 2;
        else if (inside(x, y, left, 174, 180, 12)) dragging = 3;
        else return false;
        updateSelection(x, y); return true;
    }

    private void updateSelection(double x, double y) {
        int left = width / 2 - 120;
        if (dragging == 1) {
            saturation = clamp((float)((x - left) / 180.0));
            brightness = 1.0f - clamp((float)((y - 46) / 100.0));
        } else if (dragging == 2) hue = clamp((float)((x - left) / 180.0));
        else if (dragging == 3) alpha = clamp((float)((x - left) / 180.0));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xE0101420);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 120;
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        for (int x = 0; x < 180; x += 4) for (int y = 0; y < 100; y += 4) {
            int rgb = Color.HSBtoRGB(hue, x / 179.0f, 1.0f - y / 99.0f);
            graphics.fill(left + x, 46 + y, left + x + 4, 46 + y + 4, 0xFF000000 | (rgb & 0xFFFFFF));
        }
        for (int x = 0; x < 180; x += 2) {
            int rgb = Color.HSBtoRGB(x / 179.0f, 1, 1);
            graphics.fill(left + x, 153, left + x + 2, 165, 0xFF000000 | (rgb & 0xFFFFFF));
            int a = Math.round(x / 179.0f * 255);
            graphics.fill(left + x, 174, left + x + 2, 186, (a << 24) | rgbValue());
        }
        marker(graphics, left + Math.round(saturation * 179), 46 + Math.round((1 - brightness) * 99), true);
        marker(graphics, left + Math.round(hue * 179), 159, false);
        marker(graphics, left + Math.round(alpha * 179), 180, false);
        graphics.fill(left + 190, 46, left + 240, 96, argb());
        graphics.text(font, "#" + String.format("%08X", argb()), left + 185, 106, 0xFFFFFFFF, true);
        graphics.text(font, UiText.get("Opacity: ", "Przezroczystość: ") + Math.round(alpha * 100) + "%",
                left + 185, 122, 0xFFFFFFFF, true);
    }

    private void marker(GuiGraphicsExtractor g, int x, int y, boolean circle) {
        int half = circle ? 4 : 2;
        g.fill(x - half - 1, y - half - 1, x + half + 2, y + half + 2, 0xFF000000);
        g.fill(x - half, y - half, x + half + 1, y + half + 1, 0xFFFFFFFF);
    }

    private int rgbValue() { return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF; }
    private int argb() { return (Math.round(alpha * 255) << 24) | rgbValue(); }
    private String value() { return String.format("%08X", argb()); }
    private void apply() { onApply.accept(value()); Minecraft.getInstance().setScreen(parent); }
    private static boolean inside(double x, double y, int bx, int by, int w, int h) { return x >= bx && x < bx + w && y >= by && y < by + h; }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
