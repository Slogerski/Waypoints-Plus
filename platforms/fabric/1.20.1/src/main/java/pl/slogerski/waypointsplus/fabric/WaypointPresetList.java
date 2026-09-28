package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

final class WaypointPresetList extends ClickableWidget {
    private static final int ROW_HEIGHT = 26;
    private final TextRenderer textRenderer;
    private final Runnable saveFavorite;
    private List<Preset> presets;
    private final Consumer<String> onSelection;
    private String selected = "default";
    private int scroll;
    private boolean draggingScrollbar;

    WaypointPresetList(TextRenderer textRenderer, int x, int y, int width, int height, Runnable saveFavorite,
                       String selected, Consumer<String> onSelection) {
        super(x, y, width, height, Text.literal(UiText.get("Presets", "Szablony")));
        this.textRenderer = textRenderer;
        this.saveFavorite = saveFavorite;
        this.selected = selected;
        this.onSelection = onSelection;
        refreshPresets();
    }

    private void refreshPresets() {
        presets = WaypointPresetStore.list().stream().map(preset -> new Preset(preset,
                new ItemStack(Registries.ITEM.get(new Identifier(preset.item))))).toList();
        scroll = Math.min(scroll, maxScroll());
    }

    private int getRight() { return getX() + getWidth(); }
    private int getBottom() { return getY() + getHeight(); }

    private int listTop() {
        return getY() + 29;
    }

    private int listBottom() {
        return getBottom() - 7;
    }

    private int maxScroll() {
        return Math.max(0, presets.size() * ROW_HEIGHT - (listBottom() - listTop()));
    }

    @Override public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, getX(), getY(), getRight(), getBottom());
        context.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + width / 2, getY() + 11, 0xFFE0E0E0);
        context.enableScissor(getX() + 5, listTop(), getRight() - 5, listBottom());
        for (int i = 0; i < presets.size(); i++) {
            int rowY = listTop() + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT <= listTop() || rowY >= listBottom()) continue;
            Preset preset = presets.get(i);
            int left = getX() + 6;
            int right = getRight() - (maxScroll() > 0 ? 10 : 6);
            boolean selectedRow = selected.equals(preset.data().id);
            boolean hoveredRow = mouseX >= left && mouseX < right && mouseY >= Math.max(rowY, listTop())
                    && mouseY < Math.min(rowY + ROW_HEIGHT - 3, listBottom());
            GuiPalette.input(context, left, rowY, right - left, ROW_HEIGHT - 3,
                    selectedRow ? 0xFFB8BCC2 : hoveredRow ? 0xFF757575 : 0xFF322A2A);
            if (selectedRow) context.fill(left + 1, rowY + 3, left + 3, rowY + ROW_HEIGHT - 6, 0xFFD7DADF);
            context.drawItem(preset.icon(), left + 5, rowY + 3);
            String label = textRenderer.trimToWidth(preset.data().name, Math.max(0, right - left - (saveFavorite == null ? 29 : 43)));
            context.drawTextWithShadow(textRenderer, label, left + 25, rowY + 7, selectedRow ? 0xFFFFFFFF : 0xFFB0B0B0);
            boolean favorite = preset.data().favorite;
            if (saveFavorite != null) context.drawCenteredTextWithShadow(textRenderer, favorite ? "★" : "☆", right - 10, rowY + 7,
                    favorite ? 0xFFF1CE67 : 0xFF999999);
        }
        context.disableScissor();
        if (maxScroll() > 0) {
            int trackHeight = listBottom() - listTop();
            int thumbHeight = Math.max(12, trackHeight * trackHeight / (presets.size() * ROW_HEIGHT));
            int thumbY = listTop() + (trackHeight - thumbHeight) * scroll / maxScroll();
            context.fill(getRight() - 7, listTop(), getRight() - 4, listBottom(), 0x55353535);
            context.fill(getRight() - 7, thumbY, getRight() - 4, thumbY + thumbHeight, 0xFF9B9B9B);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)
                || mouseY < listTop() || mouseY >= listBottom()) return false;
        if (maxScroll() > 0 && mouseX >= getRight() - 9) {
            draggingScrollbar = true;
            moveScrollbar(mouseY);
            return true;
        }
        int index = ((int) mouseY - listTop() + scroll) / ROW_HEIGHT;
        if (index < 0 || index >= presets.size()) return false;
        int rowY = listTop() + index * ROW_HEIGHT - scroll;
        if (mouseY >= rowY + ROW_HEIGHT - 3 || mouseX < getX() + 6) return false;
        int right = getRight() - (maxScroll() > 0 ? 10 : 6);
        if (mouseX >= right) return false;
        if (saveFavorite != null && mouseX >= right - 20) {
            WaypointPreset preset = presets.get(index).data();
            if ("default".equals(preset.id)) {
                WaypointSettings settings = WaypointsPlusClient.config().settings();
                settings.defaultPresetFavorite = !settings.defaultPresetFavorite;
                saveFavorite.run();
            } else {
                WaypointPresetStore.favorite(preset.id, !preset.favorite);
            }
            refreshPresets();
            scroll = 0;
        } else {
            selected = presets.get(index).data().id;
            onSelection.accept(selected);
        }
        playDownSound(net.minecraft.client.MinecraftClient.getInstance().getSoundManager());
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * ROW_HEIGHT)));
        return true;
    }

    private void moveScrollbar(double mouseY) {
        double progress = (mouseY - listTop()) / (listBottom() - listTop());
        scroll = (int) Math.round(Math.max(0, Math.min(1, progress)) * maxScroll());
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!draggingScrollbar || button != 0) return false;
        moveScrollbar(mouseY);
        return true;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!draggingScrollbar || button != 0) return false;
        draggingScrollbar = false;
        return true;
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    private record Preset(WaypointPreset data, ItemStack icon) { }
}
