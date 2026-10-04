package pl.slogerski.waypointsplus.fabric;

public final class PresetLayoutCheck {
    private PresetLayoutCheck() {
    }

    public static void main(String[] args) {
        WaypointPreset preset = new WaypointPreset();
        preset.padding = 0;
        WaypointPresetLayout layout = new WaypointPresetLayout();
        layout.setText(preset, 0, -20, 10, 40, 9, true);
        layout.resolve(preset);
        equal(-21, layout.left(0), "Text left margin");
        equal(21, layout.right(0), "Text right margin");
        equal(6, layout.top(0), "Text top margin");
        equal(21, layout.bottom(0), "Text bottom margin");
        equal(0, layout.centerX(0), "Text horizontal origin");
        equal(10, layout.textY(preset, 0), "Text vertical origin");
        equal(-21, layout.left(5), "Zero-padding background left");
        equal(21, layout.right(5), "Zero-padding background right");
        equal(6, layout.top(5), "Zero-padding background top");
        equal(21, layout.bottom(5), "Zero-padding background bottom");

        preset.labelLinked = false;
        layout.resolve(preset);
        equal(-5, layout.top(5), "Unlinked text does not expand the background");
        preset.labelLinked = true;
        preset.icon = true;
        preset.iconLinked = false;
        layout.set(4, 50, 40, 16, 16, true);
        preset.label.vertical = new WaypointPreset.Anchor(4, 2, 3, -2);
        layout.setText(preset, 0, -20, 10, 40, 9, true);
        layout.resolve(preset);
        equal(38, layout.bottom(0), "Text attachment uses the expanded bounds");
        equal(27, layout.textY(preset, 0), "Attached text vertical origin");
        equal(40, layout.top(4), "Icon bounds unchanged");
        if (!layout.detach(preset, 0, 1)) throw new AssertionError("Text detach failed");
        equal(27, preset.label.y, "Detached text keeps its visible position");
        layout.setText(preset, 0, -20, preset.label.y, 40, 9, true);
        layout.resolve(preset);
        equal(27, layout.textY(preset, 0), "Text position after resolving detached layout");
        equal(38, layout.bottom(0), "Bounds after detaching");

        preset.padding = 2;
        layout.setText(preset, 0, -10, 10, 20, 4.5f, true);
        layout.resolve(preset);
        equal(16.5f, layout.bottom(0), "Scaled text bottom margin");
        equal(4, layout.top(5), "Padding added outside the text bounds");
        equal(19, layout.bottom(5), "Fractional bounds round outward");
        designedLayout();
    }

    private static void designedLayout() {
        WaypointPreset preset = WaypointPreset.designed();
        WaypointPresetLayout layout = new WaypointPresetLayout();
        WaypointPresetLayout previous = new WaypointPresetLayout();
        for (int i = 0; i < 4; i++) {
            WaypointPreset.TextPart part = preset.part(i);
            float width = (40 + i * 10) * part.scale;
            layout.setText(preset, i, part.x - width / 2, part.y, width, 9 * part.scale, part.enabled);
            previous.set(i, part.x - width / 2, part.y, width, 9 * part.scale, part.enabled);
        }
        layout.set(4, preset.iconX, preset.iconY, 16 * preset.iconScale, 16 * preset.iconScale, preset.icon);
        previous.set(4, preset.iconX, preset.iconY, 16 * preset.iconScale, 16 * preset.iconScale, preset.icon);
        layout.resolve(preset);
        previous.resolve(preset);
        for (int i = 0; i <= WaypointPresetLayout.BORDER; i++) {
            equal(previous.left(i), layout.left(i), "Designed left " + i);
            equal(previous.right(i), layout.right(i), "Designed right " + i);
            equal(previous.top(i), layout.top(i), "Designed top " + i);
            equal(previous.bottom(i), layout.bottom(i), "Designed bottom " + i);
            if (i < 4) equal(previous.top(i), layout.textY(preset, i), "Designed text origin " + i);
        }
        float distanceY = previous.top(1);
        if (!layout.detach(preset, 1, 1)) throw new AssertionError("Designed text detach failed");
        equal(distanceY, preset.distance.y, "Designed position after detaching");
    }

    private static void equal(float expected, float actual, String scenario) {
        if (Math.abs(expected - actual) > 0.0001f) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
    }
}
