package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    private static final int PROPERTY_HEIGHT = 754;
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
    private int selectedEdge = -1;
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
        if (!preview.enabled(selectedPart)) selectedEdge = -1;
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
        buildAnchors();
        heading(UiText.get("Text", "Tekst"), 142);
        for (int i = 0; i < 4; i++) {
            int index = i;
            WaypointPreset.TextPart part = preview.part(i);
            String name = partName(i);
            checkbox(161 + i * 22, part.enabled, () -> part.enabled = !part.enabled);
            property(ButtonWidget.builder(Text.literal((selectedPart == i ? "> " : "") + name), button -> {
                selectedPart = index;
                selectedEdge = -1;
                clearAndInit();
            }).dimensions(propertyLeft + 23, 0, 116, 16).build(), 161 + i * 22);
        }
        positionFields(268, false);
        heading(UiText.get("Icon", "Ikona"), 295);
        checkbox(314, draft.icon, () -> draft.icon = !draft.icon);
        property(ButtonWidget.builder(Text.literal((selectedPart == 4 ? "> " : "") + UiText.get("Icon", "Ikona")), button -> {
            selectedPart = 4;
            selectedEdge = -1;
            clearAndInit();
        }).dimensions(propertyLeft + 23, 0, 116, 16).build(), 314);
        property(ButtonWidget.builder(Text.literal((draft.pngIcon ? "" : "> ") + UiText.get("Item", "Przedmiot")), button -> {
            client.setScreen(new PresetItemScreen(this, id -> {
                draft.item = id;
                draft.icon = true;
                draft.pngIcon = false;
            }));
        }).dimensions(propertyLeft + 4, 0, 135, 18).build(), 336);
        ButtonWidget pngChoice = ButtonWidget.builder(Text.literal((draft.pngIcon ? "> " : "") + "PNG"), button -> {
            draft.pngIcon = true;
            draft.icon = true;
            clearAndInit();
        }).dimensions(propertyLeft + 4, 0, 55, 18).build();
        pngChoice.active = !draft.png.isEmpty();
        property(pngChoice, 359);
        property(ButtonWidget.builder(Text.literal(UiText.get("Upload", "Wgraj")), button -> uploadPng())
                .dimensions(propertyLeft + 64, 0, 75, 18).build(), 359);
        labels.add(new PropertyLabel("16×16 / 32×32 / 64×64", 382, 0xFFAAAAAA));
        positionFields(408, true);
        heading(UiText.get("Other", "Pozostałe"), 440);
        labeledCheckbox(460, UiText.get("Corners", "Narożniki"), draft.corners, () -> draft.corners = !draft.corners);
        labeledCheckbox(482, UiText.get("Border", "Ramka"), draft.border, () -> draft.border = !draft.border);
        labeledCheckbox(504, UiText.get("Background", "Tło"), draft.background, () -> draft.background = !draft.background);
        labels.add(new PropertyLabel(UiText.get("Border Size", "Grubość ramki"), 530, 0xFFAAAAAA));
        numberField(propertyLeft + 4, 545, 135, draft.borderSize, value -> WaypointPreset.range(value, 0.5f, 8), value -> draft.borderSize = value);
        labels.add(new PropertyLabel(UiText.get("Padding", "Odstęp tła"), 574, 0xFFAAAAAA));
        numberField(propertyLeft + 4, 589, 135, draft.padding, value -> WaypointPreset.range(value, 0, 32), value -> draft.padding = value);
        heading(UiText.get("Fit Background To", "Dopasuj tło do"), 620);
        for (int i = 0; i < 5; i++) {
            int index = i;
            labeledCheckbox(639 + i * 22, i == 4 ? UiText.get("Icon", "Ikona") : partName(i),
                    draft.linked(i), () -> {
                        draft.toggleLinked(index);
                        if (!WaypointPresetLayout.valid(draft)) {
                            draft.toggleLinked(index);
                            notice = UiText.get("Detach the edge first to avoid a circular link.", "Najpierw odłącz krawędź, aby uniknąć zapętlenia.");
                        }
                    });
        }
    }

    private void buildAnchors() {
        heading(UiText.get("Attach Edges", "Przyklej krawędzie"), 2);
        labels.add(new PropertyLabel(partName(selectedPart), 16, 0xFF97C89D));
        for (int edge = 0; edge < 4; edge++) {
            int side = edge;
            ButtonWidget choice = ButtonWidget.builder(Text.literal((selectedEdge == edge ? "> " : "") + edgeName(edge)), pressed -> {
                selectedEdge = selectedEdge == side ? -1 : side;
                notice = selectedEdge < 0 ? "" : UiText.get("Click a green target edge. Right-click cancels.",
                        "Kliknij zieloną krawędź docelową. Prawy przycisk anuluje.");
                clearAndInit();
            }).dimensions(propertyLeft + 4 + (edge % 2) * 70, 0, 65, 18).build();
            choice.active = preview.enabled(selectedPart);
            property(choice, 30 + (edge / 2) * 22);
        }
        for (int axis = 0; axis < 2; axis++) {
            int direction = axis;
            ButtonWidget detach = ButtonWidget.builder(Text.literal(UiText.get("Detach ", "Odłącz ") + (axis == 0 ? "X" : "Y")), pressed -> {
                boolean detached = preview.layout().detach(draft, selectedPart, direction);
                selectedEdge = -1;
                notice = detached ? "" : UiText.get("Move the element closer before detaching.",
                        "Przesuń element bliżej przed odłączeniem.");
                clearAndInit();
            }).dimensions(propertyLeft + 4 + axis * 70, 0, 65, 18).build();
            detach.active = draft.anchor(selectedPart, axis) != null;
            property(detach, 76);
            WaypointPreset.Anchor anchor = draft.anchor(selectedPart, axis);
            String relation = anchor == null ? UiText.get("Independent", "Niezależny")
                    : partName(anchor.target) + "." + edgeName(anchor.edge);
            labels.add(new PropertyLabel((axis == 0 ? "X: " : "Y: ") + relation, 100 + axis * 12, 0xFFAAAAAA));
        }
        labels.add(new PropertyLabel(UiText.get("X* / Y* = gap", "X* / Y* = odstęp"), 127, 0xFF97C89D));
    }

    private String edgeName(int edge) {
        return switch (edge) {
            case 0 -> UiText.get("Left", "Lewa");
            case 1 -> UiText.get("Right", "Prawa");
            case 2 -> UiText.get("Top", "Góra");
            default -> UiText.get("Bottom", "Dół");
        };
    }

    private String partName(int index) {
        return switch (index) {
            case 0 -> UiText.get("Name", "Nazwa");
            case 1 -> UiText.get("Distance", "Odległość");
            case 2 -> UiText.get("Coords", "Koordynaty");
            case 3 -> UiText.get("Profile", "Profil");
            case 4 -> UiText.get("Icon", "Ikona");
            default -> UiText.get("Border", "Ramka");
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
        int index = icon ? 4 : selectedPart;
        WaypointPreset.TextPart part = preview.part(Math.min(3, index));
        numberField(propertyLeft + 4, y, 39, WaypointPresetLayout.position(draft, index, 0), WaypointPreset::position,
                value -> WaypointPresetLayout.position(draft, index, 0, value));
        numberField(propertyLeft + 47, y, 39, WaypointPresetLayout.position(draft, index, 1), WaypointPreset::position,
                value -> WaypointPresetLayout.position(draft, index, 1, value));
        numberField(propertyLeft + 90, y, 49, index == 4 ? draft.iconScale : part.scale, WaypointPreset::scale,
                value -> { if (index == 4) draft.iconScale = value; else part.scale = value; });
        labels.add(new PropertyLabel(draft.anchor(index, 0) == null ? "X" : "X*", y - 10, 0xFF999999, 4));
        labels.add(new PropertyLabel(draft.anchor(index, 1) == null ? "Y" : "Y*", y - 10, 0xFF999999, 47));
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
            entry.widget.setY(propertyTop + entry.y - (int) Math.round(propertyScroll));
            entry.widget.visible = entry.widget.getY() >= propertyTop && entry.widget.getBottom() <= propertyBottom;
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
            selectedEdge = -1;
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
            try (NativeImage image = NativeImage.read(bytes)) {
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

    @Override protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
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
        drawAnchors(context, mouseX, mouseY);
        context.disableScissor();
        context.drawTextWithShadow(textRenderer, Math.round(zoom * 100) + "%", canvasLeft + 4, canvasBottom - 12, 0xFF999999);
        context.enableScissor(propertyLeft, propertyTop, panelRight - 10, propertyBottom);
        int contentTop = propertyTop - (int) Math.round(propertyScroll);
        for (InputOutline outline : outlines) GuiPalette.input(context, outline.x, contentTop + outline.y, outline.width, 18);
        for (PropertyLabel label : labels) context.drawTextWithShadow(textRenderer, label.text, propertyLeft + label.x, contentTop + label.y, label.color);
        for (PropertyWidget entry : properties) if (entry.widget.visible) entry.widget.render(context, mouseX, mouseY, delta);
        context.disableScissor();
        if (maxPropertyScroll() > 0) {
            int track = propertyBottom - propertyTop;
            int thumb = Math.max(20, track * track / PROPERTY_HEIGHT);
            int y = propertyTop + (int) Math.round((track - thumb) * propertyScroll / maxPropertyScroll());
            context.fill(panelRight - 9, propertyTop, panelRight - 7, propertyBottom, 0x805A5A5A);
            context.fill(panelRight - 10, y, panelRight - 6, y + thumb, 0xFF969696);
        }
        for (ClickableWidget widget : fixed) widget.render(context, mouseX, mouseY, delta);
        if (!notice.isEmpty()) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(notice, panelRight - panelLeft - 20), panelLeft + 8, panelBottom - 35, 0xFFCEB36F);
    }

    private void drawAnchors(DrawContext context, int mouseX, int mouseY) {
        if (!preview.enabled(selectedPart)) return;
        if (selectedEdge < 0) {
            int hovered = sourceEdge(mouseX, mouseY);
            if (hovered >= 0) {
                drawEdge(context, preview.bounds(selectedPart), hovered, 0xA096E8A3);
                String label = UiText.get("Attach: ", "Przyklej: ") + edgeName(hovered);
                int labelX = Math.min(canvasRight - textRenderer.getWidth(label) - 5, Math.max(canvasLeft + 4, mouseX + 6));
                int labelY = Math.min(canvasBottom - 22, Math.max(canvasTop + 4, mouseY + 8));
                context.drawTextWithShadow(textRenderer, label, labelX, labelY, 0xFFB9EFC1);
            }
            for (int axis = 0; axis < 2; axis++) {
                WaypointPreset.Anchor anchor = draft.anchor(selectedPart, axis);
                if (anchor == null || !preview.enabled(anchor.target)) continue;
                drawEdge(context, preview.bounds(selectedPart), anchor.ownEdge, 0xB06FAD7B);
                drawEdge(context, preview.bounds(anchor.target), anchor.edge, 0x706FAD7B);
            }
            return;
        }
        drawEdge(context, preview.bounds(selectedPart), selectedEdge, 0xEF96E8A3);
        int hovered = targetEdge(mouseX, mouseY);
        for (int target = 0; target <= 5; target++) {
            if (!preview.enabled(target)) continue;
            for (int edge = selectedEdge / 2 * 2; edge < selectedEdge / 2 * 2 + 2; edge++) {
                if (!WaypointPresetLayout.canAttach(draft, selectedPart, selectedEdge, target, edge)) continue;
                drawEdge(context, preview.bounds(target), edge, hovered == target * 4 + edge ? 0xEF96E8A3 : 0x706FAD7B);
            }
        }
        if (hovered >= 0) {
            String label = partName(hovered / 4) + ": " + edgeName(hovered % 4);
            int labelX = Math.min(canvasRight - textRenderer.getWidth(label) - 5, Math.max(canvasLeft + 4, mouseX + 6));
            int labelY = Math.min(canvasBottom - 22, Math.max(canvasTop + 4, mouseY + 8));
            context.drawTextWithShadow(textRenderer, label, labelX, labelY, 0xFFB9EFC1);
        }
    }

    private void drawEdge(DrawContext context, WaypointPresetPreview.Bounds bounds, int edge, int color) {
        int left = Math.round(originX() + bounds.left() * zoom);
        int right = Math.round(originX() + bounds.right() * zoom);
        int top = Math.round(originY() + bounds.top() * zoom);
        int bottom = Math.round(originY() + bounds.bottom() * zoom);
        switch (edge) {
            case 0 -> context.fill(left - 1, top, left + 1, bottom, color);
            case 1 -> context.fill(right - 1, top, right + 1, bottom, color);
            case 2 -> context.fill(left, top - 1, right, top + 1, color);
            default -> context.fill(left, bottom - 1, right, bottom + 1, color);
        }
    }

    private int sourceEdge(double x, double y) {
        if (!inCanvas(x, y) || !preview.enabled(selectedPart)) return -1;
        return preview.layout().sourceEdge(selectedPart, (x - originX()) / zoom, (y - originY()) / zoom, zoom);
    }

    private int targetEdge(double x, double y) {
        if (selectedEdge < 0 || !inCanvas(x, y) || !preview.enabled(selectedPart)) return -1;
        return preview.layout().targetEdge(draft, selectedPart, selectedEdge,
                (x - originX()) / zoom, (y - originY()) / zoom, zoom);
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        if (selectedEdge >= 0 && button == 1) {
            selectedEdge = -1;
            notice = "";
            clearAndInit();
            return true;
        }
        if (selectedEdge >= 0 && inCanvas(x, y) && button == 0) {
            int target = targetEdge(x, y);
            if (target >= 0 && preview.layout().attach(draft, selectedPart, selectedEdge, target / 4, target % 4)) {
                selectedEdge = -1;
                notice = target / 4 == 5 ? UiText.get("Attached to the border. This element no longer resizes it.",
                        "Przyklejono do ramki. Ten element nie zmienia już jej rozmiaru.")
                        : UiText.get("Attached. Dragging now changes the gap.", "Przyklejono. Przeciąganie zmienia teraz odstęp.");
                clearAndInit();
            }
            setFocused(null);
            return true;
        }
        if (inCanvas(x, y) && button <= 2) {
            int edge = button == 0 ? sourceEdge(x, y) : -1;
            if (edge >= 0) {
                selectedEdge = edge;
                notice = UiText.get("Click a green target edge. Right-click cancels.",
                        "Kliknij zieloną krawędź docelową. Prawy przycisk anuluje.");
                setFocused(null);
                clearAndInit();
                return true;
            }
            int hit = button == 0 ? preview.hit((x - originX()) / zoom, (y - originY()) / zoom) : -1;
            if (hit >= 0) { selectedPart = hit; selectedEdge = -1; draggedPart = hit; clearAndInit(); }
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
            float xPosition = WaypointPresetLayout.position(draft, draggedPart, 0) + (float) dx / zoom;
            float yPosition = WaypointPresetLayout.position(draft, draggedPart, 1) + (float) dy / zoom;
            WaypointPresetLayout.position(draft, draggedPart, 0, Math.max(-512, Math.min(512, xPosition)));
            WaypointPresetLayout.position(draft, draggedPart, 1, Math.max(-512, Math.min(512, yPosition)));
            return true;
        }
        return super.dragContent(x, y, button, dx, dy);
    }

    @Override protected boolean releaseContent(double x, double y, int button) {
        boolean changed = draggedPart >= 0;
        draggedPart = -1;
        panning = false;
        draggingScrollbar = false;
        if (changed) clearAndInit();
        return super.releaseContent(x, y, button);
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

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public void close() { client.setScreen(parent); }
    private record PropertyWidget(ClickableWidget widget, int y) { }
    private record InputOutline(int x, int y, int width) { }
    private record PropertyLabel(String text, int y, int color, int x) {
        PropertyLabel(String text, int y, int color) { this(text, y, color, 4); }
    }
}
