package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

final class AdvancedSettingsScreen extends Screen {
    private static final int MAGENTA = 0xFFD946EF;
    private static final int FIELD_ACCENT = MAGENTA;
    private final WaypointSettingsScreen settingsScreen;
    private ButtonWidget saveButton;
    private TextFieldWidget scale;
    private TextFieldWidget markerColor;
    private TextFieldWidget backgroundColor;
    private TextFieldWidget markerTint;
    private int left;
    private int top;

    AdvancedSettingsScreen(WaypointSettingsScreen settingsScreen) {
        super(Text.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")));
        this.settingsScreen = settingsScreen;
    }

    @Override protected void init() {
        left = width / 2 - 158;
        top = Math.max(2, (height - 258) / 2);
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        addDrawableChild(ButtonWidget.builder(Text.literal(
                        UiText.get("Dark Background", "Ciemne tło") + ": " + (settings.menuBackground ? "ON" : "OFF")),
                button -> toggleMenuBackground())
                .dimensions(left + 10, top + 40, 144, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(
                        UiText.get("Cross-dimensional", "Między wymiarami") + ": "
                                + (settings.crossDimensionWaypoints ? "ON" : "OFF")),
                button -> toggleCrossDimensionWaypoints())
                .dimensions(left + 162, top + 40, 144, 20).build());
        scale = field(left + 224, top + 74, 82, String.valueOf(settings.scale), "Scale");
        scale.setMaxLength(5);
        scale.setChangedListener(value -> applyScale());
        markerColor = field(left + 224, top + 104, 82, String.format("%08X", settings.markerArgb), "Marker");
        markerColor.setMaxLength(9);
        markerColor.setChangedListener(value -> applyArgb(value, true));
        backgroundColor = field(left + 224, top + 134, 82, String.format("%08X", settings.backgroundArgb), "Background");
        backgroundColor.setMaxLength(9);
        backgroundColor.setChangedListener(value -> applyArgb(value, false));
        markerTint = new TextFieldWidget(textRenderer, left + 229, top + 170, 72, 10,
                Text.literal(UiText.get("Marker Tint", "Zabarwienie znacznika")));
        markerTint.setDrawsBackground(false);
        markerTint.setMaxLength(3);
        markerTint.setTextPredicate(AdvancedSettingsScreen::isValidTint);
        markerTint.setText(Integer.toString(settings.markerTintPercent));
        markerTint.setChangedListener(value -> applyMarkerTint());
        addDrawableChild(markerTint);
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(true))
                .dimensions(left + 154, top + 104, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(false))
                .dimensions(left + 154, top + 134, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Reset Settings", "Resetuj ustawienia")),
                button -> resetSettings())
                .dimensions(left + 10, top + 200, 296, 20).build());
        saveButton = addDrawableChild(ButtonWidget.builder(saveLabel(), button -> save())
                .dimensions(left + 10, top + 224, 144, 20).build());
        saveButton.active = settingsScreen.hasUnsavedChanges();
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wróć")), button -> close())
                .dimensions(left + 162, top + 224, 144, 20).build());
    }

    private void save() {
        settingsScreen.saveAdvanced();
        updateSaveButton();
    }

    private void updateSaveButton() {
        if (saveButton == null) return;
        saveButton.setMessage(saveLabel());
        saveButton.active = settingsScreen.hasUnsavedChanges();
    }

    private TextFieldWidget field(int x, int y, int width, String value, String hint) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + 5, y + 6, width - 10, 10, Text.literal(hint));
        field.setDrawsBackground(false);
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void applyScale() {
        if (scale == null || scale.getText().isEmpty()) return;
        try {
            float value = Math.max(0.25f, Math.min(4.0f, Float.parseFloat(scale.getText().replace(',', '.'))));
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if (Float.compare(settings.scale, value) != 0) {
                settings.scale = value;
                markDirty();
            }
        } catch (NumberFormatException ignored) { }
    }

    private void applyArgb(String value, boolean marker) {
        String hex = value.replace("#", "");
        if (hex.length() != 8) return;
        try {
            int color = (int)Long.parseLong(hex, 16);
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if ((marker ? settings.markerArgb : settings.backgroundArgb) != color) {
                if (marker) settings.markerArgb = color;
                else settings.backgroundArgb = color;
                markDirty();
            }
        } catch (NumberFormatException ignored) { }
    }

    private void markDirty() {
        settingsScreen.markDirty();
        updateSaveButton();
    }

    private void applyMarkerTint() {
        if (markerTint == null || markerTint.getText().isEmpty()) return;
        try {
            int value = Math.max(0, Math.min(100, Integer.parseInt(markerTint.getText())));
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if (settings.markerTintPercent != value) {
                settings.markerTintPercent = value;
                markDirty();
            }
        } catch (NumberFormatException ignored) { }
    }

    private void resetSettings() {
        WaypointsPlusClient.config().settings().resetDefaults();
        settingsScreen.markDirty();
        client.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private static boolean isValidTint(String value) {
        if (value.isEmpty()) return true;
        try {
            return Integer.parseInt(value) <= 100;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void toggleMenuBackground() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.menuBackground = !settings.menuBackground;
        settingsScreen.markDirty();
        client.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private void toggleCrossDimensionWaypoints() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.crossDimensionWaypoints = !settings.crossDimensionWaypoints;
        settingsScreen.markDirty();
        client.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private void openColorPicker(boolean marker) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int color = marker ? settings.markerArgb : settings.backgroundArgb;
        client.setScreen(new ColorPickerScreen(this, String.format("%08X", color),
                value -> applyColor(value, marker), () -> resetColor(marker)));
    }

    private void resetColor(boolean marker) {
        applyColor(marker ? "CCDBDBD3" : "E01C1C1C", marker);
    }

    private void applyColor(String value, boolean marker) {
        try {
            int color = (int)Long.parseLong(value.replace("#", ""), 16);
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if (marker) {
                settings.markerArgb = color;
                markerColor.setText(String.format("%08X", color));
            } else {
                settings.backgroundArgb = color;
                backgroundColor.setText(String.format("%08X", color));
            }
            markDirty();
        } catch (NumberFormatException ignored) { }
    }

    private Text saveLabel() {
        return Text.literal(settingsScreen.hasUnsavedChanges()
                ? UiText.get("Not Saved", "Niezapisane")
                : UiText.get("Saved", "Zapisano"));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        DrawContext context = new DrawContext(matrices);
        WaypointSettingsScreen.drawPanel(context, left, top, left + 316, top + 256);
        advancedFieldBox(context, left + 224, top + 74, 82, 20);
        advancedFieldBox(context, left + 224, top + 104, 82, 20);
        advancedFieldBox(context, left + 224, top + 134, 82, 20);
        advancedFieldBox(context, left + 224, top + 164, 82, 20);
        super.render(matrices, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, MAGENTA);
        context.drawTextWithShadow(textRenderer, UiText.get("Scale", "Skala"), left + 12, top + 80, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Marker", "Domyślny znacznik"), left + 12, top + 110, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Background", "Domyślne tło"), left + 12, top + 140, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Marker Tint (%)", "Zabarwienie znacznika (%)"),
                left + 12, top + 170, 0xFFD9E2F0);
    }

    private static void advancedFieldBox(DrawContext context, int x, int y, int width, int height) {
        context.fill(x + 1, y, x + width - 1, y + height, 0xA0000000);
        context.fill(x, y + 1, x + width, y + height - 1, 0xA0000000);
        context.fill(x + 2, y, x + width - 2, y + 1, FIELD_ACCENT);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, FIELD_ACCENT);
        context.fill(x, y + 2, x + 1, y + height - 2, FIELD_ACCENT);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, FIELD_ACCENT);
        context.fill(x + 1, y + 1, x + 2, y + 2, FIELD_ACCENT);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, FIELD_ACCENT);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, FIELD_ACCENT);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, FIELD_ACCENT);
    }

    @Override public void renderBackground(MatrixStack matrices) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) super.renderBackground(matrices);
    }

    @Override public void close() {
        client.setScreen(settingsScreen);
    }
}
