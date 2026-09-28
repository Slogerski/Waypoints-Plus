package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class PresetItemScreen extends AlertScreen {
    private final Screen parent;
    private final Consumer<String> onSelection;
    private final List<ItemEntry> items = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private List<ItemEntry> filtered = List.of();
    private int left, top;
    private String query = "";

    PresetItemScreen(Screen parent, Consumer<String> onSelection) {
        super(Component.literal(UiText.get("Select Item", "Wybierz przedmiot")));
        this.parent = parent;
        this.onSelection = onSelection;
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 160;
        top = Math.max(2, (height - 258) / 2);
        if (items.isEmpty()) {
            for (var id : BuiltInRegistries.ITEM.keySet()) {
                var item = BuiltInRegistries.ITEM.getValue(id);
                if (item != Items.AIR) items.add(new ItemEntry(id.toString(), new ItemStack(item).getHoverName().getString(), new ItemStack(item)));
            }
            items.sort(Comparator.comparing(ItemEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(ItemEntry::id));
        }
        buttons.clear();
        EditBox search = new EditBox(font, left + 15, top + 40, 290, 10,
                Component.literal(UiText.get("Search", "Szukaj")));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(value -> { query = value; filter(); });
        addRenderableWidget(search);
        for (int i = 0; i < 6; i++) {
            int index = i;
            buttons.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                onSelection.accept(filtered.get(index).id());
                onClose();
            }).pos(left + 36, top + 64 + i * 26).size(272, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Back", "Wróć")), button -> onClose())
                .pos(left + 90, top + 224).size(140, 20).build());
        filter();
        setInitialFocus(search);
    }

    private void filter() {
        String term = query.toLowerCase(Locale.ROOT).trim();
        filtered = items.stream().filter(item -> item.name().toLowerCase(Locale.ROOT).contains(term)
                || item.id().contains(term)).limit(6).toList();
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            button.visible = i < filtered.size();
            if (button.visible) button.setMessage(Component.literal(filtered.get(i).name()));
        }
    }

    @Override protected void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 320, top + 256);
        GuiPalette.input(context, left + 10, top + 35, 300, 20);
        for (int i = 0; i < filtered.size(); i++) context.item(filtered.get(i).stack(), left + 14, top + 66 + i * 26);
        super.extractContent(context, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, font, title, width / 2f, top + 7);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) { }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    private record ItemEntry(String id, String name, ItemStack stack) { }
}
