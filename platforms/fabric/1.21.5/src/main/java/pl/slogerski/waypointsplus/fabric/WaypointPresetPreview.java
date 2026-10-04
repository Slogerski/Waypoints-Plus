package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Base64;

final class WaypointPresetPreview implements AutoCloseable {
    private static final Identifier PNG_TEXTURE = Identifier.of("waypointsplus", "dynamic/preset_preview");
    private final WaypointPreset preset;
    private final WaypointPresetLayout layout = new WaypointPresetLayout();
    private final TextRenderer textRenderer;
    private final ItemStack item;
    private NativeImageBackedTexture texture;
    private int imageSize;

    WaypointPresetPreview(WaypointPreset preset, TextRenderer textRenderer) {
        this.preset = preset;
        this.textRenderer = textRenderer;
        item = new ItemStack(Registries.ITEM.get(Identifier.of(WaypointPreset.displayItem(preset.item))));
        if (!preset.png.isEmpty()) loadImage();
    }

    private void loadImage() {
        NativeImage image = null;
        NativeImageBackedTexture created = null;
        try {
            if (!WaypointPreset.validPng(preset.png)) return;
            image = NativeImage.read(Base64.getDecoder().decode(preset.png));
            imageSize = image.getWidth();
            if (image.getHeight() != imageSize || (imageSize != 16 && imageSize != 32 && imageSize != 64)) {
                image.close();
                return;
            }
            created = new NativeImageBackedTexture(() -> "waypointsplus/preset-preview", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(PNG_TEXTURE, created);
            texture = created;
        } catch (IOException | RuntimeException exception) {
            if (created != null) created.close();
            else if (image != null) image.close();
        }
    }

    String text(int index) {
        return switch (index) {
            case 0 -> UiText.get("Waypoint", "Waypoint");
            case 1 -> "125 m";
            case 2 -> "100, 64, 200";
            default -> "Default";
        };
    }

    WaypointPreset.TextPart part(int index) {
        return switch (index) {
            case 0 -> preset.label;
            case 1 -> preset.distance;
            case 2 -> preset.coordinates;
            default -> preset.profile;
        };
    }

    private void updateLayout() {
        for (int i = 0; i < 4; i++) {
            WaypointPreset.TextPart part = part(i);
            float width = textRenderer.getWidth(text(i)) * part.scale;
            layout.setText(preset, i, part.x - width / 2, part.y, width, textRenderer.fontHeight * part.scale, part.enabled);
        }
        layout.set(4, preset.iconX, preset.iconY, 16 * preset.iconScale, 16 * preset.iconScale, preset.icon);
        layout.resolve(preset);
    }

    WaypointPresetLayout layout() {
        updateLayout();
        return layout;
    }

    boolean enabled(int index) {
        return index == 5 ? preset.border || preset.background : index == 4 ? preset.icon : part(index).enabled;
    }

    Bounds bounds(int index) {
        updateLayout();
        return resolvedBounds(index);
    }

    private Bounds resolvedBounds(int index) {
        return new Bounds(layout.left(index), layout.top(index), layout.right(index), layout.bottom(index));
    }

    Bounds contentBounds() {
        updateLayout();
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 5; i++) {
            if (i == 4 ? !preset.icon : !part(i).enabled) continue;
            Bounds bounds = resolvedBounds(i);
            left = Math.min(left, bounds.left); top = Math.min(top, bounds.top);
            right = Math.max(right, bounds.right); bottom = Math.max(bottom, bounds.bottom);
        }
        if (!Float.isFinite(left)) return new Bounds(-20, -5, 20, 5);
        return new Bounds(left - preset.padding, top - preset.padding, right + preset.padding, bottom + preset.padding);
    }

    int hit(double x, double y) {
        updateLayout();
        for (int i = 3; i >= 0; i--) if (part(i).enabled && resolvedBounds(i).contains(x, y)) return i;
        if (preset.icon && resolvedBounds(4).contains(x, y)) return 4;
        return -1;
    }

    void draw(DrawContext context, float originX, float originY, float zoom, int selected) {
        updateLayout();
        int l = (int) layout.left(5), t = (int) layout.top(5);
        int r = (int) layout.right(5), b = (int) layout.bottom(5);
        context.getMatrices().push();
        context.getMatrices().translate(originX, originY, 0);
        context.getMatrices().scale(zoom, zoom, 1);
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int marker = settings.markerArgb | 0xFF000000;
        int background = tintedBackground(settings.backgroundArgb, marker, settings.markerTintPercent);
        if (preset.background) shape(context, l, t, r, b, background, preset.corners);
        if (preset.border) frame(context, l, t, r, b, Math.max(1, Math.round(preset.borderSize)), marker, preset.corners);
        for (int i = 0; i < 4; i++) {
            WaypointPreset.TextPart part = part(i);
            if (!part.enabled) continue;
            context.getMatrices().push();
            context.getMatrices().translate(layout.centerX(i), layout.textY(preset, i), 0);
            context.getMatrices().scale(part.scale, part.scale, 1);
            context.getMatrices().translate(-textRenderer.getWidth(text(i)) / 2.0f, 0, 0);
            context.drawTextWithShadow(textRenderer, text(i), 0, 0,
                    settings.matchTextToBorder ? marker : settings.textArgb);
            context.getMatrices().pop();
        }
        if (preset.icon) {
            context.getMatrices().push();
            context.getMatrices().translate(layout.left(4), layout.top(4), 0);
            context.getMatrices().scale(preset.iconScale, preset.iconScale, 1);
            if (preset.pngIcon && texture != null) {
                context.drawTexture(RenderLayer::getGuiTextured, PNG_TEXTURE, 0, 0, 0f, 0f,
                        16, 16, imageSize, imageSize, imageSize, imageSize);
            } else if (!preset.pngIcon) context.drawItem(item, 0, 0);
            context.getMatrices().pop();
        }
        if (selected >= 0 && (selected == 4 ? preset.icon : part(selected).enabled)) {
            Bounds bounds = resolvedBounds(selected);
            frame(context, (int) bounds.left - 1, (int) bounds.top - 1, (int) Math.ceil(bounds.right) + 1,
                    (int) Math.ceil(bounds.bottom) + 1, 1, 0xFFCEB36F, false);
        }
        context.getMatrices().pop();
    }

    private static int tintedBackground(int background, int marker, int percent) {
        int result = background & 0xFF000000;
        for (int shift : new int[] {0, 8, 16}) {
            int base = (background >>> shift) & 255;
            int tint = (marker >>> shift) & 255;
            result |= (base + (tint - base) * percent / 100) << shift;
        }
        return result;
    }

    private static void shape(DrawContext context, int l, int t, int r, int b, int color, boolean rounded) {
        if (!rounded || r - l < 6 || b - t < 6) { context.fill(l, t, r, b, color); return; }
        context.fill(l + 2, t, r - 2, t + 1, color);
        context.fill(l + 1, t + 1, r - 1, t + 2, color);
        context.fill(l, t + 2, r, b - 2, color);
        context.fill(l + 1, b - 2, r - 1, b - 1, color);
        context.fill(l + 2, b - 1, r - 2, b, color);
    }

    private static void frame(DrawContext context, int l, int t, int r, int b, int size, int color, boolean rounded) {
        int inset = rounded ? 2 : 0;
        context.fill(l + inset, t, r - inset, t + size, color);
        context.fill(l + inset, b - size, r - inset, b, color);
        context.fill(l, t + inset, l + size, b - inset, color);
        context.fill(r - size, t + inset, r, b - inset, color);
        if (rounded) {
            context.fill(l + 1, t + 1, l + 2, t + 2, color);
            context.fill(r - 2, t + 1, r - 1, t + 2, color);
            context.fill(l + 1, b - 2, l + 2, b - 1, color);
            context.fill(r - 2, b - 2, r - 1, b - 1, color);
        }
    }

    @Override public void close() {
        if (texture == null) return;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(PNG_TEXTURE);
        texture = null;
    }

    record Bounds(float left, float top, float right, float bottom) {
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }
}
