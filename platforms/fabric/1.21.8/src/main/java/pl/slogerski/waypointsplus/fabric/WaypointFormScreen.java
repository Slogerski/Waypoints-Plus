package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class WaypointFormScreen extends Screen {
    private static final Pattern COORDINATE = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Identifier PASTE_ICON = Identifier.of("waypointsplus", "textures/gui/clipboard.png");
    private static final Identifier PRIVACY_ICON = Identifier.of("waypointsplus", "textures/gui/incognito.png");
    private static final Identifier PRIVACY_TEXTURE = Identifier.of("waypointsplus", "dynamic/incognito_icon");
    private static final int ICON_SIZE = 18;
    private static final int ICON_SPACING = 4;
    private static final int COORDINATE_GAP = 8;
    private static final int ICON_GAP = 12;
    private final Screen parent;
    private String pendingName, pendingX, pendingY, pendingZ;
    protected String selectedColor;
    protected String selectedDimension;
    private TextFieldWidget name, xField, yField, zField;
    private int panelLeft, panelTop, panelWidth, coordinateWidth, yFieldLeft, zFieldLeft;
    private NativeImageBackedTexture privacyTexture;
    private int privacyTextureWidth, privacyTextureHeight;
    private boolean presetsVisible;
    protected String selectedPreset = "default";
    protected String selectedItem = "";
    private int nameLeft, nameWidth;
    private String error = "";

    WaypointFormScreen(Screen parent, Text title, String name, int x, int y, int z, String color,
                       String dimension) {
        super(title);
        this.parent = parent;
        this.pendingName = name;
        this.pendingX = String.valueOf(x);
        this.pendingY = String.valueOf(y);
        this.pendingZ = String.valueOf(z);
        this.selectedColor = color;
        this.selectedDimension = dimension;
    }

    @Override protected void init() {
        int presetPanelWidth = Math.min(120, width / 3);
        int sidebarWidth = presetsVisible ? presetPanelWidth + 8 : 0;
        panelWidth = Math.min(330, width - 12 - sidebarWidth);
        panelLeft = (width - panelWidth - sidebarWidth) / 2 + sidebarWidth;
        panelTop = this instanceof CreateWaypointScreen
                ? Math.max(28, height / 2 - 85)
                : Math.max(14, (height - 206) / 2);
        int contentWidth = panelWidth - 20;
        coordinateWidth = (contentWidth - 2 * ICON_SIZE - ICON_SPACING - ICON_GAP - 2 * COORDINATE_GAP) / 3;
        yFieldLeft = panelLeft + 10 + coordinateWidth + COORDINATE_GAP;
        zFieldLeft = yFieldLeft + coordinateWidth + COORDINATE_GAP;
        int iconLeft = panelLeft + panelWidth - 12 - ICON_SIZE;
        int presetWidth = contentWidth * 32 / 100;
        int bottomButtonWidth = (contentWidth - 8) / 2;

        WaypointPreset preset = WaypointPresetStore.find(selectedPreset);
        boolean itemIcon = preset != null && preset.icon && !preset.pngIcon;
        nameLeft = panelLeft + 10 + (itemIcon ? 26 : 0);
        nameWidth = contentWidth - (itemIcon ? 26 : 0);
        name = field(nameLeft, panelTop + 46, nameWidth, pendingName, 64, "Name");
        if (itemIcon) {
            String item = selectedItem.isEmpty() ? preset.item : selectedItem;
            addDrawableChild(new ItemIconButton(panelLeft + 10, panelTop + 46, item));
        }
        xField = coordinateField(panelLeft + 10, pendingX, "X");
        yField = coordinateField(yFieldLeft, pendingY, "Y");
        zField = coordinateField(zFieldLeft, pendingZ, "Z");
        loadPrivacyTexture();
        addDrawableChild(new PrivacyIconButton(iconLeft - ICON_SIZE - ICON_SPACING, panelTop + 89));
        addDrawableChild(new PasteIconButton(iconLeft, panelTop + 89));

        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Preset", "Szablon")), b -> {
            snapshot();
            presetsVisible = !presetsVisible;
            clearAndInit();
        })
                .dimensions(panelLeft + 10, panelTop + 120, presetWidth, 20).build());
        if (presetsVisible) {
            addDrawableChild(new WaypointPresetList(textRenderer, panelLeft - presetPanelWidth - 8,
                    panelTop, presetPanelWidth, 196, () -> WaypointsPlusClient.config().saveSettings(),
                    selectedPreset, id -> {
                        if (id.equals(selectedPreset)) return;
                        snapshot();
                        selectedPreset = id;
                        selectedItem = "";
                        clearAndInit();
                    }));
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Settings", "Ustawienia"))
                .styled(style -> style.withColor(borderColor())), b -> {
            snapshot();
            client.setScreen(new ColorPickerScreen(this, selectedColor, selectedDimension, (color, dimension) -> {
                selectedColor = color;
                selectedDimension = dimension;
            }));
        }).dimensions(panelLeft + 18 + presetWidth, panelTop + 120, contentWidth - presetWidth - 8, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Save", "Zapisz")), b -> save())
                .dimensions(panelLeft + 10, panelTop + 158, bottomButtonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                .dimensions(panelLeft + 18 + bottomButtonWidth, panelTop + 158, contentWidth - bottomButtonWidth - 8, 20).build());
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

    private TextFieldWidget coordinateField(int x, String value, String label) {
        TextFieldWidget field = new CoordinateField(x + 5, panelTop + 94, coordinateWidth - 10, label);
        field.setDrawsBackground(false);
        field.setMaxLength(14);
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private boolean coordinatesHidden() {
        return WaypointsPlusClient.config().settings().hideFormCoordinates;
    }

    private void loadPrivacyTexture() {
        releasePrivacyTexture();
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        try (InputStream stream = client.getResourceManager().open(PRIVACY_ICON)) {
            image = NativeImage.read(stream);
            privacyTextureWidth = image.getWidth();
            privacyTextureHeight = image.getHeight();
            for (int y = 0; y < privacyTextureHeight; y++) {
                for (int x = 0; x < privacyTextureWidth; x++) {
                    image.setColorArgb(x, y, (image.getColorArgb(x, y) & 0xFF000000) | 0x00FFFFFF);
                }
            }
            texture = new NativeImageBackedTexture(() -> "waypointsplus/incognito-icon", image);
            client.getTextureManager().registerTexture(PRIVACY_TEXTURE, texture);
            privacyTexture = texture;
        } catch (IOException | RuntimeException exception) {
            if (texture != null) texture.close();
            else if (image != null) image.close();
            LoggerFactory.getLogger("waypointsplus").warn("Cannot load incognito icon", exception);
        }
    }

    private void releasePrivacyTexture() {
        if (privacyTexture == null) return;
        client.getTextureManager().destroyTexture(PRIVACY_TEXTURE);
        privacyTexture = null;
    }

    @Override public void removed() {
        releasePrivacyTexture();
        super.removed();
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
                    Integer.parseInt(zField.getText()), selectedColor, selectedDimension);
            WaypointsPlusClient.config().settings().rememberWaypointColor(selectedColor);
            WaypointsPlusClient.config().saveSettings();
            close();
        } catch (RuntimeException ignored) {
            error = UiText.get("Check the name and coordinates.", "Sprawdź nazwę i koordynaty.");
        }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this instanceof CreateWaypointScreen && name.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected abstract void persist(String name, int x, int y, int z, String color, String dimension);

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        GuiPalette.panel(
                context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + 196, borderColor());
        drawField(context, nameLeft, panelTop + 46, nameWidth, 20);
        drawField(context, panelLeft + 10, panelTop + 88, coordinateWidth, 20);
        drawField(context, yFieldLeft, panelTop + 88, coordinateWidth, 20);
        drawField(context, zFieldLeft, panelTop + 88, coordinateWidth, 20);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, panelLeft + panelWidth / 2, panelTop + 14, borderColor());
        context.drawTextWithShadow(textRenderer, UiText.get("Name", "Nazwa"), nameLeft, panelTop + 34, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, "X", panelLeft + 10, panelTop + 76, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, "Y", yFieldLeft, panelTop + 76, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, "Z", zFieldLeft, panelTop + 76, 0xFFD9E2F0);
        if (this instanceof CreateWaypointScreen) {
            Text tip = Text.literal(UiText.get("Tip: Press [ ", "Tip: Naciśnij [ "))
                    .styled(style -> style.withColor(0x6B7280))
                    .append(Text.literal(";").styled(style -> style.withColor(0xFEC110)))
                    .append(Text.literal(UiText.get(
                            " ] to manage, edit, or delete waypoints.",
                            " ], aby zarządzać, edytować")).styled(style -> style.withColor(0x6B7280)));
            context.drawCenteredTextWithShadow(textRenderer, tip, width / 2, panelTop - 22, 0xFF6B7280);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(UiText.get(
                    "If it doesn’t work, check your key bindings in Controls.",
                    "i usuwać waypointy. Jeśli nie działa, sprawdź Sterowanie.")),
                    width / 2, panelTop - 11, 0xFF6B7280);
        }
        if (!error.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), panelLeft + panelWidth / 2, panelTop + 183, 0xFFFF657A);
    }

    private void drawField(DrawContext context, int x, int y, int width, int height) {
        GuiPalette.input(context, x, y, width, height, borderColor());
    }

    private int borderColor() {
        try { return 0xFF000000 | (int)Long.parseLong(selectedColor.replace("#", "").substring(2), 16); }
        catch (RuntimeException ignored) { return 0xFF00F5FF; }
    }

    private final class ItemIconButton extends PressableWidget {
        private final ItemStack item;

        private ItemIconButton(int x, int y, String itemId) {
            super(x, y, 20, 20, Text.literal(UiText.get("Select Icon", "Wybierz ikonę")));
            item = new ItemStack(Registries.ITEM.get(Identifier.of(itemId)));
            setTooltip(Tooltip.of(getMessage()));
        }

        @Override public void onPress() {
            snapshot();
            client.setScreen(new PresetItemScreen(WaypointFormScreen.this, value -> selectedItem = value));
        }

        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            GuiPalette.input(context, getX(), getY(), 20, 20, borderColor());
            if (isHovered() || isFocused()) context.fill(getX() + 1, getY() + 1, getX() + 19, getY() + 19, 0x30FFFFFF);
            context.drawItem(item, getX() + 2, getY() + 2);
        }

        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class PasteIconButton extends PressableWidget {
        private PasteIconButton(int x, int y) {
            super(x, y, ICON_SIZE, ICON_SIZE, Text.literal(UiText.get("Paste Coordinates", "Wklej koordynaty")));
            setTooltip(Tooltip.of(getMessage()));
        }

        @Override public void onPress() {
            pasteCoordinates();
        }

        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, PASTE_ICON, getX(), getY(),
                    0.0f, 0.0f, ICON_SIZE, ICON_SIZE, 512, 512, 512, 512);
        }

        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class PrivacyIconButton extends PressableWidget {
        private PrivacyIconButton(int x, int y) {
            super(x, y, ICON_SIZE, ICON_SIZE, Text.empty());
            updateLabel();
        }

        private void updateLabel() {
            setMessage(Text.literal(coordinatesHidden()
                    ? UiText.get("Show Coordinates", "Pokaż koordynaty")
                    : UiText.get("Hide Coordinates", "Ukryj koordynaty")));
            setTooltip(Tooltip.of(getMessage()));
        }

        @Override public void onPress() {
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            settings.hideFormCoordinates = !settings.hideFormCoordinates;
            WaypointsPlusClient.config().saveSettings();
            updateLabel();
        }

        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int color = coordinatesHidden() ? 0xFFE0E0E0 : 0xFF888888;
            if (isHovered() || isFocused()) color = coordinatesHidden() ? 0xFFFFFFFF : 0xFFAAAAAA;
            if (privacyTexture == null) {
                context.drawCenteredTextWithShadow(textRenderer, "?", getX() + ICON_SIZE / 2, getY() + 5, color);
                return;
            }
            context.drawTexture(RenderPipelines.GUI_TEXTURED, PRIVACY_TEXTURE, getX(), getY(),
                    0.0f, 0.0f, ICON_SIZE, ICON_SIZE, privacyTextureWidth, privacyTextureHeight,
                    privacyTextureWidth, privacyTextureHeight, color);
        }

        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class CoordinateField extends TextFieldWidget {
        private CoordinateField(int x, int y, int width, String label) {
            super(textRenderer, x, y, width, 10, Text.literal(label));
        }

        @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            if (coordinatesHidden()) {
                context.drawTextWithShadow(textRenderer, "****", getX(), getY(), 0xFFE0E0E0);
            } else {
                super.renderWidget(context, mouseX, mouseY, delta);
            }
        }

        @Override protected MutableText getNarrationMessage() {
            return coordinatesHidden() ? Text.literal(getMessage().getString() + ": ****") : super.getNarrationMessage();
        }
    }

    @Override public void close() { client.setScreen(parent); }
}
