package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

final class AdvancedSettingsScreen extends Screen {
    private static final int CONTENT_HEIGHT = 205;
    private static final int SCROLL_STEP = 18;
    private static final int SCROLL_TRACK = 0x805A5A5A;
    private static final int SCROLL_THUMB = 0xFF969696;
    private static final int SCROLL_THUMB_HOVERED = 0xFFB8B8B8;
    private static final int SEPARATOR_COLOR = 0x605A5A5A;

    private final WaypointSettingsScreen settingsScreen;
    private final List<ScrollEntry> scrollWidgets = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private Button saveButton;
    private EditBox scale;
    private EditBox textColor;
    private EditBox backgroundColor;
    private EditBox markerTint;
    private int left;
    private int top;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    AdvancedSettingsScreen(WaypointSettingsScreen settingsScreen) {
        super(Component.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")));
        this.settingsScreen = settingsScreen;
    }

    @Override protected void init() {
        scrollWidgets.clear();
        fixedWidgets.clear();
        left = width / 2 - 158;
        top = Math.max(2, (height - 258) / 2);
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        scrollChild(Button.builder(Component.literal(UiText.get("Fights Alerts", "Alerty walki")),
                button -> minecraft.gui.setScreen(new FightAlertsScreen(this)))
                .pos(left + 10, 0).size(296, 18).build(), 6);
        scrollChild(Button.builder(Component.literal(settings.menuBackground ? "ON" : "OFF"),
                button -> toggleMenuBackground(button))
                .pos(left + 224, 0).size(82, 18).build(), 31);
        scrollChild(Button.builder(Component.literal(settings.crossDimensionWaypoints ? "ON" : "OFF"),
                button -> toggleCrossDimensionWaypoints(button))
                .pos(left + 224, 0).size(82, 18).build(), 56);
        scale = field(left + 224, 81, 82, String.valueOf(settings.scale), "Scale");
        scale.setMaxLength(5);
        scale.setResponder(value -> applyScale());
        textColor = field(left + 224, 106, 82, String.format("%08X", settings.textArgb), "Text");
        textColor.setMaxLength(9);
        textColor.setResponder(value -> applyArgb(value, true));
        backgroundColor = field(left + 224, 131, 82, String.format("%08X", settings.backgroundArgb), "Background");
        backgroundColor.setMaxLength(9);
        backgroundColor.setResponder(value -> applyArgb(value, false));
        markerTint = new EditBox(font, left + 229, 0, 72, 10,
                Component.literal(UiText.get("Marker Tint", "Zabarwienie znacznika")));
        markerTint.setBordered(false);
        markerTint.setMaxLength(3);
        markerTint.setValue(Integer.toString(settings.markerTintPercent));
        markerTint.setResponder(value -> { if (isValidTint(value)) applyMarkerTint(); });
        scrollChild(markerTint, 161);
        scrollChild(Button.builder(Component.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(true))
                .pos(left + 154, 0).size(64, 18).build(), 106);
        scrollChild(Button.builder(Component.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(false))
                .pos(left + 154, 0).size(64, 18).build(), 131);
        scrollChild(Button.builder(Component.literal(settings.matchTextToBorder ? "ON" : "OFF"),
                button -> toggleTextBorderMatch(button))
                .pos(left + 224, 0).size(82, 18).build(), 181);
        fixedChild(Button.builder(Component.literal(UiText.get("Reset Settings", "Resetuj ustawienia")),
                button -> resetSettings())
                .pos(left + 94, top + 224).size(132, 20).build());
        saveButton = fixedChild(Button.builder(saveLabel(), button -> save())
                .pos(left + 10, top + 224).size(78, 20).build());
        saveButton.active = settingsScreen.hasUnsavedChanges();
        fixedChild(Button.builder(Component.literal(UiText.get("Back", "Wróć")), button -> onClose())
                .pos(left + 232, top + 224).size(74, 20).build());
        fixedChild(Button.builder(Component.literal("?"), button -> {
            String language = "pl".equals(settings.language) ? "pl" : "en";
            Util.getPlatform().openUri("https://slogerski.github.io/Waypoints-Plus/?lang="
                    + language + "#settings");
        }).pos(left + 297, top + 5).size(14, 14).build());
        setScroll(scrollOffset);
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

    private EditBox field(int x, int contentY, int width, String value, String hint) {
        EditBox field = new EditBox(font, x + 5, 0, width - 10, 10, Component.literal(hint));
        field.setBordered(false);
        field.setValue(value);
        scrollChild(field, contentY + 5);
        return field;
    }

    private <T extends AbstractWidget> T scrollChild(T widget, int contentY) {
        addRenderableWidget(widget);
        scrollWidgets.add(new ScrollEntry(widget, contentY));
        return widget;
    }

    private <T extends AbstractWidget> T fixedChild(T widget) {
        addRenderableWidget(widget);
        fixedWidgets.add(widget);
        return widget;
    }

    private int viewportTop() {
        return top + 34;
    }

    private int viewportBottom() {
        return top + 218;
    }

    private int maxScroll() {
        return Math.max(0, CONTENT_HEIGHT - (viewportBottom() - viewportTop()));
    }

    private void setScroll(double value) {
        scrollOffset = Math.max(0.0, Math.min(maxScroll(), value));
        int viewportTop = viewportTop();
        for (ScrollEntry entry : scrollWidgets) {
            entry.widget.setY(viewportTop + entry.contentY - (int)Math.round(scrollOffset));
            entry.widget.visible = entry.widget.getY() + entry.widget.getHeight() > viewportTop
                    && entry.widget.getY() < viewportBottom();
        }
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
        minecraft.gui.setScreen(new AdvancedSettingsScreen(settingsScreen));
    }

    private static boolean isValidTint(String value) {
        if (value.isEmpty()) return true;
        try {
            return Integer.parseInt(value) <= 100;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void toggleMenuBackground(Button button) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.menuBackground = !settings.menuBackground;
        button.setMessage(Component.literal(settings.menuBackground ? "ON" : "OFF"));
        markDirty();
    }

    private void toggleCrossDimensionWaypoints(Button button) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.crossDimensionWaypoints = !settings.crossDimensionWaypoints;
        button.setMessage(Component.literal(settings.crossDimensionWaypoints ? "ON" : "OFF"));
        markDirty();
    }

    private void toggleTextBorderMatch(Button button) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.matchTextToBorder = !settings.matchTextToBorder;
        button.setMessage(Component.literal(settings.matchTextToBorder ? "ON" : "OFF"));
        markDirty();
    }

    private void openColorPicker(boolean text) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int color = text ? settings.textArgb : settings.backgroundArgb;
        minecraft.gui.setScreen(new ColorPickerScreen(this, String.format("%08X", color),
                value -> applyColor(value, text), () -> resetColor(text)));
    }

    private void resetColor(boolean text) {
        applyColor(text ? "FFE0E0E0" : "E01C1C1C", text);
    }

    private void applyColor(String value, boolean text) {
        try {
            int color = (int)Long.parseLong(value.replace("#", ""), 16);
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            if (text) {
                settings.textArgb = color;
                textColor.setValue(String.format("%08X", color));
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
        int contentTop = viewportTop() - (int)Math.round(scrollOffset);
        graphics.enableScissor(left + 4, viewportTop(), left + 307, viewportBottom());
        for (int y : new int[] {51, 76, 101, 126, 151, 176}) {
            drawSeparator(graphics, left + 12, contentTop + y - 3);
        }
        GuiPalette.input(graphics, left + 224, contentTop + 81, 82, 18);
        GuiPalette.input(graphics, left + 224, contentTop + 106, 82, 18);
        GuiPalette.input(graphics, left + 224, contentTop + 131, 82, 18);
        GuiPalette.input(graphics, left + 224, contentTop + 156, 82, 18);
        graphics.text(font, UiText.get("Blurred Background", "Rozmyte tło"),
                left + 12, contentTop + 36, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Cross-dimensional", "Między wymiarami"),
                left + 12, contentTop + 61, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Scale", "Skala"), left + 12, contentTop + 86, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Default Text", "Domyślny tekst"), left + 12, contentTop + 111, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Default Background", "Domyślne tło"), left + 12, contentTop + 136, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Marker Tint (%)", "Zabarwienie znacznika (%)"),
                left + 12, contentTop + 161, 0xFFD9E2F0, true);
        graphics.text(font, UiText.get("Match Text To Border", "Dopasuj tekst do obramowania"),
                left + 12, contentTop + 186, 0xFFD9E2F0, true);
        for (ScrollEntry entry : scrollWidgets) {
            if (entry.widget.visible) entry.widget.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        graphics.disableScissor();
        for (AbstractWidget widget : fixedWidgets) widget.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0f, top + 7.0f);
        graphics.pose().scale(1.2f, 1.2f);
        graphics.centeredText(font, title, 0, 0, 0xFFFFFFFF);
        graphics.pose().popMatrix();
        drawScrollbar(graphics, mouseX, mouseY);
    }

    private void drawSeparator(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 2, y - 2, x - 1, y - 1, SEPARATOR_COLOR);
        graphics.fill(x - 2, y - 1, x, y, SEPARATOR_COLOR);
        graphics.fill(x - 1, y, x + 1, y + 1, SEPARATOR_COLOR);
        graphics.fill(x + 1, y, x + 8, y + 2, SEPARATOR_COLOR);
        graphics.fill(x, y + 1, x + 1, y + 2, SEPARATOR_COLOR);
        graphics.fill(x + 8, y, x + 16, y + 2, SEPARATOR_COLOR);
        graphics.fill(x + 16, y, x + 65, y + 1, SEPARATOR_COLOR);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + 309;
        int top = viewportTop();
        int bottom = viewportBottom();
        graphics.fill(x + 1, top, x + 3, bottom, SCROLL_TRACK);
        int thumbTop = scrollbarThumbTop();
        int thumbBottom = thumbTop + scrollbarThumbHeight();
        int color = mouseX >= x && mouseX < x + 4 && mouseY >= thumbTop && mouseY < thumbBottom
                ? SCROLL_THUMB_HOVERED : SCROLL_THUMB;
        graphics.fill(x + 1, thumbTop, x + 3, thumbBottom, color);
        if (thumbBottom - thumbTop > 2) graphics.fill(x, thumbTop + 1, x + 4, thumbBottom - 1, color);
    }

    private int scrollbarThumbHeight() {
        int viewportHeight = viewportBottom() - viewportTop();
        return Math.min(viewportHeight, Math.max(24, viewportHeight * viewportHeight / CONTENT_HEIGHT));
    }

    private int scrollbarThumbTop() {
        int travel = viewportBottom() - viewportTop() - scrollbarThumbHeight();
        if (maxScroll() == 0) return viewportTop();
        return viewportTop() + (int)Math.round(travel * scrollOffset / maxScroll());
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= left && mouseX < left + 316 && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            setScroll(scrollOffset - verticalAmount * SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && mouseX >= left + 307 && mouseX < left + 315
                && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            int thumbTop = scrollbarThumbTop();
            int thumbBottom = thumbTop + scrollbarThumbHeight();
            if (mouseY >= thumbTop && mouseY < thumbBottom) {
                scrollbarGrabOffset = mouseY - thumbTop;
            } else {
                scrollbarGrabOffset = scrollbarThumbHeight() / 2.0;
                scrollFromMouse(mouseY);
            }
            draggingScrollbar = true;
            return true;
        }
        if (mouseY < viewportTop() || mouseY >= viewportBottom()) {
            for (ScrollEntry entry : scrollWidgets) entry.widget.visible = false;
            boolean handled = super.mouseClicked(event, doubled);
            setScroll(scrollOffset);
            return handled;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && draggingScrollbar) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void scrollFromMouse(double mouseY) {
        int travel = viewportBottom() - viewportTop() - scrollbarThumbHeight();
        if (travel <= 0) return;
        double position = mouseY - viewportTop() - scrollbarGrabOffset;
        setScroll(position / travel * maxScroll());
    }

    @Override public void onClose() {
        minecraft.gui.setScreen(settingsScreen);
    }

    private record ScrollEntry(AbstractWidget widget, int contentY) { }
}
