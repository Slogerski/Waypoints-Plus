package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class AdvancedSettingsScreen extends Screen {
    private final WaypointSettingsScreen settingsScreen;
    private ButtonWidget saveButton;
    private TextFieldWidget scale;
    private TextFieldWidget textColor;
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
                        UiText.get("Blurred Background", "Rozmyte tło") + ": " + (settings.menuBackground ? "ON" : "OFF")),
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
        textColor = field(left + 224, top + 104, 82, String.format("%08X", settings.textArgb), "Text");
        textColor.setMaxLength(9);
        textColor.setChangedListener(value -> applyArgb(value, true));
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
        addDrawableChild(ButtonWidget.builder(Text.literal(
                        UiText.get("Match Text To Border", "Dopasuj tekst do obramowania")
                                + ": " + (settings.matchTextToBorder ? "ON" : "OFF")),
                button -> toggleTextBorderMatch())
                .dimensions(left + 10, top + 200, 296, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Reset Settings", "Resetuj ustawienia")),
                button -> resetSettings())
                .dimensions(left + 94, top + 224, 132, 20).build());
        saveButton = addDrawableChild(ButtonWidget.builder(saveLabel(), button -> save())
                .dimensions(left + 10, top + 224, 78, 20).build());
        saveButton.active = settingsScreen.hasUnsavedChanges();
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wróć")), button -> close())
                .dimensions(left + 232, top + 224, 74, 20).build());
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

    private void applyArgb(String value, boolean text) {
        String hex = value.replace("#", "");
        if (hex.length() != 8) return;
        try {
            int color = (int)Long.parseLong(hex, 16);
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if ((text ? settings.textArgb : settings.backgroundArgb) != color) {
                if (text) settings.textArgb = color;
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

    private void toggleTextBorderMatch() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.matchTextToBorder = !settings.matchTextToBorder;
        settingsScreen.markDirty();
        client.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private void openColorPicker(boolean text) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int color = text ? settings.textArgb : settings.backgroundArgb;
        client.setScreen(new ColorPickerScreen(this, String.format("%08X", color),
                value -> applyColor(value, text), () -> resetColor(text)));
    }

    private void resetColor(boolean text) {
        applyColor(text ? "FFFFFFFF" : "E01C1C1C", text);
    }

    private void applyColor(String value, boolean text) {
        try {
            int color = (int)Long.parseLong(value.replace("#", ""), 16);
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if (text) {
                settings.textArgb = color;
                textColor.setText(String.format("%08X", color));
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

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        GuiPalette.panel(context, left, top, left + 316, top + 256);
        GuiPalette.input(context, left + 224, top + 74, 82, 20);
        GuiPalette.input(context, left + 224, top + 104, 82, 20);
        GuiPalette.input(context, left + 224, top + 134, 82, 20);
        GuiPalette.input(context, left + 224, top + 164, 82, 20);
        super.render(context, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, textRenderer, title, width / 2.0f, top + 7.0f);
        context.drawTextWithShadow(textRenderer, UiText.get("Scale", "Skala"), left + 12, top + 80, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Text", "Domyślny tekst"), left + 12, top + 110, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Background", "Domyślne tło"), left + 12, top + 140, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Marker Tint (%)", "Zabarwienie znacznika (%)"),
                left + 12, top + 170, 0xFFD9E2F0);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void close() {
        client.setScreen(settingsScreen);
    }
}
