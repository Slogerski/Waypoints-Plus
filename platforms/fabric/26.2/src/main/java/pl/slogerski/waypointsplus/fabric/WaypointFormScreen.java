package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class WaypointFormScreen extends Screen {
    private static final Pattern COORDINATE = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private final Screen parent;
    private String pendingName, pendingX, pendingY, pendingZ;
    protected String selectedColor;
    private EditBox name, xField, yField, zField;
    private int panelLeft, panelTop;
    private String error = "";

    WaypointFormScreen(Screen parent, Component title, String name, int x, int y, int z, String color) {
        super(title);
        this.parent = parent;
        this.pendingName = name;
        this.pendingX = String.valueOf(x);
        this.pendingY = String.valueOf(y);
        this.pendingZ = String.valueOf(z);
        this.selectedColor = color;
    }

    @Override protected void init() {
        panelLeft = width / 2 - 150;
        panelTop = Math.max(14, (height - 206) / 2);
        name = field(panelLeft + 10, panelTop + 46, 280, pendingName, 64, "Name");
        xField = field(panelLeft + 10, panelTop + 88, 88, pendingX, 14, "X");
        yField = field(panelLeft + 106, panelTop + 88, 88, pendingY, 14, "Y");
        zField = field(panelLeft + 202, panelTop + 88, 88, pendingZ, 14, "Z");

        addRenderableWidget(Button.builder(Component.literal(UiText.get("Paste", "Wklej")), b -> pasteCoordinates())
                .pos(panelLeft + 10, panelTop + 120).size(94, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Choose Color", "Wybierz kolor"))
                .withColor(borderColor()), b -> {
            snapshot();
            minecraft.gui.setScreen(new ColorPickerScreen(this, selectedColor, value -> selectedColor = value));
        }).pos(panelLeft + 112, panelTop + 120).size(178, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Save", "Zapisz")), b -> save())
                .pos(panelLeft + 10, panelTop + 158).size(136, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Exit", "Wyjdź")), b -> onClose())
                .pos(panelLeft + 154, panelTop + 158).size(136, 20).build());
        setInitialFocus(name);
    }

    private EditBox field(int x, int y, int width, String value, int max, String hint) {
        EditBox field = new EditBox(font, x + 5, y + 5, width - 10, 10, Component.literal(hint));
        field.setBordered(false);
        field.setMaxLength(max);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void snapshot() {
        pendingName = name.getValue();
        pendingX = xField.getValue();
        pendingY = yField.getValue();
        pendingZ = zField.getValue();
    }

    private void pasteCoordinates() {
        Matcher matcher = COORDINATE.matcher(minecraft.keyboardHandler.getClipboard());
        String[] values = new String[3];
        for (int i = 0; i < values.length; i++) {
            if (!matcher.find()) {
                error = UiText.get("Clipboard does not contain X Y Z.", "Schowek nie zawiera X Y Z.");
                return;
            }
            values[i] = String.valueOf((int)Math.floor(Double.parseDouble(matcher.group())));
        }
        xField.setValue(values[0]);
        yField.setValue(values[1]);
        zField.setValue(values[2]);
        error = "";
    }

    private void save() {
        try {
            String waypointName = name.getValue().trim();
            if (waypointName.isEmpty()) throw new IllegalArgumentException();
            persist(waypointName, Integer.parseInt(xField.getValue()), Integer.parseInt(yField.getValue()),
                    Integer.parseInt(zField.getValue()), selectedColor);
            onClose();
        } catch (RuntimeException ignored) {
            error = UiText.get("Check the name and coordinates.", "Sprawdź nazwę i koordynaty.");
        }
    }

    protected abstract void persist(String name, int x, int y, int z, String color);

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xC0080B12);
        roundedFill(graphics, panelLeft, panelTop, panelLeft + 300, panelTop + 196, 0xE0141824);
        outline(graphics, panelLeft, panelTop, panelLeft + 300, panelTop + 196, borderColor());
        drawField(graphics, panelLeft + 10, panelTop + 46, 280, 20);
        drawField(graphics, panelLeft + 10, panelTop + 88, 88, 20);
        drawField(graphics, panelLeft + 106, panelTop + 88, 88, 20);
        drawField(graphics, panelLeft + 202, panelTop + 88, 88, 20);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, panelTop + 14, borderColor());
        graphics.text(font, UiText.get("Name", "Nazwa"), panelLeft + 10, panelTop + 34, 0xFFD9E2F0, true);
        graphics.text(font, "X", panelLeft + 10, panelTop + 76, 0xFFD9E2F0, true);
        graphics.text(font, "Y", panelLeft + 106, panelTop + 76, 0xFFD9E2F0, true);
        graphics.text(font, "Z", panelLeft + 202, panelTop + 76, 0xFFD9E2F0, true);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, panelTop + 183, 0xFFFF657A);
    }

    private void drawField(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        roundedFill(graphics, x, y, x + width, y + height, 0xA0000000);
        outline(graphics, x, y, x + width, y + height, borderColor());
    }

    private int borderColor() {
        try { return 0xFF000000 | (int)Long.parseLong(selectedColor.replace("#", "").substring(2), 16); }
        catch (RuntimeException ignored) { return 0xFF00F5FF; }
    }

    private static void roundedFill(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left + 2, top, right - 2, bottom, color);
        graphics.fill(left, top + 2, right, bottom - 2, color);
    }

    private static void outline(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left + 2, top, right - 2, top + 1, color);
        graphics.fill(left + 2, bottom - 1, right - 2, bottom, color);
        graphics.fill(left, top + 2, left + 1, bottom - 2, color);
        graphics.fill(right - 1, top + 2, right, bottom - 2, color);
        graphics.fill(left + 1, top + 1, left + 2, top + 2, color);
        graphics.fill(right - 2, top + 1, right - 1, top + 2, color);
        graphics.fill(left + 1, bottom - 2, left + 2, bottom - 1, color);
        graphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
    }

    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
}
