package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class WaypointFormScreen extends Screen {
    private static final Pattern COORDINATE = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private final Screen parent;
    private String pendingName, pendingX, pendingY, pendingZ;
    protected String selectedColor;
    private TextFieldWidget name, xField, yField, zField;
    private int panelLeft, panelTop;
    private String error = "";

    WaypointFormScreen(Screen parent, Text title, String name, int x, int y, int z, String color) {
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

        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Paste", "Wklej")), b -> pasteCoordinates())
                .dimensions(panelLeft + 10, panelTop + 120, 94, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Choose Color", "Wybierz kolor"))
                .styled(style -> style.withColor(borderColor())), b -> {
            snapshot();
            client.setScreen(new ColorPickerScreen(this, selectedColor, value -> selectedColor = value));
        }).dimensions(panelLeft + 112, panelTop + 120, 178, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Save", "Zapisz")), b -> save())
                .dimensions(panelLeft + 10, panelTop + 158, 136, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                .dimensions(panelLeft + 154, panelTop + 158, 136, 20).build());
        setInitialFocus(name);
    }

    private TextFieldWidget field(int x, int y, int width, String value, int max, String hint) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + 5, y + 6, width - 10, 10, Text.literal(hint));
        field.setDrawsBackground(false);
        field.setMaxLength(max);
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void snapshot() {
        pendingName = name.getText();
        pendingX = xField.getText();
        pendingY = yField.getText();
        pendingZ = zField.getText();
    }

    private void pasteCoordinates() {
        Matcher matcher = COORDINATE.matcher(client.keyboard.getClipboard());
        String[] values = new String[3];
        for (int i = 0; i < values.length; i++) {
            if (!matcher.find()) {
                error = UiText.get("Clipboard does not contain X Y Z.", "Schowek nie zawiera X Y Z.");
                return;
            }
            values[i] = String.valueOf((int)Math.floor(Double.parseDouble(matcher.group())));
        }
        xField.setText(values[0]);
        yField.setText(values[1]);
        zField.setText(values[2]);
        error = "";
    }

    private void save() {
        try {
            String waypointName = name.getText().trim();
            if (waypointName.isEmpty()) throw new IllegalArgumentException();
            persist(waypointName, Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()),
                    Integer.parseInt(zField.getText()), selectedColor);
            close();
        } catch (RuntimeException ignored) {
            error = UiText.get("Check the name and coordinates.", "Sprawdź nazwę i koordynaty.");
        }
    }

    protected abstract void persist(String name, int x, int y, int z, String color);

    @Override public void renderBackground(DrawContext context) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) super.renderBackground(context);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        roundedFill(context, panelLeft, panelTop, panelLeft + 300, panelTop + 196, 0xE0141824);
        outline(context, panelLeft, panelTop, panelLeft + 300, panelTop + 196, borderColor());
        drawField(context, panelLeft + 10, panelTop + 46, 280, 20);
        drawField(context, panelLeft + 10, panelTop + 88, 88, 20);
        drawField(context, panelLeft + 106, panelTop + 88, 88, 20);
        drawField(context, panelLeft + 202, panelTop + 88, 88, 20);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 14, borderColor());
        context.drawTextWithShadow(textRenderer, UiText.get("Name", "Nazwa"), panelLeft + 10, panelTop + 34, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, "X", panelLeft + 10, panelTop + 76, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, "Y", panelLeft + 106, panelTop + 76, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, "Z", panelLeft + 202, panelTop + 76, 0xFFD9E2F0);
        if (!error.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2, panelTop + 183, 0xFFFF657A);
    }

    private void drawField(DrawContext context, int x, int y, int width, int height) {
        roundedFill(context, x, y, x + width, y + height, 0xA0000000);
        outline(context, x, y, x + width, y + height, borderColor());
    }

    private int borderColor() {
        try { return 0xFF000000 | (int)Long.parseLong(selectedColor.replace("#", "").substring(2), 16); }
        catch (RuntimeException ignored) { return 0xFF00F5FF; }
    }

    private static void roundedFill(DrawContext context, int left, int top, int right, int bottom, int color) {
        context.fill(left + 2, top, right - 2, bottom, color);
        context.fill(left, top + 2, right, bottom - 2, color);
    }

    private static void outline(DrawContext context, int left, int top, int right, int bottom, int color) {
        context.fill(left + 2, top, right - 2, top + 1, color);
        context.fill(left + 2, bottom - 1, right - 2, bottom, color);
        context.fill(left, top + 2, left + 1, bottom - 2, color);
        context.fill(right - 1, top + 2, right, bottom - 2, color);
        context.fill(left + 1, top + 1, left + 2, top + 2, color);
        context.fill(right - 2, top + 1, right - 1, top + 2, color);
        context.fill(left + 1, bottom - 2, left + 2, bottom - 1, color);
        context.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
    }

    @Override public void close() { client.setScreen(parent); }
}
