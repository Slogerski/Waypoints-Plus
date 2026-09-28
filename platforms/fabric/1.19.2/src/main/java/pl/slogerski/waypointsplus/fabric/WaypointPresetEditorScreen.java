package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class WaypointPresetEditorScreen extends AlertScreen {
    private static final int PROPERTY_HEIGHT = 614;
    private static final float MIN_ZOOM = 0.5f;
    private final Screen parent;
    private WaypointPreset draft;
    private WaypointPresetPreview preview;
    private final List<PropertyWidget> properties = new ArrayList<>();
    private final List<PropertyLabel> labels = new ArrayList<>();
    private final List<InputOutline> outlines = new ArrayList<>();
    private final List<Supplier<Boolean>> validation = new ArrayList<>();
    private final List<ClickableWidget> fixed = new ArrayList<>();
    private ButtonWidget saveButton;
    private int propertyLeft, propertyTop, propertyBottom;
    private int panelLeft, panelTop, panelRight, panelBottom;
    private int canvasLeft, canvasTop, canvasRight, canvasBottom;
    private double propertyScroll;
    private float zoom = 3;
    private boolean viewInitialized;
    private float panX, panY;
    private float previewCenterX, previewCenterY;
    private int selectedPart;
    private int draggedPart = -1;
    private boolean panning, draggingScrollbar;
    private String notice = "";

    WaypointPresetEditorScreen(Screen parent, WaypointPreset preset) {
        super(Text.literal(UiText.get("Preset Creator", "Kreator szablonu")));
        this.parent = parent;
        draft = WaypointPresetStore.copy(preset);
    }

    @Override protected void init() {
        configureScale();
        properties.clear(); labels.clear(); outlines.clear(); validation.clear(); fixed.clear();
        int panelWidth = Math.min(620, width - 12), panelHeight = Math.min(380, height - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;
        propertyLeft = panelRight - 162;
        propertyTop = panelTop + 55;
        propertyBottom = panelBottom - 38;
        canvasLeft = panelLeft + 8;
        canvasTop = propertyTop;
        canvasRight = propertyLeft - 10;
        canvasBottom = propertyBottom;
        if (preview != null) preview.close();
        preview = new WaypointPresetPreview(draft, textRenderer);
        if (!viewInitialized) {
            WaypointPresetPreview.Bounds bounds = preview.contentBounds();
            zoom = Math.max(MIN_ZOOM, Math.min(3, Math.min(
                    (canvasRight - canvasLeft - 24) / (bounds.right() - bounds.left()),
                    (canvasBottom - canvasTop - 24) / (bounds.bottom() - bounds.top()))));
            previewCenterX = (bounds.left() + bounds.right()) / 2;
            previewCenterY = (bounds.top() + bounds.bottom()) / 2;
            panX = -previewCenterX * zoom;
            panY = -previewCenterY * zoom;
            viewInitialized = true;
        }
        limitPan();

        int nameWidth = Math.min(180, panelWidth - 220);
        TextFieldWidget name = new TextFieldWidget(textRenderer, panelLeft + 13, panelTop + 26, nameWidth - 10, 10, Text.literal(UiText.get("Preset Name", "Nazwa szablonu")));
        name.setDrawsBackground(false);
        name.setMaxLength(64);
        name.setText(draft.name);
        name.setChangedListener(value -> { draft.name = value; updateSave(); });
        fixed.add(addDrawableChild(name));
        fixed.add(addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Import", "Import")), button -> importPreset())
                .dimensions(panelRight - 124, panelTop + 20, 54, 20).build()));
        fixed.add(addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Export", "Export")), button -> exportPreset())
                .dimensions(panelRight - 64, panelTop + 20, 54, 20).build()));
        saveButton = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Save", "Zapisz")), button -> save())
                .dimensions(width / 2 - 84, panelBottom - 24, 80, 20).build());
        fixed.add(saveButton);
        fixed.add(addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), button -> close())
                .dimensions(width / 2 + 4, panelBottom - 24, 80, 20).build()));
        buildProperties();
        setPropertyScroll(propertyScroll);
        updateSave();
        setInitialFocus(name);
    }

    private void buildProperties() {
        heading(UiText.get("Text", "Tekst"), 2);
        for (int i = 0; i < 4; i++) {
            int index = i;
            WaypointPreset.TextPart part = preview.part(i);
            String name = partName(i);
            checkbox(21 + i * 22, part.enabled, () -> part.enabled = !part.enabled);
            property(ButtonWidget.builder(Text.literal((selectedPart == i ? "> " : "") + name), button -> {
                selectedPart = index;
                clearAndInit();
            }).dimensions(propertyLeft + 23, 0, 116, 16).build(), 21 + i * 22);
        }
        positionFields(128, false);
        heading(UiText.get("Icon", "Ikona"), 155);
        checkbox(174, draft.icon, () -> draft.icon = !draft.icon);
        property(ButtonWidget.builder(Text.literal((selectedPart == 4 ? "> " : "") + UiText.get("Icon", "Ikona")), button -> {
            selectedPart = 4;
            clearAndInit();
        }).dimensions(propertyLeft + 23, 0, 116, 16).build(), 174);
        property(ButtonWidget.builder(Text.literal((draft.pngIcon ? "" : "> ") + UiText.get("Item", "Przedmiot")), button -> {
            client.setScreen(new PresetItemScreen(this, id -> {
                draft.item = id;
                draft.icon = true;
                draft.pngIcon = false;
            }));
        }).dimensions(propertyLeft + 4, 0, 135, 18).build(), 196);
        ButtonWidget pngChoice = ButtonWidget.builder(Text.literal((draft.pngIcon ? "> " : "") + "PNG"), button -> {
            draft.pngIcon = true;
            draft.icon = true;
            clearAndInit();
        }).dimensions(propertyLeft + 4, 0, 55, 18).build();
        pngChoice.active = !draft.png.isEmpty();
        property(pngChoice, 219);
        property(ButtonWidget.builder(Text.literal(UiText.get("Upload", "Wgraj")), button -> uploadPng())
                .dimensions(propertyLeft + 64, 0, 75, 18).build(), 219);
        labels.add(new PropertyLabel("16×16 / 32×32 / 64×64", 242, 0xFFAAAAAA));
        positionFields(268, true);
        heading(UiText.get("Other", "Pozostałe"), 300);
        labeledCheckbox(320, UiText.get("Corners", "Narożniki"), draft.corners, () -> draft.corners = !draft.corners);
        labeledCheckbox(342, UiText.get("Border", "Ramka"), draft.border, () -> draft.border = !draft.border);
        labeledCheckbox(364, UiText.get("Background", "Tło"), draft.background, () -> draft.background = !draft.background);
        labels.add(new PropertyLabel(UiText.get("Border Size", "Grubość ramki"), 390, 0xFFAAAAAA));
        numberField(propertyLeft + 4, 405, 135, draft.borderSize, value -> WaypointPreset.range(value, 0.5f, 8), value -> draft.borderSize = value);
        labels.add(new PropertyLabel(UiText.get("Padding", "Odstęp tła"), 434, 0xFFAAAAAA));
        numberField(propertyLeft + 4, 449, 135, draft.padding, value -> WaypointPreset.range(value, 0, 32), value -> draft.padding = value);
        heading(UiText.get("Fit Background To", "Dopasuj tło do"), 480);
        for (int i = 0; i < 5; i++) {
            int index = i;
            labeledCheckbox(499 + i * 22, i == 4 ? UiText.get("Icon", "Ikona") : partName(i),
                    draft.linked(i), () -> draft.toggleLinked(index));
        }
    }

    private String partName(int index) {
        return switch (index) {
            case 0 -> UiText.get("Name", "Nazwa");
            case 1 -> UiText.get("Distance", "Odległość");
            case 2 -> UiText.get("Coords", "Koordynaty");
            default -> UiText.get("Profile", "Profil");
        };
    }

    private void heading(String text, int y) { labels.add(new PropertyLabel(text, y, 0xFFE0E0E0)); }

    private void checkbox(int y, boolean value, Runnable toggle) {
        property(ButtonWidget.builder(Text.literal(value ? "✓" : ""), button -> { toggle.run(); clearAndInit(); })
                .dimensions(propertyLeft + 4, 0, 15, 16).build(), y);
    }

    private void labeledCheckbox(int y, String label, boolean value, Runnable toggle) {
        checkbox(y, value, toggle);
        labels.add(new PropertyLabel(label, y + 4, 0xFFCCCCCC, 24));
    }

    private void positionFields(int y, boolean icon) {
        WaypointPreset.TextPart part = preview.part(Math.min(3, selectedPart));
        boolean editsIcon = icon || selectedPart == 4;
        numberField(propertyLeft + 4, y, 39, editsIcon ? draft.iconX : part.x, WaypointPreset::position,
                value -> { if (editsIcon) draft.iconX = value; else part.x = value; });
        numberField(propertyLeft + 47, y, 39, editsIcon ? draft.iconY : part.y, WaypointPreset::position,
                value -> { if (editsIcon) draft.iconY = value; else part.y = value; });
        numberField(propertyLeft + 90, y, 49, editsIcon ? draft.iconScale : part.scale, WaypointPreset::scale,
                value -> { if (editsIcon) draft.iconScale = value; else part.scale = value; });
        labels.add(new PropertyLabel("X", y - 10, 0xFF999999, 4));
        labels.add(new PropertyLabel("Y", y - 10, 0xFF999999, 47));
        labels.add(new PropertyLabel(UiText.get("Scale", "Skala"), y - 10, 0xFF999999, 90));
    }

    private void numberField(int x, int y, int width, float initial, Predicate<Float> valid, Consumer<Float> apply) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + 4, 0, width - 8, 10, Text.literal("Value"));
        field.setDrawsBackground(false);
        field.setMaxLength(8);
        field.setText(number(initial));
        Supplier<Boolean> validator = () -> {
            try { return valid.test(Float.parseFloat(field.getText().replace(',', '.'))); }
            catch (NumberFormatException exception) { return false; }
        };
        validation.add(validator);
        field.setChangedListener(value -> {
            if (validator.get()) apply.accept(Float.parseFloat(value.replace(',', '.')));
            updateSave();
        });
        property(field, y + 5);
        outlines.add(new InputOutline(x, y, width));
    }

    private static String number(float value) {
        return value == (int) value ? Integer.toString((int) value) : Float.toString(value);
    }

    private <T extends ClickableWidget> void property(T widget, int y) {
        addDrawableChild(widget);
        properties.add(new PropertyWidget(widget, y));
    }

    private void setPropertyScroll(double value) {
        propertyScroll = Math.max(0, Math.min(maxPropertyScroll(), value));
        for (PropertyWidget entry : properties) {
            entry.widget.y = propertyTop + entry.y - (int) Math.round(propertyScroll);
            entry.widget.visible = entry.widget.y >= propertyTop && (entry.widget.y + entry.widget.getHeight()) <= propertyBottom;
            if (!entry.widget.visible && getFocused() == entry.widget) setFocused(null);
        }
    }

    private int maxPropertyScroll() { return Math.max(0, PROPERTY_HEIGHT - (propertyBottom - propertyTop)); }

    private void updateSave() {
        if (saveButton != null) saveButton.active = draft.valid() && validation.stream().allMatch(Supplier::get);
    }

    private void save() {
        updateSave();
        if (!saveButton.active) return;
        if (WaypointPresetStore.save(draft)) close();
        else notice = UiText.get("Could not save the preset.", "Nie udało się zapisać szablonu.");
    }

    private void importPreset() {
        try {
            WaypointPreset imported = WaypointPresetStore.importPreset(client.keyboard.getClipboard());
            imported.id = draft.id;
            draft = imported;
            notice = UiText.get("Imported. Save to keep this preset.", "Zaimportowano. Zapisz, aby zachować szablon.");
            clearAndInit();
        } catch (RuntimeException exception) {
            notice = UiText.get("Clipboard does not contain a valid preset.", "Schowek nie zawiera poprawnego szablonu.");
        }
    }

    private void exportPreset() {
        updateSave();
        if (!saveButton.active) {
            notice = UiText.get("Check the name and properties first.", "Najpierw sprawdź nazwę i właściwości.");
            return;
        }
        client.keyboard.setClipboard(WaypointPresetStore.exportPreset(draft));
        notice = UiText.get("Preset copied to clipboard.", "Szablon skopiowany do schowka.");
    }

    private void uploadPng() {
        try {
            String selected = TinyFileDialogs.tinyfd_openFileDialog(UiText.get("Select PNG", "Wybierz PNG"), "", null, "PNG", false);
            if (selected == null) return;
            Path file = Path.of(selected);
            if (!Files.isRegularFile(file) || Files.size(file) > 32768) throw new IllegalArgumentException();
            byte[] bytes;
            try (var stream = Files.newInputStream(file)) {
                bytes = stream.readNBytes(32769);
            }
            if (bytes.length > 32768) throw new IllegalArgumentException();
            String encoded = Base64.getEncoder().encodeToString(bytes);
            if (!WaypointPreset.validPng(encoded)) throw new IllegalArgumentException();
            try (NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
                if (image.getWidth() != image.getHeight() || image.getWidth() > 64) throw new IllegalArgumentException();
            }
            draft.png = encoded;
            draft.pngIcon = true;
            draft.icon = true;
            notice = "";
            clearAndInit();
        } catch (IOException | RuntimeException | LinkageError exception) {
            notice = UiText.get("Use a PNG: 16×16, 32×32 or 64×64 (max 32 KiB).", "Wybierz PNG: 16×16, 32×32 lub 64×64 (maks. 32 KiB).");
        }
    }

    private float originX() { return (canvasLeft + canvasRight) / 2f + panX; }
    private float originY() { return (canvasTop + canvasBottom) / 2f + panY; }
    private boolean inCanvas(double x, double y) { return x >= canvasLeft && x < canvasRight && y >= canvasTop && y < canvasBottom; }

    private void limitPan() {
        float centerX = -previewCenterX * zoom, centerY = -previewCenterY * zoom;
        float maxX = (canvasRight - canvasLeft) * (zoom / MIN_ZOOM - 1) / 2;
        float maxY = (canvasBottom - canvasTop) * (zoom / MIN_ZOOM - 1) / 2;
        panX = Math.max(centerX - maxX, Math.min(centerX + maxX, panX));
        panY = Math.max(centerY - maxY, Math.min(centerY + maxY, panY));
    }

    private void drawWorkspace(DrawContext context) {
        float halfWidth = (canvasRight - canvasLeft) / (2 * MIN_ZOOM);
        float halfHeight = (canvasBottom - canvasTop) / (2 * MIN_ZOOM);
        int left = Math.round(originX() + (previewCenterX - halfWidth) * zoom);
        int top = Math.round(originY() + (previewCenterY - halfHeight) * zoom);
        int right = Math.round(originX() + (previewCenterX + halfWidth) * zoom);
        int bottom = Math.round(originY() + (previewCenterY + halfHeight) * zoom);
        int visibleLeft = Math.max(canvasLeft, left), visibleTop = Math.max(canvasTop, top);
        int visibleRight = Math.min(canvasRight, right), visibleBottom = Math.min(canvasBottom, bottom);
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return;
        context.fill(visibleLeft, visibleTop, visibleRight, visibleBottom, 0xFF202020);
        int grid = Math.max(8, Math.round(16 * zoom));
        int startX = visibleLeft + Math.floorMod(Math.round(originX()) - visibleLeft, grid);
        int startY = visibleTop + Math.floorMod(Math.round(originY()) - visibleTop, grid);
        for (int x = startX; x < visibleRight; x += grid) context.fill(x, visibleTop, x + 1, visibleBottom, 0xFF292929);
        for (int y = startY; y < visibleBottom; y += grid) context.fill(visibleLeft, y, visibleRight, y + 1, 0xFF292929);
        context.fill(left, top, right, top + 1, 0xFF484848);
        context.fill(left, bottom - 1, right, bottom, 0xFF484848);
        context.fill(left, top, left + 1, bottom, 0xFF484848);
        context.fill(right - 1, top, right, bottom, 0xFF484848);
    }

    @Override protected void renderContent(DrawContext context, MatrixStack matrices, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, panelLeft, panelTop, panelRight, panelBottom);
        GuiPalette.input(context, panelLeft + 8, panelTop + 20, Math.min(180, panelRight - panelLeft - 220), 20);
        context.drawTextWithShadow(textRenderer, UiText.get("Preset Name", "Nazwa szablonu"), panelLeft + 8, panelTop + 9, 0xFFE0E0E0);
        context.drawTextWithShadow(textRenderer, "Classic", panelLeft + 198, panelTop + 26, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, UiText.get("Preview", "Podgląd"), canvasLeft, canvasTop - 12, 0xFFE0E0E0);
        context.drawTextWithShadow(textRenderer, UiText.get("Properties", "Właściwości"), propertyLeft + 4, propertyTop - 12, 0xFFE0E0E0);
        context.fill(canvasLeft, canvasTop, canvasRight, canvasBottom, 0xA0121212);
        context.enableScissor(canvasLeft, canvasTop, canvasRight, canvasBottom);
        drawWorkspace(context);
        preview.draw(context, originX(), originY(), zoom, selectedPart);
        context.disableScissor();
        context.drawTextWithShadow(textRenderer, Math.round(zoom * 100) + "%", canvasLeft + 4, canvasBottom - 12, 0xFF999999);
        context.enableScissor(propertyLeft, propertyTop, panelRight - 10, propertyBottom);
        int contentTop = propertyTop - (int) Math.round(propertyScroll);
        for (InputOutline outline : outlines) GuiPalette.input(context, outline.x, contentTop + outline.y, outline.width, 18);
        for (PropertyLabel label : labels) context.drawTextWithShadow(textRenderer, label.text, propertyLeft + label.x, contentTop + label.y, label.color);
        for (PropertyWidget entry : properties) if (entry.widget.visible) entry.widget.render(matrices, mouseX, mouseY, delta);
        context.disableScissor();
        if (maxPropertyScroll() > 0) {
            int track = propertyBottom - propertyTop;
            int thumb = Math.max(20, track * track / PROPERTY_HEIGHT);
            int y = propertyTop + (int) Math.round((track - thumb) * propertyScroll / maxPropertyScroll());
            context.fill(panelRight - 9, propertyTop, panelRight - 7, propertyBottom, 0x805A5A5A);
            context.fill(panelRight - 10, y, panelRight - 6, y + thumb, 0xFF969696);
        }
        for (ClickableWidget widget : fixed) widget.render(matrices, mouseX, mouseY, delta);
        if (!notice.isEmpty()) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(notice, panelRight - panelLeft - 20), panelLeft + 8, panelBottom - 35, 0xFFCEB36F);
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        if (inCanvas(x, y) && button <= 2) {
            int hit = button == 0 ? preview.hit((x - originX()) / zoom, (y - originY()) / zoom) : -1;
            if (hit >= 0) { selectedPart = hit; draggedPart = hit; clearAndInit(); }
            else panning = true;
            setFocused(null);
            return true;
        }
        if (button == 0 && maxPropertyScroll() > 0 && x >= panelRight - 12 && x < panelRight - 4 && y >= propertyTop && y < propertyBottom) {
            draggingScrollbar = true;
            scrollPropertiesToMouse(y);
            return true;
        }
        return super.clickContent(x, y, button);
    }

    @Override protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) { scrollPropertiesToMouse(y); return true; }
        if (panning) {
            panX += (float) dx;
            panY += (float) dy;
            limitPan();
            return true;
        }
        if (draggedPart >= 0) {
            if (draggedPart == 4) {
                draft.iconX = Math.max(-512, Math.min(512, draft.iconX + (float) dx / zoom));
                draft.iconY = Math.max(-512, Math.min(512, draft.iconY + (float) dy / zoom));
            } else {
                WaypointPreset.TextPart part = preview.part(draggedPart);
                part.x = Math.max(-512, Math.min(512, part.x + (float) dx / zoom));
                part.y = Math.max(-512, Math.min(512, part.y + (float) dy / zoom));
            }
            return true;
        }
        return super.dragContent(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean changed = draggedPart >= 0;
        draggedPart = -1;
        panning = false;
        draggingScrollbar = false;
        if (changed) clearAndInit();
        return super.mouseReleased(x, y, button);
    }

    @Override protected boolean scrollContent(double x, double y, double horizontal, double vertical) {
        if (inCanvas(x, y)) {
            float old = zoom;
            zoom = Math.max(MIN_ZOOM, Math.min(8, zoom * (float) Math.pow(1.15, vertical)));
            panX += (float) (x - originX()) * (1 - zoom / old);
            panY += (float) (y - originY()) * (1 - zoom / old);
            limitPan();
            return true;
        }
        if (x >= propertyLeft && x < panelRight - 4 && y >= propertyTop && y < propertyBottom) {
            setPropertyScroll(propertyScroll - vertical * 22);
            return true;
        }
        return super.scrollContent(x, y, horizontal, vertical);
    }

    private void scrollPropertiesToMouse(double y) {
        int track = propertyBottom - propertyTop;
        int thumb = Math.max(20, track * track / PROPERTY_HEIGHT);
        setPropertyScroll((y - propertyTop - thumb / 2.0) / Math.max(1, track - thumb) * maxPropertyScroll());
    }

    @Override public void removed() {
        if (preview != null) { preview.close(); preview = null; }
        super.removed();
    }

    @Override public void renderBackground(MatrixStack matrices) { }
    @Override public void close() { client.setScreen(parent); }
    private record PropertyWidget(ClickableWidget widget, int y) { }
    private record InputOutline(int x, int y, int width) { }
    private record PropertyLabel(String text, int y, int color, int x) {
        PropertyLabel(String text, int y, int color) { this(text, y, color, 4); }
    }
}
