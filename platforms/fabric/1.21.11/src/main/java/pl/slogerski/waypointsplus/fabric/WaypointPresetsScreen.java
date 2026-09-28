package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

final class WaypointPresetsScreen extends AlertScreen {
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int left, top;
    private double scroll;
    private boolean draggingScrollbar;
    private String pendingDelete;
    private String notice = "";
    private ButtonWidget backButton;

    WaypointPresetsScreen(Screen parent) {
        super(Text.literal(UiText.get("Presets", "Szablony")));
        this.parent = parent;
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 172;
        top = Math.max(2, (height - 258) / 2);
        rows.clear();
        for (WaypointPreset preset : WaypointPresetStore.list()) {
            ButtonWidget edit = addDrawableChild(ButtonWidget.builder(Text.literal(preset.name), button -> {
                pendingDelete = null;
                client.setScreen(new WaypointPresetEditorScreen(this, preset));
            }).dimensions(left + 42, 0, 230, 20).build());
            edit.active = !WaypointPreset.builtIn(preset.id);
            ButtonWidget favorite = addDrawableChild(ButtonWidget.builder(Text.literal(preset.favorite ? "★" : "☆"), button -> {
                pendingDelete = null;
                if ("default".equals(preset.id)) {
                    WaypointSettings settings = WaypointsPlusClient.config().settings();
                    settings.defaultPresetFavorite = !settings.defaultPresetFavorite;
                    if (parent instanceof AdvancedSettingsScreen advanced) advanced.savePresetPreference();
                    else WaypointsPlusClient.config().saveSettings();
                } else if (!WaypointPresetStore.favorite(preset.id, !preset.favorite)) {
                    notice = UiText.get("Could not save the preset.", "Nie udało się zapisać szablonu.");
                }
                clearAndInit();
            }).dimensions(left + 278, 0, 20, 20).build());
            ButtonWidget delete = addDrawableChild(ButtonWidget.builder(Text.literal("X"), button -> delete(preset.id))
                    .dimensions(left + 305, 0, 22, 20).build());
            delete.active = !WaypointPreset.builtIn(preset.id);
            rows.add(new Row(preset.id, new ItemStack(Registries.ITEM.get(Identifier.of(preset.item))), edit, favorite, delete));
        }
        backButton = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wróć")), button -> close())
                .dimensions(left + 104, top + 224, 136, 20).build());
        setScroll(scroll);
    }

    private int viewportTop() { return top + 34; }
    private int viewportBottom() { return top + 218; }
    private int contentHeight() { return Math.max(184, 42 + rows.size() * 26); }
    private int maxScroll() { return Math.max(0, contentHeight() - 184); }

    private void setScroll(double value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        int y = viewportTop() + 42 - (int) Math.round(scroll);
        for (Row row : rows) {
            for (ButtonWidget button : row.buttons()) {
                button.setY(y);
                button.visible = y >= viewportTop() && y + 20 <= viewportBottom();
            }
            y += 26;
        }
    }

    private void delete(String id) {
        if (!id.equals(pendingDelete)) { pendingDelete = id; return; }
        if (!WaypointPresetStore.remove(id)) notice = UiText.get("Could not remove the preset.", "Nie udało się usunąć szablonu.");
        pendingDelete = null;
        clearAndInit();
    }

    @Override protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 344, top + 256);
        context.enableScissor(left + 4, viewportTop(), left + 334, viewportBottom());
        int addY = viewportTop() + 8 - (int) Math.round(scroll);
        GuiPalette.inputOutline(context, left + 17, addY, left + 327, addY + 22);
        boolean hovered = mouseX >= left + 17 && mouseX < left + 327 && mouseY >= addY && mouseY < addY + 22;
        context.drawCenteredTextWithShadow(textRenderer, WaypointPresetStore.canCreate()
                        ? ">                 [+]                 <" : UiText.get("Cannot add more presets", "Nie można dodać kolejnych szablonów"),
                left + 172, addY + 7, hovered ? 0xFFD0D0D0 : 0x805A5A5A);
        for (Row row : rows) {
            if (!row.edit.visible) continue;
            context.drawItem(row.icon, left + 20, row.edit.getY() + 2);
            for (ButtonWidget button : row.buttons()) button.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
        for (Row row : rows) {
            if (row.id.equals(pendingDelete) && row.delete.visible) {
                context.drawTextWithShadow(textRenderer, UiText.get("Sure?", "Na pewno?"), left + 348, row.delete.getY() + 6, 0xFFFFA0A0);
            }
        }
        backButton.render(context, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, textRenderer, title, width / 2f, top + 7);
        if (!notice.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, notice, width / 2, top + 247, 0xFFFFA0A0);
        if (maxScroll() > 0) {
            int thumb = Math.max(24, 184 * 184 / contentHeight());
            int y = viewportTop() + (int) Math.round((184 - thumb) * scroll / maxScroll());
            context.fill(left + 338, viewportTop(), left + 340, viewportBottom(), 0x805A5A5A);
            context.fill(left + 337, y, left + 341, y + thumb, 0xFF969696);
        }
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        boolean confirmation = pendingDelete != null && rows.stream()
                .anyMatch(row -> row.id.equals(pendingDelete) && row.delete.visible && row.delete.isMouseOver(x, y));
        if (!confirmation) pendingDelete = null;
        if (button == 0 && maxScroll() > 0 && x >= left + 334 && x < left + 344 && y >= viewportTop() && y < viewportBottom()) {
            draggingScrollbar = true;
            scrollToMouse(y);
            return true;
        }
        int addY = viewportTop() + 8 - (int) Math.round(scroll);
        if (button == 0 && WaypointPresetStore.canCreate() && x >= left + 17 && x < left + 327
                && y >= Math.max(viewportTop(), addY) && y < Math.min(viewportBottom(), addY + 22)) {
            client.setScreen(new WaypointPresetEditorScreen(this, WaypointPreset.classic()));
            return true;
        }
        return super.clickContent(x, y, button);
    }

    @Override protected boolean scrollContent(double x, double y, double horizontal, double vertical) {
        if (x < left || x >= left + 344 || y < viewportTop() || y >= viewportBottom()) return false;
        pendingDelete = null;
        setScroll(scroll - vertical * 18);
        return true;
    }

    private void scrollToMouse(double y) {
        int thumb = Math.max(24, 184 * 184 / contentHeight());
        setScroll((y - viewportTop() - thumb / 2.0) / Math.max(1, 184 - thumb) * maxScroll());
    }

    @Override protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        if (!draggingScrollbar || button != 0) return super.dragContent(x, y, button, dx, dy);
        scrollToMouse(y);
        return true;
    }

    @Override protected boolean releaseContent(double x, double y, int button) {
        draggingScrollbar = false;
        return super.releaseContent(x, y, button);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public void close() { client.setScreen(parent); }

    private record Row(String id, ItemStack icon, ButtonWidget edit, ButtonWidget favorite, ButtonWidget delete,
                       List<ButtonWidget> buttons) {
        Row(String id, ItemStack icon, ButtonWidget edit, ButtonWidget favorite, ButtonWidget delete) {
            this(id, icon, edit, favorite, delete, List.of(edit, favorite, delete));
        }
    }
}
