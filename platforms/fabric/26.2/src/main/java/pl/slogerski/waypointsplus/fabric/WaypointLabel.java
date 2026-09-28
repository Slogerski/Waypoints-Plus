package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointAppearance;

final class WaypointLabel {
    private final Waypoint waypoint;
    private final Integer waypointColor;
    private final String coordinates;
    private final WaypointPreset preset;
    private final Component[] text;
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
    private double radius;
    private double contentRadius;
    private PanelQuad[] panels = new PanelQuad[0];

    WaypointLabel(Waypoint waypoint, double x, double y, double z, WaypointPreset preset) {
        this.waypoint = waypoint;
        waypointColor = parseArgb(waypoint.colorArgb());
        this.preset = preset;
        coordinates = Math.round(x) + " " + Math.round(y) + " " + Math.round(z);
        int count = preset == null ? 1 : 4;
        text = new Component[count]; widths = new int[count]; enabled = new boolean[count];
        textMatrices = new Matrix4f[count];
        for (int i = 0; i < count; i++) textMatrices[i] = new Matrix4f();
    }

    int marker(int fallback) { return waypointColor == null ? fallback : waypointColor; }

    void update(Font renderer, WaypointSettings settings, double distance) {
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
        boolean wantsDistance = showDistance && (preset == null || preset.distance.enabled);
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
        if (preset == null) {
            String label = waypoint.name();
            if (showDistance) label += "  •  " + distanceText;
            if (showCoordinates) label += "  " + coordinates;
            setText(renderer, 0, label);
            enabled[0] = true;
            left = -widths[0] / 2.0f - 3; top = -7;
            right = widths[0] / 2.0f + 3; bottom = 8;
            textMatrices[0].identity().translate(-widths[0] / 2.0f, -3, 0);
        } else {
            setText(renderer, 0, waypoint.name()); setText(renderer, 1, distanceText);
            setText(renderer, 2, coordinates); setText(renderer, 3, waypoint.profile());
            left = top = Float.POSITIVE_INFINITY; right = bottom = Float.NEGATIVE_INFINITY;
            contentRadius = 0;
            for (int i = 0; i < 4; i++) {
                WaypointPreset.TextPart part = part(i);
                enabled[i] = part.enabled && (i != 1 || showDistance) && (i != 2 || showCoordinates);
                if (!enabled[i]) continue;
                float width = widths[i] * part.scale;
                float l = part.x - width / 2, r = part.x + width / 2;
                float b = part.y + renderer.lineHeight * part.scale;
                includeContent(l, part.y, r, b);
                if (preset.linked(i)) include(l, part.y, r, b);
                textMatrices[i].identity().translate(part.x, part.y, 0).scale(part.scale, part.scale, 1)
                        .translate(-widths[i] / 2.0f, 0, 0);
            }
            if (preset.icon) {
                float r = preset.iconX + 16 * preset.iconScale, b = preset.iconY + 16 * preset.iconScale;
                includeContent(preset.iconX, preset.iconY, r, b);
                if (preset.iconLinked) include(preset.iconX, preset.iconY, r, b);
            }
            if (!Float.isFinite(left)) { left = -20; top = -5; right = 20; bottom = 5; }
            left = (float) Math.floor(left - preset.padding); top = (float) Math.floor(top - preset.padding);
            right = (float) Math.ceil(right + preset.padding); bottom = (float) Math.ceil(bottom + preset.padding);
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

    private void setText(Font renderer, int index, String value) {
        if (text[index] != null && text[index].getString().equals(value)) return;
        text[index] = Component.literal(value);
        widths[index] = renderer.width(text[index]);
    }

    private WaypointPreset.TextPart part(int index) {
        return switch (index) { case 0 -> preset.label; case 1 -> preset.distance;
            case 2 -> preset.coordinates; default -> preset.profile; };
    }

    private void includeContent(float l, float t, float r, float b) {
        contentRadius = Math.max(contentRadius,
                Math.max(Math.max(Math.abs(l), Math.abs(r)), Math.max(Math.abs(t), Math.abs(b))));
    }

    private void include(float l, float t, float r, float b) {
        left = Math.min(left, l); top = Math.min(top, t); right = Math.max(right, r); bottom = Math.max(bottom, b);
    }

    void drawPanel(net.minecraft.client.renderer.OrderedSubmitNodeCollector queue) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(matrix);
        PanelQuad[] framePanels = panels;
        int frameMarker = marker, frameBackground = background;
        net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer geometry = (entry, vertices) -> {
            for (PanelQuad quad : framePanels) {
                int color = quad.border ? frameMarker : frameBackground;
                if ((color >>> 24) == 0) continue;
                vertices.addVertex(entry.pose(), quad.left, quad.top, 0.01f).setColor(color).setLight(0xF000F0);
                vertices.addVertex(entry.pose(), quad.left, quad.bottom, 0.01f).setColor(color).setLight(0xF000F0);
                vertices.addVertex(entry.pose(), quad.right, quad.bottom, 0.01f).setColor(color).setLight(0xF000F0);
                vertices.addVertex(entry.pose(), quad.right, quad.top, 0.01f).setColor(color).setLight(0xF000F0);
            }
        };
        if (WaypointHudRenderer.phased()) {
            queue.submitCustom(net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases.ALWAYS_ON_TOP,
                    new net.minecraft.client.renderer.feature.CustomFeatureRenderer.Submit(pose.last().copy(), RenderTypes.textBackgroundSeeThrough(), geometry));
        } else queue.submitCustomGeometry(pose, RenderTypes.textBackgroundSeeThrough(), geometry);
    }

    void drawText(Font renderer, net.minecraft.client.renderer.OrderedSubmitNodeCollector queue) {
        for (int i = 0; i < text.length; i++) {
            if (!enabled[i]) continue;
            if (!WaypointHudRenderer.phased()) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(matrix).mul(textMatrices[i]);
                queue.submitText(pose, 0, 0, text[i].getVisualOrderText(), false,
                        Font.DisplayMode.SEE_THROUGH, 0xF000F0, textColor, 0, 0);
                continue;
            }
            queue.submitCustom(net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases.ALWAYS_ON_TOP,
                    new net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit(new Matrix4f(matrix).mul(textMatrices[i]),
                    0, 0, text[i].getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, 0xF000F0, textColor, 0, 0));
        }
    }

    private void buildPanel() {
        java.util.ArrayList<PanelQuad> quads = new java.util.ArrayList<>(16);
        if (preset != null) {
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
