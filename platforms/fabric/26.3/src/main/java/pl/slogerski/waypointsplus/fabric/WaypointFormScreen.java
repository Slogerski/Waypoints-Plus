package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class WaypointFormScreen extends Screen {
    private static final Pattern COORDINATE = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Identifier PASTE_ICON = Identifier.fromNamespaceAndPath("waypointsplus", "textures/gui/clipboard.png");
    private static final Identifier PRIVACY_ICON = Identifier.fromNamespaceAndPath("waypointsplus", "textures/gui/incognito.png");
    private static final Identifier PRIVACY_TEXTURE = Identifier.fromNamespaceAndPath("waypointsplus", "dynamic/incognito_icon");
    private static final int ICON_SIZE = 18;
    private static final int ICON_SPACING = 4;
    private static final int COORDINATE_GAP = 8;
    private static final int ICON_GAP = 12;
    private final Screen parent;
    private String pendingName, pendingX, pendingY, pendingZ;
    protected String selectedColor;
    protected String selectedDimension;
    private EditBox name, xField, yField, zField;
    private int panelLeft, panelTop, panelWidth, coordinateWidth, yFieldLeft, zFieldLeft;
    private DynamicTexture privacyTexture;
    private int privacyTextureWidth, privacyTextureHeight;
    private boolean presetsVisible;
    protected String selectedPreset = "default";
    protected String selectedItem = "";
    private int nameLeft, nameWidth;
    private String error = "";

    WaypointFormScreen(Screen parent, Component title, String name, int x, int y, int z, String color,
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
            addRenderableWidget(new ItemIconButton(panelLeft + 10, panelTop + 46, item));
        }
        xField = coordinateField(panelLeft + 10, pendingX, "X");
        yField = coordinateField(yFieldLeft, pendingY, "Y");
        zField = coordinateField(zFieldLeft, pendingZ, "Z");
        loadPrivacyTexture();
        addRenderableWidget(new PrivacyIconButton(iconLeft - ICON_SIZE - ICON_SPACING, panelTop + 89));
        addRenderableWidget(new PasteIconButton(iconLeft, panelTop + 89));

        addRenderableWidget(Button.builder(Component.literal(UiText.get("Preset", "Szablon")), b -> {
            snapshot();
            presetsVisible = !presetsVisible;
            rebuildWidgets();
        })
                .pos(panelLeft + 10, panelTop + 120).size(presetWidth, 20).build());
        if (presetsVisible) {
            addRenderableWidget(new WaypointPresetList(font, panelLeft - presetPanelWidth - 8,
                    panelTop, presetPanelWidth, 196, () -> WaypointsPlusClient.config().saveSettings(),
                    selectedPreset, id -> {
                        if (id.equals(selectedPreset)) return;
                        snapshot();
                        selectedPreset = id;
                        selectedItem = "";
                        rebuildWidgets();
                    }));
        }
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Settings", "Ustawienia"))
                .withColor(borderColor()), b -> {
            snapshot();
            minecraft.gui.setScreen(new ColorPickerScreen(this, selectedColor, selectedDimension, (color, dimension) -> {
                selectedColor = color;
                selectedDimension = dimension;
            }));
        }).pos(panelLeft + 18 + presetWidth, panelTop + 120).size(contentWidth - presetWidth - 8, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Save", "Zapisz")), b -> save())
                .pos(panelLeft + 10, panelTop + 158).size(bottomButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Exit", "Wyjdź")), b -> onClose())
                .pos(panelLeft + 18 + bottomButtonWidth, panelTop + 158).size(contentWidth - bottomButtonWidth - 8, 20).build());
        setInitialFocus(name);
    }

    private EditBox field(int x, int y, int width, String value, int max, String hint) {
        EditBox field = new EditBox(font, x + 5, y + 6, width - 10, 10, Component.literal(hint));
        field.setBordered(false);
        field.setMaxLength(max);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private EditBox coordinateField(int x, String value, String label) {
        EditBox field = new CoordinateField(x + 5, panelTop + 94, coordinateWidth - 10, label);
        field.setBordered(false);
        field.setMaxLength(14);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private boolean coordinatesHidden() {
        return WaypointsPlusClient.config().settings().hideFormCoordinates;
    }

    private void loadPrivacyTexture() {
        releasePrivacyTexture();
        NativeImage image = null;
        DynamicTexture texture = null;
        try (InputStream stream = minecraft.getResourceManager().open(PRIVACY_ICON)) {
            image = NativeImage.read(stream);
            privacyTextureWidth = image.getWidth();
            privacyTextureHeight = image.getHeight();
            for (int y = 0; y < privacyTextureHeight; y++) {
                for (int x = 0; x < privacyTextureWidth; x++) {
                    image.setPixel(x, y, (image.getPixel(x, y) & 0xFF000000) | 0x00FFFFFF);
                }
            }
            texture = new DynamicTexture(() -> "waypointsplus/incognito-icon", image);
            minecraft.getTextureManager().register(PRIVACY_TEXTURE, texture);
            privacyTexture = texture;
        } catch (IOException | RuntimeException exception) {
            if (texture != null) texture.close();
            else if (image != null) image.close();
            LoggerFactory.getLogger("waypointsplus").warn("Cannot load incognito icon", exception);
        }
    }

    private void releasePrivacyTexture() {
        if (privacyTexture == null) return;
        minecraft.getTextureManager().release(PRIVACY_TEXTURE);
        privacyTexture = null;
    }

    @Override public void removed() {
        releasePrivacyTexture();
        super.removed();
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
                    Integer.parseInt(zField.getValue()), selectedColor, selectedDimension);
            WaypointsPlusClient.config().settings().rememberWaypointColor(selectedColor);
            WaypointsPlusClient.config().saveSettings();
            onClose();
        } catch (RuntimeException ignored) {
            error = UiText.get("Check the name and coordinates.", "Sprawdź nazwę i koordynaty.");
        }
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int keyCode = input.key();
        if (this instanceof CreateWaypointScreen && name.isFocused()
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            save();
            return true;
        }
        return super.keyPressed(input);
    }

    protected abstract void persist(String name, int x, int y, int z, String color, String dimension);

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.extractBackground(context, mouseX, mouseY, delta);
        }
        GuiPalette.panel(
                context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + 196, borderColor());
        drawField(context, nameLeft, panelTop + 46, nameWidth, 20);
        drawField(context, panelLeft + 10, panelTop + 88, coordinateWidth, 20);
        drawField(context, yFieldLeft, panelTop + 88, coordinateWidth, 20);
        drawField(context, zFieldLeft, panelTop + 88, coordinateWidth, 20);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, panelLeft + panelWidth / 2, panelTop + 14, borderColor());
        context.text(font, UiText.get("Name", "Nazwa"), nameLeft, panelTop + 34, 0xFFD9E2F0);
        context.text(font, "X", panelLeft + 10, panelTop + 76, 0xFFD9E2F0);
        context.text(font, "Y", yFieldLeft, panelTop + 76, 0xFFD9E2F0);
        context.text(font, "Z", zFieldLeft, panelTop + 76, 0xFFD9E2F0);
        if (this instanceof CreateWaypointScreen) {
            Component tip = Component.literal(UiText.get("Tip: Press [ ", "Tip: Naciśnij [ "))
                    .withColor(0x6B7280)
                    .append(Component.literal(";").withColor(0xFEC110))
                    .append(Component.literal(UiText.get(
                            " ] to manage, edit, or delete waypoints.",
                            " ], aby zarządzać, edytować")).withColor(0x6B7280));
            context.centeredText(font, tip, width / 2, panelTop - 22, 0xFF6B7280);
            context.centeredText(font, Component.literal(UiText.get(
                    "If it doesn’t work, check your key bindings in Controls.",
                    "i usuwać waypointy. Jeśli nie działa, sprawdź Sterowanie.")),
                    width / 2, panelTop - 11, 0xFF6B7280);
        }
        if (!error.isEmpty()) context.centeredText(font, Component.literal(error), panelLeft + panelWidth / 2, panelTop + 183, 0xFFFF657A);
    }

    private void drawField(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        GuiPalette.input(context, x, y, width, height, borderColor());
    }

    private int borderColor() {
        try { return 0xFF000000 | (int)Long.parseLong(selectedColor.replace("#", "").substring(2), 16); }
        catch (RuntimeException ignored) { return 0xFF00F5FF; }
    }

    private abstract class IconButton extends AbstractWidget {
        IconButton(int x, int y, int width, int height, Component label) { super(x, y, width, height, label); }
        public abstract void onPress();
        @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) { onPress(); }
        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
            if (active && isFocused() && (input.key() == InputConstants.KEY_RETURN || input.key() == InputConstants.KEY_SPACE)) {
                onPress(); return true;
            }
            return super.keyPressed(input);
        }
    }

    private final class ItemIconButton extends IconButton {
        private final ItemStack item;

        private ItemIconButton(int x, int y, String itemId) {
            super(x, y, 20, 20, Component.literal(UiText.get("Select Icon", "Wybierz ikonę")));
            item = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override public void onPress() {
            snapshot();
            minecraft.gui.setScreen(new PresetItemScreen(WaypointFormScreen.this, value -> selectedItem = value));
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            GuiPalette.input(context, getX(), getY(), 20, 20, borderColor());
            if (isHovered() || isFocused()) context.fill(getX() + 1, getY() + 1, getX() + 19, getY() + 19, 0x30FFFFFF);
            context.item(item, getX() + 2, getY() + 2);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }

    private final class PasteIconButton extends IconButton {
        private PasteIconButton(int x, int y) {
            super(x, y, ICON_SIZE, ICON_SIZE, Component.literal(UiText.get("Paste Coordinates", "Wklej koordynaty")));
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override public void onPress() {
            pasteCoordinates();
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.blit(RenderPipelines.GUI_TEXTURED, PASTE_ICON, getX(), getY(),
                    0.0f, 0.0f, ICON_SIZE, ICON_SIZE, 512, 512, 512, 512);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }

    private final class PrivacyIconButton extends IconButton {
        private PrivacyIconButton(int x, int y) {
            super(x, y, ICON_SIZE, ICON_SIZE, Component.empty());
            updateLabel();
        }

        private void updateLabel() {
            setMessage(Component.literal(coordinatesHidden()
                    ? UiText.get("Show Coordinates", "Pokaż koordynaty")
                    : UiText.get("Hide Coordinates", "Ukryj koordynaty")));
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override public void onPress() {
            WaypointSettings settings = WaypointsPlusClient.config().settings();
            settings.hideFormCoordinates = !settings.hideFormCoordinates;
            WaypointsPlusClient.config().saveSettings();
            updateLabel();
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            int color = coordinatesHidden() ? 0xFFE0E0E0 : 0xFF888888;
            if (isHovered() || isFocused()) color = coordinatesHidden() ? 0xFFFFFFFF : 0xFFAAAAAA;
            if (privacyTexture == null) {
                context.centeredText(font, "?", getX() + ICON_SIZE / 2, getY() + 5, color);
                return;
            }
            context.blit(RenderPipelines.GUI_TEXTURED, PRIVACY_TEXTURE, getX(), getY(),
                    0.0f, 0.0f, ICON_SIZE, ICON_SIZE, privacyTextureWidth, privacyTextureHeight,
                    privacyTextureWidth, privacyTextureHeight, color);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }

    private final class CoordinateField extends EditBox {
        private CoordinateField(int x, int y, int width, String label) {
            super(font, x, y, width, 10, Component.literal(label));
        }

        @Override public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            if (coordinatesHidden()) {
                context.text(font, "****", getX(), getY(), 0xFFE0E0E0);
            } else {
                super.extractWidgetRenderState(context, mouseX, mouseY, delta);
            }
        }

        @Override protected MutableComponent createNarrationMessage() {
            return coordinatesHidden() ? Component.literal(getMessage().getString() + ": ****") : super.createNarrationMessage();
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
