package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointAppearance;

final class WaypointLabel {
    private final Waypoint waypoint;
    private final Integer waypointColor;
    private final String coordinates;
    private final WaypointPreset preset;
    private final boolean classic;
    private final WaypointPresetLayout layout;
    private final Text[] text;
    private final int[] widths;
    private final boolean[] enabled;
    private final Matrix4f[] textMatrices;
    private final Matrix4f scratch = new Matrix4f();
    final Matrix4f matrix = new Matrix4f();
    private boolean initialized;
    private boolean showDistance, showCoordinates, polish, backgroundEnabled, matchText;
    private int backgroundSetting, textSetting, markerSetting, tintSetting;
    private int marker, textColor, background;
    private long distanceBucket = Long.MIN_VALUE;
    private boolean kilometers;
    private String distanceText = "";
    private float left, top, right, bottom;
    private float contentLeft, contentTop, contentRight, contentBottom;
    private double radius;
    private double contentRadius;
    private PanelQuad[] panels = new PanelQuad[0];

    WaypointLabel(Waypoint waypoint, double x, double y, double z, WaypointPreset preset) {
        this.waypoint = waypoint;
        waypointColor = parseArgb(waypoint.colorArgb());
        this.preset = preset;
        classic = preset == null || WaypointPreset.DEFAULT_ICON_ID.equals(preset.id);
        layout = classic ? null : new WaypointPresetLayout();
        coordinates = Math.round(x - 0.5) + " " + Math.round(y) + " " + Math.round(z - 0.5);
        int count = classic ? 1 : 4;
        text = new Text[count]; widths = new int[count]; enabled = new boolean[count];
        textMatrices = new Matrix4f[count];
        for (int i = 0; i < count; i++) textMatrices[i] = new Matrix4f();
    }

    int marker(int fallback) { return waypointColor == null ? fallback : waypointColor; }

    void update(TextRenderer renderer, WaypointSettings settings, double distance) {
        boolean languageChanged = polish != "pl".equals(settings.language);
        boolean layoutChanged = !initialized || languageChanged || showDistance != settings.showDistance
                || showCoordinates != settings.showCoordinates;
        if (!initialized || backgroundEnabled != settings.background || backgroundSetting != settings.backgroundArgb
                || textSetting != settings.textArgb || markerSetting != settings.markerArgb
                || tintSetting != settings.markerTintPercent || matchText != settings.matchTextToBorder) {
            backgroundEnabled = settings.background; backgroundSetting = settings.backgroundArgb;
            textSetting = settings.textArgb; markerSetting = settings.markerArgb;
            tintSetting = settings.markerTintPercent; matchText = settings.matchTextToBorder;
            marker = marker(markerSetting);
            textColor = matchText ? marker : textSetting;
            background = backgroundEnabled
                    ? WaypointAppearance.backgroundArgb(waypoint, backgroundSetting, marker, tintSetting) : 0;
        }
        showDistance = settings.showDistance; showCoordinates = settings.showCoordinates;
        polish = "pl".equals(settings.language);
        boolean wantsDistance = showDistance && (classic || preset.distance.enabled);
        if (wantsDistance) {
            boolean km = distance >= 1000;
            long bucket = Math.round(km ? distance / 100 : distance);
            if (bucket != distanceBucket || km != kilometers || languageChanged || !initialized) {
                distanceBucket = bucket; kilometers = km;
                distanceText = km ? bucket / 10 + (polish ? "," : ".") + bucket % 10 + " km" : bucket + " m";
                layoutChanged = true;
            }
        }
        if (!layoutChanged) return;
        float oldLeft = left, oldTop = top, oldRight = right, oldBottom = bottom;
        boolean first = !initialized;
        initialized = true;
        contentRadius = 0;
        contentLeft = contentTop = Float.POSITIVE_INFINITY;
        contentRight = contentBottom = Float.NEGATIVE_INFINITY;
        if (classic) {
            String label = waypoint.name();
            if (showDistance) label += "  •  " + distanceText;
            if (showCoordinates) label += "  " + coordinates;
            setText(renderer, 0, label);
            enabled[0] = true;
            left = -widths[0] / 2.0f - 3; top = -7;
            right = widths[0] / 2.0f + 3; bottom = 8;
            textMatrices[0].identity().translate(-widths[0] / 2.0f, -3, 0);
            if (preset != null && preset.icon) {
                includeContent(iconX(), iconY(), iconX() + 16, iconY() + 16);
            }
        } else {
            setText(renderer, 0, waypoint.name()); setText(renderer, 1, distanceText);
            setText(renderer, 2, coordinates); setText(renderer, 3, waypoint.profile());
            for (int i = 0; i < 4; i++) {
                WaypointPreset.TextPart part = part(i);
                enabled[i] = part.enabled && (i != 1 || showDistance) && (i != 2 || showCoordinates);
                float width = widths[i] * part.scale;
                layout.setText(preset, i, part.x - width / 2, part.y, width, renderer.fontHeight * part.scale, enabled[i]);
            }
            layout.set(4, preset.iconX, preset.iconY, 16 * preset.iconScale, 16 * preset.iconScale, preset.icon);
            layout.resolve(preset);
            left = layout.left(5); top = layout.top(5); right = layout.right(5); bottom = layout.bottom(5);
            for (int i = 0; i < 4; i++) {
                if (!enabled[i]) continue;
                WaypointPreset.TextPart part = part(i);
                float offset = WaypointPreset.DESIGNED_ID.equals(preset.id) ? 1 : 0;
                float textY = layout.textY(preset, i) + offset;
                includeContent(layout.left(i), layout.top(i) + offset, layout.right(i), layout.bottom(i) + offset);
                textMatrices[i].identity().translate(layout.centerX(i), textY, 0).scale(part.scale, part.scale, 1)
                        .translate(-widths[i] / 2.0f, 0, 0);
            }
            if (preset.icon) includeContent(layout.left(4), layout.top(4), layout.right(4), layout.bottom(4));
        }
        if (first || oldLeft != left || oldTop != top || oldRight != right || oldBottom != bottom) {
            buildPanel();
        }
        radius = Math.max(contentRadius,
                Math.max(Math.max(Math.abs(left), Math.abs(right)), Math.max(Math.abs(top), Math.abs(bottom)))) * 1.415;
    }

    double radius() {
        return radius;
    }

    float left() { return left; }
    float top() { return top; }
    float right() { return right; }
    float bottom() { return bottom; }
    float visualLeft() { return Math.min(left, contentLeft); }
    float visualTop() { return Math.min(top, contentTop); }
    float visualRight() { return Math.max(right, contentRight); }
    float visualBottom() { return Math.max(bottom, contentBottom); }
    float iconX() { return classic ? left - 20 : layout.left(4); }
    float iconY() { return classic ? (top + bottom - 16) / 2 - (preset != null ? 1 : 0) : layout.top(4); }

    private void setText(TextRenderer renderer, int index, String value) {
        if (text[index] != null && text[index].getString().equals(value)) return;
        text[index] = Text.literal(value);
        widths[index] = renderer.getWidth(text[index]);
    }

    private WaypointPreset.TextPart part(int index) {
        return switch (index) { case 0 -> preset.label; case 1 -> preset.distance;
            case 2 -> preset.coordinates; default -> preset.profile; };
    }

    private void includeContent(float l, float t, float r, float b) {
        contentLeft = Math.min(contentLeft, l); contentTop = Math.min(contentTop, t);
        contentRight = Math.max(contentRight, r); contentBottom = Math.max(contentBottom, b);
        contentRadius = Math.max(contentRadius,
                Math.max(Math.max(Math.abs(l), Math.abs(r)), Math.max(Math.abs(t), Math.abs(b))));
    }

    void drawPanel(net.minecraft.client.render.command.RenderCommandQueue queue) {
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().set(matrix);
        PanelQuad[] framePanels = panels;
        int frameMarker = marker, frameBackground = background;
        queue.submitCustom(matrices, RenderLayer.getTextBackgroundSeeThrough(), (entry, vertices) -> {
            Matrix4f transform = entry.getPositionMatrix();
            for (PanelQuad quad : framePanels) {
                int color = quad.border ? frameMarker : frameBackground;
                if ((color >>> 24) == 0) continue;
                vertices.vertex(transform, quad.left, quad.top, 0.01f).color(color).light(0xF000F0);
                vertices.vertex(transform, quad.left, quad.bottom, 0.01f).color(color).light(0xF000F0);
                vertices.vertex(transform, quad.right, quad.bottom, 0.01f).color(color).light(0xF000F0);
                vertices.vertex(transform, quad.right, quad.top, 0.01f).color(color).light(0xF000F0);
            }
        });
    }

    void drawText(TextRenderer renderer, WaypointTextShaders shaders,
                  net.minecraft.client.render.command.RenderCommandQueue queue) {
        MatrixStack matrices = new MatrixStack();
        for (int i = 0; i < text.length; i++) {
            if (!enabled[i]) continue;
            matrices.peek().getPositionMatrix().set(matrix).mul(textMatrices[i]);
            shaders.submit(renderer, matrices, queue, text[i].asOrderedText(), 0, 0, textColor, 0xF000F0);
        }
    }

    private void buildPanel() {
        java.util.ArrayList<PanelQuad> quads = new java.util.ArrayList<>(16);
        if (!classic) {
            buildPresetPanel(quads);
            panels = quads.toArray(PanelQuad[]::new);
            return;
        }
        add(quads, left + 2, top + 1, right - 2, top + 2, false);
        add(quads, left + 1, top + 2, right - 1, bottom - 2, false);
        add(quads, left + 2, bottom - 2, right - 2, bottom - 1, false);
        add(quads, left + 2, top, right - 2, top + 1, true);
        add(quads, left + 2, bottom - 1, right - 2, bottom, true);
        add(quads, left, top + 2, left + 1, bottom - 2, true);
        add(quads, right - 1, top + 2, right, bottom - 2, true);
        add(quads, left + 1, top + 1, left + 2, top + 2, true);
        add(quads, right - 2, top + 1, right - 1, top + 2, true);
        add(quads, left + 1, bottom - 2, left + 2, bottom - 1, true);
        add(quads, right - 2, bottom - 2, right - 1, bottom - 1, true);
        panels = quads.toArray(PanelQuad[]::new);
    }

    private void buildPresetPanel(java.util.List<PanelQuad> quads) {
        float size = preset.border ? Math.min(preset.borderSize, Math.min(right - left, bottom - top) / 2) : 0;
        float innerTop = top + size, innerBottom = bottom - size;
        float innerCorner = preset.corners ? Math.max(0, 2 - size) : 0;
        java.util.TreeSet<Float> rows = new java.util.TreeSet<>();
        for (float y : new float[] {top, top + 1, top + 2, bottom - 2, bottom - 1, bottom,
                innerTop, innerTop + innerCorner, innerBottom - innerCorner, innerBottom}) {
            if (y >= top && y <= bottom) rows.add(y);
        }
        float start = top;
        for (float end : rows) {
            if (end <= start) continue;
            float middle = (start + end) / 2;
            float edge = Math.min(middle - top, bottom - middle);
            float inset = preset.corners ? (edge < 1 ? 2 : edge < 2 ? 1 : 0) : 0;
            float outerLeft = left + inset, outerRight = right - inset;
            if (!preset.border) {
                if (preset.background) add(quads, outerLeft, start, outerRight, end, false);
            } else if (middle < innerTop || middle >= innerBottom) {
                if (preset.border) add(quads, outerLeft, start, outerRight, end, true);
            } else {
                float innerInset = Math.min(middle - innerTop, innerBottom - middle) < innerCorner ? innerCorner : 0;
                float innerLeft = Math.max(outerLeft, left + size + innerInset);
                float innerRight = Math.min(outerRight, right - size - innerInset);
                if (preset.border) {
                    add(quads, outerLeft, start, innerLeft, end, true);
                    add(quads, innerRight, start, outerRight, end, true);
                }
                if (preset.background) add(quads, innerLeft, start, innerRight, end, false);
            }
            start = end;
        }
    }

    private static void add(java.util.List<PanelQuad> quads, float left, float top, float right, float bottom, boolean border) {
        if (right > left && bottom > top) quads.add(new PanelQuad(left, top, right, bottom, border));
    }

    private static Integer parseArgb(String value) {
        try { return (int) Long.parseLong(value.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return null; }
    }

    private record PanelQuad(float left, float top, float right, float bottom, boolean border) { }
}
