package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class ColorPickerScreen extends Screen {
    private static final Pattern CUSTOM_DIMENSION = Pattern.compile("[a-z0-9_./-]+");
    private final Screen parent;
    private final Consumer<String> onApply;
    private final Consumer<String> onMarker;
    private final Consumer<String> onBackground;
    private final Runnable onReset;
    private final BiConsumer<String, String> onWaypointSettings;
    private float hue, saturation, brightness, alpha;
    private int dragging;
    private String dimensionMode;
    private String customNamespace = "minecraft";
    private String initialCustomDimension = "";
    private EditBox colorInput;
    private EditBox dimensionInput;
    private Button applyButton;
    private Button rememberButton;
    private Button overworldButton;
    private Button netherButton;
    private Button endButton;
    private Button customButton;

    ColorPickerScreen(Screen parent, String argb, Consumer<String> onApply) {
        this(parent, argb, onApply, null, null, null, null, null);
    }

    ColorPickerScreen(Screen parent, String argb, Consumer<String> onMarker, Consumer<String> onBackground) {
        this(parent, argb, null, onMarker, onBackground, null, null, null);
    }

    ColorPickerScreen(Screen parent, String argb, Consumer<String> onApply, Runnable onReset) {
        this(parent, argb, onApply, null, null, onReset, null, null);
    }

    ColorPickerScreen(Screen parent, String argb, String dimension,
                      BiConsumer<String, String> onWaypointSettings) {
        this(parent, argb, null, null, null, null, dimension, onWaypointSettings);
    }

    private ColorPickerScreen(Screen parent, String argb, Consumer<String> onApply,
                              Consumer<String> onMarker, Consumer<String> onBackground, Runnable onReset,
                              String dimension, BiConsumer<String, String> onWaypointSettings) {
        super(Component.literal(onWaypointSettings == null
                ? UiText.get("Color Picker", "Wybór koloru")
                : UiText.get("Waypoint Settings", "Ustawienia waypointa")));
        this.parent = parent;
        this.onApply = onApply;
        this.onMarker = onMarker;
        this.onBackground = onBackground;
        this.onReset = onReset;
        this.onWaypointSettings = onWaypointSettings;
        selectInitialDimension(dimension);
        long value = Long.parseLong(argb, 16);
        float[] hsb = Color.RGBtoHSB((int)(value >> 16) & 255, (int)(value >> 8) & 255, (int)value & 255, null);
        hue = hsb[0]; saturation = hsb[1]; brightness = hsb[2]; alpha = ((value >> 24) & 255) / 255.0f;
    }

    @Override protected void init() {
        if (onWaypointSettings != null) {
            initWaypointSettings();
            return;
        }
        int left = width / 2 - 120;
        if (onMarker != null) {
            int buttonLeft = width / 2 - 150;
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Set Default Marker", "Ustaw domyślny znacznik")),
                    b -> assign(onMarker)).pos(buttonLeft, 190).size(146, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Set Default Background", "Ustaw domyślne tło")),
                    b -> assign(onBackground)).pos(buttonLeft + 154, 190).size(146, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Copy", "Kopiuj")),
                    b -> minecraft.keyboardHandler.setClipboard("#" + value())).pos(buttonLeft, 214).size(146, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Back", "Wróć")),
                    b -> onClose()).pos(buttonLeft + 154, 214).size(146, 20).build());
            return;
        }
        if (onReset != null) {
            int buttonLeft = width / 2 - 150;
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Apply", "Zastosuj")), b -> apply())
                    .pos(buttonLeft, 210).size(92, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Reset", "Resetuj")), b -> resetColor())
                    .pos(buttonLeft + 100, 210).size(100, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")), b -> onClose())
                    .pos(buttonLeft + 208, 210).size(92, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Apply", "Zastosuj")), b -> apply())
                    .pos(left, 210).size(116, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")), b -> onClose())
                    .pos(left + 124, 210).size(116, 20).build());
        }
    }

    private void initWaypointSettings() {
        int left = pickerLeft();
        int panelLeft = left + 190;
        colorInput = new EditBox(font, panelLeft + 12, 76, 186, 10,
                Component.literal(UiText.get("ARGB Color", "Kolor ARGB")));
        colorInput.setBordered(false);
        colorInput.setMaxLength(9);
        colorInput.setValue("#" + value());
        colorInput.setResponder(value -> refreshApplyState());
        addRenderableWidget(colorInput);

        addRenderableWidget(Button.builder(Component.literal(UiText.get("Copy", "Kopiuj")),
                button -> minecraft.keyboardHandler.setClipboard(colorInput.getValue()))
                .pos(panelLeft + 10, 98).size(92, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Paste", "Wklej")), button -> {
            colorInput.setValue(minecraft.keyboardHandler.getClipboard().trim());
            applyInputColor();
        }).pos(panelLeft + 106, 98).size(92, 20).build());
        rememberButton = addRenderableWidget(Button.builder(Component.literal(UiText.get("Remember", "Zapamiętaj")),
                button -> rememberDefaultColor()).pos(panelLeft + 68, 146).size(130, 20).build());

        int buttonWidth = 96;
        overworldButton = dimensionButton("Overworld", "Overworld", left, 210, buttonWidth, "overworld");
        netherButton = dimensionButton("Nether", "Nether", left + 102, 210, buttonWidth, "nether");
        endButton = dimensionButton("End", "End", left + 204, 210, buttonWidth, "end");
        customButton = dimensionButton("Custom", "Własny", left + 306, 210, buttonWidth, "custom");

        dimensionInput = new EditBox(font, left + 10, 250, 280, 10,
                Component.literal(UiText.get("Custom Dimension", "Własny wymiar")));
        dimensionInput.setBordered(false);
        dimensionInput.setMaxLength(128);
        dimensionInput.setValue(initialCustomDimension);
        dimensionInput.setResponder(value -> refreshApplyState());
        addRenderableWidget(dimensionInput);

        applyButton = addRenderableWidget(Button.builder(Component.literal(UiText.get("Apply", "Zastosuj")),
                button -> applyWaypointSettings()).pos(left + 306, 242).size(96, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")), button -> onClose())
                .pos(left + 306, 268).size(96, 20).build());
        refreshDimensionButtons();
        refreshApplyState();
    }

    private Button dimensionButton(String english, String polish, int x, int y, int width, String mode) {
        return addRenderableWidget(Button.builder(Component.literal(UiText.get(english, polish)), button -> {
            dimensionMode = mode;
            refreshDimensionButtons();
            refreshApplyState();
            if ("custom".equals(mode)) setInitialFocus(dimensionInput);
        }).pos(x, y).size(width, 20).build());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && selectHistoryColor(event.x(), event.y())) return true;
        if (event.button() == 0 && beginDrag(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && dragging != 0) { updateSelection(event.x(), event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    private boolean beginDrag(double x, double y) {
        int left = pickerLeft();
        if (inside(x, y, left, 46, 180, 100)) dragging = 1;
        else if (inside(x, y, left, 153, 180, 12)) dragging = 2;
        else if (inside(x, y, left, 174, 180, 12)) dragging = 3;
        else return false;
        updateSelection(x, y);
        return true;
    }

    private void updateSelection(double x, double y) {
        int left = pickerLeft();
        if (dragging == 1) {
            saturation = clamp((float)((x - left) / 180.0));
            brightness = 1.0f - clamp((float)((y - 46) / 100.0));
        } else if (dragging == 2) hue = clamp((float)((x - left) / 180.0));
        else if (dragging == 3) alpha = clamp((float)((x - left) / 180.0));
        if (colorInput != null) colorInput.setValue("#" + value());
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int left = pickerLeft();
        if (onWaypointSettings != null) {
            int panelLeft = left + 190;
            GuiPalette.panel(graphics, panelLeft, 46, panelLeft + 238, 186);
            GuiPalette.input(graphics, panelLeft + 10, 68, 190, 24);
            if ("custom".equals(dimensionMode)) GuiPalette.input(graphics, left, 242, 300, 24);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        int colorStep = pl.slogerski.waypointsplus.core.UiRenderBudget.isResizeActive(this) ? 8 : 4;
        int sliderStep = colorStep / 2;
        for (int x = 0; x < 180; x += colorStep) for (int y = 0; y < 100; y += colorStep) {
            int rgb = Color.HSBtoRGB(hue, x / 179.0f, 1.0f - y / 99.0f);
            graphics.fill(left + x, 46 + y, left + Math.min(180, x + colorStep),
                    46 + Math.min(100, y + colorStep), 0xFF000000 | (rgb & 0xFFFFFF));
        }
        for (int x = 0; x < 180; x += sliderStep) {
            int rgb = Color.HSBtoRGB(x / 179.0f, 1, 1);
            graphics.fill(left + x, 153, left + Math.min(180, x + sliderStep), 165, 0xFF000000 | (rgb & 0xFFFFFF));
            int a = Math.round(x / 179.0f * 255);
            graphics.fill(left + x, 174, left + Math.min(180, x + sliderStep), 186, (a << 24) | rgbValue());
        }
        marker(graphics, left + Math.round(saturation * 179), 46 + Math.round((1 - brightness) * 99), true);
        marker(graphics, left + Math.round(hue * 179), 159, false);
        marker(graphics, left + Math.round(alpha * 179), 180, false);
        if (onWaypointSettings != null) {
            int panelLeft = left + 190;
            graphics.centeredText(font, Component.literal(UiText.get("Color Value", "Wartość koloru")),
                    panelLeft + 105, 54, 0xFFFFFFFF);
            graphics.fill(panelLeft + 10, 126, panelLeft + 58, 174, argb());
            graphics.text(font,
                    UiText.get("Opacity: ", "Przezroczystość: ") + Math.round(alpha * 100) + "%",
                    panelLeft + 68, 128, 0xFFFFFFFF, true);
            drawColorHistory(graphics, panelLeft + 216, 70);
            graphics.text(font, UiText.get("Dimension", "Wymiar"), left, 197, 0xFFD9E2F0, true);
            if ("custom".equals(dimensionMode)) {
                graphics.text(font, UiText.get("Custom Dimension", "Własny wymiar"),
                        left, 233, 0xFFD9E2F0, true);
            }
        } else {
            graphics.fill(left + 190, 46, left + 240, 96, argb());
            graphics.text(font, "#" + String.format("%08X", argb()), left + 185, 106, 0xFFFFFFFF, true);
            graphics.text(font,
                    UiText.get("Opacity: ", "Przezroczystość: ") + Math.round(alpha * 100) + "%",
                    left + 185, 122, 0xFFFFFFFF, true);
        }
    }

    private void marker(GuiGraphicsExtractor c, int x, int y, boolean circle) {
        int half = circle ? 4 : 2;
        c.fill(x - half - 1, y - half - 1, x + half + 2, y + half + 2, 0xFF000000);
        c.fill(x - half, y - half, x + half + 1, y + half + 1, 0xFFFFFFFF);
    }

    private int rgbValue() { return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF; }
    private int argb() { return (Math.round(alpha * 255) << 24) | rgbValue(); }
    private String value() { return String.format("%08X", argb()); }
    private void apply() { onApply.accept(value()); Minecraft.getInstance().gui.setScreen(parent); }
    private void resetColor() { onReset.run(); Minecraft.getInstance().gui.setScreen(parent); }
    private void assign(Consumer<String> target) { target.accept(value()); Minecraft.getInstance().gui.setScreen(parent); }
    private int pickerLeft() { return onWaypointSettings == null ? width / 2 - 120 : width / 2 - 201; }

    private void selectInitialDimension(String dimension) {
        if (dimension == null || dimension.equals("minecraft:overworld")) dimensionMode = "overworld";
        else if (dimension.equals("minecraft:the_nether")) dimensionMode = "nether";
        else if (dimension.equals("minecraft:the_end")) dimensionMode = "end";
        else {
            dimensionMode = "custom";
            int separator = dimension.indexOf(':');
            if (separator > 0) {
                customNamespace = dimension.substring(0, separator);
                initialCustomDimension = dimension.substring(separator + 1);
            } else {
                initialCustomDimension = dimension;
            }
        }
    }

    private void refreshDimensionButtons() {
        setDimensionLabel(overworldButton, "Overworld", "Overworld", "overworld");
        setDimensionLabel(netherButton, "Nether", "Nether", "nether");
        setDimensionLabel(endButton, "End", "End", "end");
        setDimensionLabel(customButton, "Custom", "Własny", "custom");
        if (dimensionInput != null) {
            dimensionInput.setEditable("custom".equals(dimensionMode));
            dimensionInput.visible = "custom".equals(dimensionMode);
        }
    }

    private void setDimensionLabel(Button button, String english, String polish, String mode) {
        String label = UiText.get(english, polish);
        button.setMessage(Component.literal(mode.equals(dimensionMode) ? "[" + label + "]" : label));
    }

    private void refreshApplyState() {
        if (applyButton != null) applyButton.active = validColorInput() && validDimension();
        if (rememberButton != null) rememberButton.active = validColorInput();
    }

    private boolean validColorInput() {
        if (colorInput == null) return true;
        return colorInput.getValue().trim().matches("#?[0-9a-fA-F]{8}");
    }

    private boolean validDimension() {
        if (!"custom".equals(dimensionMode)) return true;
        return dimensionInput != null && CUSTOM_DIMENSION.matcher(dimensionInput.getValue().trim().toLowerCase(Locale.ROOT)).matches();
    }

    private void applyInputColor() {
        if (!validColorInput()) return;
        long parsed = Long.parseLong(normalizedColor(), 16);
        float[] hsb = Color.RGBtoHSB((int)(parsed >> 16) & 255, (int)(parsed >> 8) & 255, (int)parsed & 255, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = ((parsed >> 24) & 255) / 255.0f;
    }

    private void drawColorHistory(GuiGraphicsExtractor graphics, int startX, int y) {
        List<String> colors = displayedHistoryColors();
        String selected = colorInput == null ? value() : normalizedColor();
        for (int index = 0; index < 8; index++) {
            if (index >= colors.size()) continue;
            String color = colors.get(index);
            int x = startX;
            int colorY = y + index * 14;
            int argb = (int)Long.parseLong(color, 16);
            if (color.equalsIgnoreCase(selected)) {
                graphics.fill(x, colorY, x + 10, colorY + 10, 0xD3D3AB11);
                graphics.fill(x + 1, colorY + 1, x + 9, colorY + 9, argb);
            } else {
                graphics.fill(x, colorY, x + 10, colorY + 10, argb);
            }
        }
    }

    private boolean selectHistoryColor(double mouseX, double mouseY) {
        if (onWaypointSettings == null) return false;
        int x = pickerLeft() + 406;
        List<String> colors = displayedHistoryColors();
        for (int index = 0; index < colors.size() && index < 8; index++) {
            int y = 70 + index * 14;
            if (!inside(mouseX, mouseY, x, y, 10, 10)) continue;
            colorInput.setValue("#" + colors.get(index));
            applyInputColor();
            return true;
        }
        return false;
    }

    private List<String> displayedHistoryColors() {
        List<String> colors = new ArrayList<>();
        appendColors(colors, WaypointsPlusClient.config().settings().waypointColorHistory);
        appendColor(colors, String.format("%08X", WaypointsPlusClient.config().settings().markerArgb));
        appendColor(colors, "F3DF0707");
        appendColor(colors, "F33BEB14");
        appendColor(colors, "F31F34F0");
        appendColor(colors, "FFAD10BF");
        appendColor(colors, "FFF418D9");
        appendColor(colors, "FFFF7500");
        return colors.subList(0, Math.min(8, colors.size()));
    }

    private static void appendColors(List<String> target, List<String> colors) {
        for (String color : colors) appendColor(target, color);
    }

    private static void appendColor(List<String> target, String color) {
        if (target.stream().noneMatch(color::equalsIgnoreCase)) target.add(color);
    }

    private void rememberDefaultColor() {
        if (!validColorInput()) return;
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        String color = normalizedColor();
        settings.markerArgb = (int)Long.parseLong(color, 16);
        settings.rememberWaypointColor(color);
        WaypointsPlusClient.config().saveSettings();
    }

    private String normalizedColor() {
        return colorInput.getValue().trim().replace("#", "").toUpperCase(Locale.ROOT);
    }

    private String selectedDimension() {
        return switch (dimensionMode) {
            case "nether" -> "minecraft:the_nether";
            case "end" -> "minecraft:the_end";
            case "custom" -> customNamespace + ":" + dimensionInput.getValue().trim().toLowerCase(Locale.ROOT);
            default -> "minecraft:overworld";
        };
    }

    private void applyWaypointSettings() {
        if (!validColorInput() || !validDimension()) return;
        onWaypointSettings.accept(normalizedColor(), selectedDimension());
        Minecraft.getInstance().gui.setScreen(parent);
    }
    private static boolean inside(double x, double y, int bx, int by, int w, int h) { return x >= bx && x < bx + w && y >= by && y < by + h; }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
}
