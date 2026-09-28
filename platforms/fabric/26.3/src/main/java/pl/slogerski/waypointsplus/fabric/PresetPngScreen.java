package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.function.Consumer;

final class PresetPngScreen extends AlertScreen {
    private final Screen parent;
    private final Consumer<Path> onSelection;
    private EditBox path;
    private int left, top;

    PresetPngScreen(Screen parent, Consumer<Path> onSelection) {
        super(Component.literal(UiText.get("Select PNG", "Wybierz PNG")));
        this.parent = parent;
        this.onSelection = onSelection;
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 172;
        top = (height - 124) / 2;
        String previous = path == null ? "" : path.getValue();
        path = new EditBox(font, left + 14, top + 49, 316, 20,
                Component.literal(UiText.get("PNG File Path", "Ścieżka do pliku PNG")));
        path.setMaxLength(1024);
        path.setValue(previous);
        addRenderableWidget(path);
        setInitialFocus(path);
        Button load = addRenderableWidget(Button.builder(Component.literal(UiText.get("Load", "Wczytaj")), button -> {
            try {
                Path selected = Path.of(path.getValue().trim());
                minecraft.gui.setScreen(parent);
                onSelection.accept(selected);
            } catch (RuntimeException exception) {
                path.setTextColor(0xFFFF657A);
            }
        }).pos(left + 176, top + 89).size(154, 20).build());
        load.active = !previous.isBlank();
        path.setResponder(value -> load.active = !value.isBlank());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")), button -> onClose())
                .pos(left + 14, top + 89).size(154, 20).build());
    }

    @Override protected void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 344, top + 124);
        super.extractContent(context, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, font, title, width / 2f, top + 8);
        context.text(font, UiText.get("Paste the path to a 16, 32 or 64 px PNG.",
                "Wklej ścieżkę do PNG o rozmiarze 16, 32 lub 64 px."), left + 14, top + 33, 0xFFB0B0B0);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) { }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
