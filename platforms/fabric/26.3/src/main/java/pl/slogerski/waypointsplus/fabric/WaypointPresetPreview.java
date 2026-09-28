package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.Base64;

final class WaypointPresetPreview implements AutoCloseable {
    private static final Identifier PNG_TEXTURE = Identifier.fromNamespaceAndPath("waypointsplus", "dynamic/preset_preview");
    private final WaypointPreset preset;
    private final Font font;
    private final ItemStack item;
    private DynamicTexture texture;
    private int imageSize;

    WaypointPresetPreview(WaypointPreset preset, Font font) {
        this.preset = preset;
        this.font = font;
        item = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(preset.item)));
        if (!preset.png.isEmpty()) loadImage();
    }

    private void loadImage() {
        NativeImage image = null;
        DynamicTexture created = null;
        try {
            if (!WaypointPreset.validPng(preset.png)) return;
            image = NativeImage.read(Base64.getDecoder().decode(preset.png));
            imageSize = image.getWidth();
            if (image.getHeight() != imageSize || (imageSize != 16 && imageSize != 32 && imageSize != 64)) {
                image.close();
                return;
            }
            created = new DynamicTexture(() -> "waypointsplus/preset-preview", image);
            Minecraft.getInstance().getTextureManager().register(PNG_TEXTURE, created);
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

    Bounds textBounds(int index) {
        WaypointPreset.TextPart part = part(index);
        float width = font.width(text(index)) * part.scale;
        return new Bounds(part.x - width / 2, part.y, part.x + width / 2, part.y + font.lineHeight * part.scale);
    }

    Bounds iconBounds() {
        float size = 16 * preset.iconScale;
        return new Bounds(preset.iconX, preset.iconY, preset.iconX + size, preset.iconY + size);
    }

    Bounds contentBounds() {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 5; i++) {
            if (i == 4 ? !preset.icon : !part(i).enabled) continue;
            Bounds bounds = i == 4 ? iconBounds() : textBounds(i);
            left = Math.min(left, bounds.left); top = Math.min(top, bounds.top);
            right = Math.max(right, bounds.right); bottom = Math.max(bottom, bounds.bottom);
        }
        if (!Float.isFinite(left)) return new Bounds(-20, -5, 20, 5);
        return new Bounds(left - preset.padding, top - preset.padding, right + preset.padding, bottom + preset.padding);
    }

    int hit(double x, double y) {
        for (int i = 3; i >= 0; i--) if (part(i).enabled && textBounds(i).contains(x, y)) return i;
        if (preset.icon && iconBounds().contains(x, y)) return 4;
        return -1;
    }

    void draw(GuiGraphicsExtractor context, float originX, float originY, float zoom, int selected) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 5; i++) {
            if (i == 4 ? !preset.icon : !part(i).enabled) continue;
            if (!preset.linked(i)) continue;
            Bounds bounds = i == 4 ? iconBounds() : textBounds(i);
            left = Math.min(left, bounds.left); top = Math.min(top, bounds.top);
            right = Math.max(right, bounds.right); bottom = Math.max(bottom, bounds.bottom);
        }
        if (!Float.isFinite(left)) { left = -20; top = -5; right = 20; bottom = 5; }
        context.pose().pushMatrix();
        context.pose().translate(originX, originY);
        context.pose().scale(zoom, zoom);
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int marker = settings.markerArgb | 0xFF000000;
        int background = tintedBackground(settings.backgroundArgb, marker, settings.markerTintPercent);
        int l = (int) Math.floor(left - preset.padding), t = (int) Math.floor(top - preset.padding);
        int r = (int) Math.ceil(right + preset.padding), b = (int) Math.ceil(bottom + preset.padding);
        if (preset.background) shape(context, l, t, r, b, background, preset.corners);
        if (preset.border) frame(context, l, t, r, b, Math.max(1, Math.round(preset.borderSize)), marker, preset.corners);
        for (int i = 0; i < 4; i++) {
            WaypointPreset.TextPart part = part(i);
            if (!part.enabled) continue;
            context.pose().pushMatrix();
            context.pose().translate(part.x, part.y);
            context.pose().scale(part.scale, part.scale);
            context.text(font, text(i), -font.width(text(i)) / 2, 0,
                    settings.matchTextToBorder ? marker : settings.textArgb);
            context.pose().popMatrix();
        }
        if (preset.icon) {
            context.pose().pushMatrix();
            context.pose().translate(preset.iconX, preset.iconY);
            context.pose().scale(preset.iconScale, preset.iconScale);
            if (preset.pngIcon && texture != null) {
                context.blit(RenderPipelines.GUI_TEXTURED, PNG_TEXTURE, 0, 0, 0f, 0f,
                        16, 16, imageSize, imageSize, imageSize, imageSize);
            } else if (!preset.pngIcon) context.item(item, 0, 0);
            context.pose().popMatrix();
        }
        if (selected >= 0 && (selected == 4 ? preset.icon : part(selected).enabled)) {
            Bounds bounds = selected == 4 ? iconBounds() : textBounds(selected);
            frame(context, (int) bounds.left - 1, (int) bounds.top - 1, (int) Math.ceil(bounds.right) + 1,
                    (int) Math.ceil(bounds.bottom) + 1, 1, 0xFFCEB36F, false);
        }
        context.pose().popMatrix();
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

    private static void shape(GuiGraphicsExtractor context, int l, int t, int r, int b, int color, boolean rounded) {
        if (!rounded || r - l < 6 || b - t < 6) { context.fill(l, t, r, b, color); return; }
        context.fill(l + 2, t, r - 2, t + 1, color);
        context.fill(l + 1, t + 1, r - 1, t + 2, color);
        context.fill(l, t + 2, r, b - 2, color);
        context.fill(l + 1, b - 2, r - 1, b - 1, color);
        context.fill(l + 2, b - 1, r - 2, b, color);
    }

    private static void frame(GuiGraphicsExtractor context, int l, int t, int r, int b, int size, int color, boolean rounded) {
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
        Minecraft.getInstance().getTextureManager().release(PNG_TEXTURE);
        texture = null;
    }

    record Bounds(float left, float top, float right, float bottom) {
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    }
}
