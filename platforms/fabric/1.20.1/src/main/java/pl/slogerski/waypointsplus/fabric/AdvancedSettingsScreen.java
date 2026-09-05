package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

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
    private final List<ClickableWidget> fixedWidgets = new ArrayList<>();
    private ButtonWidget saveButton;
    private TextFieldWidget scale;
    private TextFieldWidget textColor;
    private TextFieldWidget backgroundColor;
    private TextFieldWidget markerTint;
    private int left;
    private int top;
    private double scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    AdvancedSettingsScreen(WaypointSettingsScreen settingsScreen) {
        super(Text.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")));
        this.settingsScreen = settingsScreen;
    }

    @Override protected void init() {
        scrollWidgets.clear();
        fixedWidgets.clear();
        left = width / 2 - 158;
        top = Math.max(2, (height - 258) / 2);
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        scrollChild(ButtonWidget.builder(Text.literal(UiText.get("Fights Alerts", "Alerty walki")),
                button -> client.setScreen(new FightAlertsScreen(this)))
                .dimensions(left + 10, 0, 296, 18).build(), 6);
        scrollChild(ButtonWidget.builder(Text.literal(settings.menuBackground ? "ON" : "OFF"),
                button -> toggleMenuBackground(button))
                .dimensions(left + 224, 0, 82, 18).build(), 31);
        scrollChild(ButtonWidget.builder(Text.literal(settings.crossDimensionWaypoints ? "ON" : "OFF"),
                button -> toggleCrossDimensionWaypoints(button))
                .dimensions(left + 224, 0, 82, 18).build(), 56);
        scale = field(left + 224, 81, 82, String.valueOf(settings.scale), "Scale");
        scale.setMaxLength(5);
        scale.setChangedListener(value -> applyScale());
        textColor = field(left + 224, 106, 82, String.format("%08X", settings.textArgb), "Text");
        textColor.setMaxLength(9);
        textColor.setChangedListener(value -> applyArgb(value, true));
        backgroundColor = field(left + 224, 131, 82, String.format("%08X", settings.backgroundArgb), "Background");
        backgroundColor.setMaxLength(9);
        backgroundColor.setChangedListener(value -> applyArgb(value, false));
        markerTint = new TextFieldWidget(textRenderer, left + 229, 0, 72, 10,
                Text.literal(UiText.get("Marker Tint", "Zabarwienie znacznika")));
        markerTint.setDrawsBackground(false);
        markerTint.setMaxLength(3);
        markerTint.setTextPredicate(AdvancedSettingsScreen::isValidTint);
        markerTint.setText(Integer.toString(settings.markerTintPercent));
        markerTint.setChangedListener(value -> applyMarkerTint());
        scrollChild(markerTint, 161);
        scrollChild(ButtonWidget.builder(Text.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(true))
                .dimensions(left + 154, 0, 64, 18).build(), 106);
        scrollChild(ButtonWidget.builder(Text.literal(UiText.get("Palette", "Paleta")),
                button -> openColorPicker(false))
                .dimensions(left + 154, 0, 64, 18).build(), 131);
        scrollChild(ButtonWidget.builder(Text.literal(settings.matchTextToBorder ? "ON" : "OFF"),
                button -> toggleTextBorderMatch(button))
                .dimensions(left + 224, 0, 82, 18).build(), 181);
        fixedChild(ButtonWidget.builder(Text.literal(UiText.get("Reset Settings", "Resetuj ustawienia")),
                button -> resetSettings())
                .dimensions(left + 94, top + 224, 132, 20).build());
        saveButton = fixedChild(ButtonWidget.builder(saveLabel(), button -> save())
                .dimensions(left + 10, top + 224, 78, 20).build());
        saveButton.active = settingsScreen.hasUnsavedChanges();
        fixedChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wróć")), button -> close())
                .dimensions(left + 232, top + 224, 74, 20).build());
        fixedChild(ButtonWidget.builder(Text.literal("?"), button -> {
            String language = "pl".equals(settings.language) ? "pl" : "en";
            Util.getOperatingSystem().open("https://slogerski.github.io/Waypoints-Plus/?lang="
                    + language + "#settings");
        }).dimensions(left + 297, top + 5, 14, 14).build());
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

    private TextFieldWidget field(int x, int contentY, int width, String value, String hint) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + 5, 0, width - 10, 10, Text.literal(hint));
        field.setDrawsBackground(false);
        field.setText(value);
        scrollChild(field, contentY + 5);
        return field;
    }

    private <T extends ClickableWidget> T scrollChild(T widget, int contentY) {
        addDrawableChild(widget);
        scrollWidgets.add(new ScrollEntry(widget, contentY));
        return widget;
    }

    private <T extends ClickableWidget> T fixedChild(T widget) {
        addDrawableChild(widget);
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

    private void toggleMenuBackground(ButtonWidget button) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.menuBackground = !settings.menuBackground;
        button.setMessage(Text.literal(settings.menuBackground ? "ON" : "OFF"));
        markDirty();
    }

    private void toggleCrossDimensionWaypoints(ButtonWidget button) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.crossDimensionWaypoints = !settings.crossDimensionWaypoints;
        button.setMessage(Text.literal(settings.crossDimensionWaypoints ? "ON" : "OFF"));
        markDirty();
    }

    private void toggleTextBorderMatch(ButtonWidget button) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        settings.matchTextToBorder = !settings.matchTextToBorder;
        button.setMessage(Text.literal(settings.matchTextToBorder ? "ON" : "OFF"));
        markDirty();
    }

    private void openColorPicker(boolean text) {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        int color = text ? settings.textArgb : settings.backgroundArgb;
        client.setScreen(new ColorPickerScreen(this, String.format("%08X", color),
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
        renderBackground(context);
        GuiPalette.panel(context, left, top, left + 316, top + 256);
        int contentTop = viewportTop() - (int)Math.round(scrollOffset);
        context.enableScissor(left + 4, viewportTop(), left + 307, viewportBottom());
        for (int y : new int[] {51, 76, 101, 126, 151, 176}) {
            drawSeparator(context, left + 12, contentTop + y - 3);
        }
        GuiPalette.input(context, left + 224, contentTop + 81, 82, 18);
        GuiPalette.input(context, left + 224, contentTop + 106, 82, 18);
        GuiPalette.input(context, left + 224, contentTop + 131, 82, 18);
        GuiPalette.input(context, left + 224, contentTop + 156, 82, 18);
        context.drawTextWithShadow(textRenderer, UiText.get("Blurred Background", "Rozmyte tło"),
                left + 12, contentTop + 36, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Cross-dimensional", "Między wymiarami"),
                left + 12, contentTop + 61, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Scale", "Skala"), left + 12, contentTop + 86, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Text", "Domyślny tekst"), left + 12, contentTop + 111, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Background", "Domyślne tło"), left + 12, contentTop + 136, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Marker Tint (%)", "Zabarwienie znacznika (%)"),
                left + 12, contentTop + 161, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Match Text To Border", "Dopasuj tekst do obramowania"),
                left + 12, contentTop + 186, 0xFFD9E2F0);
        for (ScrollEntry entry : scrollWidgets) {
            if (entry.widget.visible) entry.widget.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
        for (ClickableWidget widget : fixedWidgets) widget.render(context, mouseX, mouseY, delta);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0f, top + 7.0f, 0.0f);
        context.getMatrices().scale(1.2f, 1.2f, 1.0f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFFFFFFF);
        context.getMatrices().pop();
        drawScrollbar(context, mouseX, mouseY);
    }

    private void drawSeparator(DrawContext context, int x, int y) {
        context.fill(x - 2, y - 2, x - 1, y - 1, SEPARATOR_COLOR);
        context.fill(x - 2, y - 1, x, y, SEPARATOR_COLOR);
        context.fill(x - 1, y, x + 1, y + 1, SEPARATOR_COLOR);
        context.fill(x + 1, y, x + 8, y + 2, SEPARATOR_COLOR);
        context.fill(x, y + 1, x + 1, y + 2, SEPARATOR_COLOR);
        context.fill(x + 8, y, x + 16, y + 2, SEPARATOR_COLOR);
        context.fill(x + 16, y, x + 65, y + 1, SEPARATOR_COLOR);
    }

    private void drawScrollbar(DrawContext context, int mouseX, int mouseY) {
        int x = left + 309;
        int top = viewportTop();
        int bottom = viewportBottom();
        context.fill(x + 1, top, x + 3, bottom, SCROLL_TRACK);
        int thumbTop = scrollbarThumbTop();
        int thumbBottom = thumbTop + scrollbarThumbHeight();
        int color = mouseX >= x && mouseX < x + 4 && mouseY >= thumbTop && mouseY < thumbBottom
                ? SCROLL_THUMB_HOVERED : SCROLL_THUMB;
        context.fill(x + 1, thumbTop, x + 3, thumbBottom, color);
        if (thumbBottom - thumbTop > 2) context.fill(x, thumbTop + 1, x + 4, thumbBottom - 1, color);
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

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (mouseX >= left && mouseX < left + 316 && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            setScroll(scrollOffset - verticalAmount * SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            setScroll(scrollOffset);
            return handled;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollbar) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollFromMouse(double mouseY) {
        int travel = viewportBottom() - viewportTop() - scrollbarThumbHeight();
        if (travel <= 0) return;
        double position = mouseY - viewportTop() - scrollbarGrabOffset;
        setScroll(position / travel * maxScroll());
    }

    @Override public void renderBackground(DrawContext context) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) super.renderBackground(context);
    }

    @Override public void close() {
        client.setScreen(settingsScreen);
    }

    private record ScrollEntry(ClickableWidget widget, int contentY) { }
}
