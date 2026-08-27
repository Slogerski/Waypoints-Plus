package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AdvancedSettingsScreen extends Screen {
    private static final int FIELD_ACCENT = 0xFFD946EF;
    private final WaypointSettingsScreen settingsScreen;
    private Button saveButton;
    private EditBox scale;
    private EditBox markerColor;
    private EditBox backgroundColor;
    private EditBox markerTint;
    private String validTint;
    private int left;
    private int top;

    AdvancedSettingsScreen(WaypointSettingsScreen settingsScreen) {
        super(Component.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")));
        this.settingsScreen = settingsScreen;
    }

    @Override protected void init() {
        left = width / 2 - 158;
        top = Math.max(2, (height - 258) / 2);
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        addRenderableWidget(Button.builder(Component.literal(
                        UiText.get("Blurred Background", "Rozmyte tło") + ": " + (settings.menuBackground ? "ON" : "OFF")),
                button -> toggleMenuBackground())
                .pos(left + 10, top + 40).size(144, 20).build());
        addRenderableWidget(Button.builder(Component.literal(
                        UiText.get("Cross-dimensional", "Między wymiarami") + ": "
                                + (settings.crossDimensionWaypoints ? "ON" : "OFF")),
                button -> toggleCrossDimensionWaypoints())
                .pos(left + 162, top + 40).size(144, 20).build());
        scale = field(left + 224, top + 74, 82, String.valueOf(settings.scale), "Scale");
        scale.setMaxLength(5);
        scale.setResponder(value -> applyScale());
        markerColor = field(left + 224, top + 104, 82, String.format("%08X", settings.markerArgb), "Marker");
        markerColor.setMaxLength(9);
        markerColor.setResponder(value -> applyArgb(value, true));
        backgroundColor = field(left + 224, top + 134, 82, String.format("%08X", settings.backgroundArgb), "Background");
        backgroundColor.setMaxLength(9);
        backgroundColor.setResponder(value -> applyArgb(value, false));
        markerTint = new EditBox(font, left + 229, top + 170, 72, 10,
                Component.literal(UiText.get("Marker Tint", "Zabarwienie znacznika")));
        markerTint.setBordered(false);
        markerTint.setMaxLength(3);
        markerTint.setValue(Integer.toString(settings.markerTintPercent));
        validTint = markerTint.getValue();
        markerTint.setResponder(value -> {
            if (isValidTint(value)) {
                validTint = value;
                applyMarkerTint();
            } else {
                markerTint.setValue(validTint);
            }
        });
        addRenderableWidget(markerTint);
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(true))
                .pos(left + 154, top + 104).size(64, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(false))
                .pos(left + 154, top + 134).size(64, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Reset Settings", "Resetuj ustawienia")),
                button -> resetSettings())
                .pos(left + 10, top + 200).size(296, 20).build());
        saveButton = addRenderableWidget(Button.builder(saveLabel(), button -> save())
                .pos(left + 10, top + 224).size(144, 20).build());
        saveButton.active = settingsScreen.hasUnsavedChanges();
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Back", "Wróć")), button -> onClose())
                .pos(left + 162, top + 224).size(144, 20).build());
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

    private EditBox field(int x, int y, int width, String value, String hint) {
        EditBox field = new EditBox(font, x + 5, y + 6, width - 10, 10, Component.literal(hint));
        field.setBordered(false);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void applyScale() {
        if (scale == null || scale.getValue().isEmpty()) return;
        try {
            float value = Math.max(0.25f, Math.min(4.0f, Float.parseFloat(scale.getValue().replace(',', '.'))));
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
        if (markerTint == null || markerTint.getValue().isEmpty()) return;
        try {
            int value = Math.max(0, Math.min(100, Integer.parseInt(markerTint.getValue())));
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
        minecraft.setScreen(new AdvancedSettingsScreen(settingsScreen));
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
        minecraft.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private void toggleCrossDimensionWaypoints() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.crossDimensionWaypoints = !settings.crossDimensionWaypoints;
        settingsScreen.markDirty();
        minecraft.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private void openColorPicker(boolean marker) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int color = marker ? settings.markerArgb : settings.backgroundArgb;
        minecraft.setScreen(new ColorPickerScreen(this, String.format("%08X", color),
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
                markerColor.setValue(String.format("%08X", color));
            } else {
                settings.backgroundArgb = color;
                backgroundColor.setValue(String.format("%08X", color));
            }
            markDirty();
        } catch (NumberFormatException ignored) { }
    }

    private Component saveLabel() {
        return Component.literal(settingsScreen.hasUnsavedChanges()
                ? UiText.get("Not Saved", "Niezapisane")
                : UiText.get("Saved", "Zapisano"));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(graphics, left, top, left + 316, top + 256);
        GuiPalette.input(graphics, left + 224, top + 74, 82, 20);
        GuiPalette.input(graphics, left + 224, top + 104, 82, 20);
        GuiPalette.input(graphics, left + 224, top + 134, 82, 20);
        GuiPalette.input(graphics, left + 224, top + 164, 82, 20);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0f, top + 7.0f);
        graphics.pose().scale(1.2f, 1.2f);
        graphics.centeredText(font, title, 0, 0, 0xFFFFFFFF);
        graphics.pose().popMatrix();
        graphics.text(font, UiText.get("Scale", "Skala"), left + 12, top + 80, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Default Marker", "Domyślny znacznik"), left + 12, top + 110, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Default Background", "Domyślne tło"), left + 12, top + 140, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Marker Tint (%)", "Zabarwienie znacznika (%)"),
                left + 12, top + 170, 0xFFD9E2F0, true);
    }

    @Override public void onClose() {
        minecraft.setScreen(settingsScreen);
    }
}
