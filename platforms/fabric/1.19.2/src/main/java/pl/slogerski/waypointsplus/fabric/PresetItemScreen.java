package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class PresetItemScreen extends AlertScreen {
    private final Screen parent;
    private final Consumer<String> onSelection;
    private final List<ItemEntry> items = new ArrayList<>();
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private List<ItemEntry> filtered = List.of();
    private int left, top;
    private String query = "";

    PresetItemScreen(Screen parent, Consumer<String> onSelection) {
        super(Text.literal(UiText.get("Select Item", "Wybierz przedmiot")));
        this.parent = parent;
        this.onSelection = onSelection;
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 160;
        top = Math.max(2, (height - 258) / 2);
        if (items.isEmpty()) {
            for (var id : Registry.ITEM.getIds()) {
                var item = Registry.ITEM.get(id);
                if (item != Items.AIR) items.add(new ItemEntry(id.toString(), item.getName().getString(), new ItemStack(item)));
            }
            items.sort(Comparator.comparing(ItemEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(ItemEntry::id));
        }
        buttons.clear();
        TextFieldWidget search = new TextFieldWidget(textRenderer, left + 15, top + 40, 290, 10,
                Text.literal(UiText.get("Search", "Szukaj")));
        search.setDrawsBackground(false);
        search.setMaxLength(64);
        search.setText(query);
        search.setChangedListener(value -> { query = value; filter(); });
        addDrawableChild(search);
        for (int i = 0; i < 6; i++) {
            int index = i;
            buttons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                onSelection.accept(filtered.get(index).id());
                close();
            }).dimensions(left + 36, top + 64 + i * 26, 272, 20).build()));
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wróć")), button -> close())
                .dimensions(left + 90, top + 224, 140, 20).build());
        filter();
        setInitialFocus(search);
    }

    private void filter() {
        String term = query.toLowerCase(Locale.ROOT).trim();
        filtered = items.stream().filter(item -> item.name().toLowerCase(Locale.ROOT).contains(term)
                || item.id().contains(term)).limit(6).toList();
        for (int i = 0; i < buttons.size(); i++) {
            ButtonWidget button = buttons.get(i);
            button.visible = i < filtered.size();
            if (button.visible) button.setMessage(Text.literal(filtered.get(i).name()));
        }
    }

    @Override protected void renderContent(DrawContext context, MatrixStack matrices, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 320, top + 256);
        GuiPalette.input(context, left + 10, top + 35, 300, 20);
        for (int i = 0; i < filtered.size(); i++) context.drawItem(filtered.get(i).stack(), left + 14, top + 66 + i * 26);
        super.renderContent(context, matrices, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, textRenderer, title, width / 2f, top + 7);
    }

    @Override public void renderBackground(MatrixStack matrices) { }
    @Override public void close() { client.setScreen(parent); }
    private record ItemEntry(String id, String name, ItemStack stack) { }
}
