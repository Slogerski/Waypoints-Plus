package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class ImportPresetScreen extends AlertScreen {
    private final Screen parent;
    private final WaypointPresetTransfer.Payload payload;
    private final Consumer<WaypointPresetTransfer.Payload> onImport;
    private final List<String> missing;
    private final Map<String, String> replacements = new LinkedHashMap<>();
    private int left, top, index;
    private String notice = "";

    ImportPresetScreen(Screen parent, WaypointPresetTransfer.Payload payload,
                       Consumer<WaypointPresetTransfer.Payload> onImport) {
        super(Component.literal(UiText.get("Select Preset", "Wybierz szablon")));
        this.parent = parent;
        this.payload = payload;
        this.onImport = onImport;
        missing = payload.missingPresets();
        if (missing.isEmpty()) throw new IllegalArgumentException();
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 172;
        top = Math.max(2, (height - 258) / 2);
        String source = missing.get(index);
        Button next = addRenderableWidget(Button.builder(Component.literal(index + 1 == missing.size()
                        ? UiText.get("Import", "Importuj") : UiText.get("Next", "Dalej")), button -> advance())
                .pos(left + 226, top + 220).size(106, 20).build());
        next.active = replacements.containsKey(source);
        addRenderableWidget(new WaypointPresetList(font, left + 12, top + 66, 320, 144, null,
                replacements.getOrDefault(source, ""), id -> {
                    replacements.put(source, id);
                    next.active = true;
                }));
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")), button -> onClose())
                .pos(left + 12, top + 220).size(106, 20).build());
        Button back = addRenderableWidget(Button.builder(Component.literal(UiText.get("Back", "Wstecz")), button -> {
            index--; rebuildWidgets();
        }).pos(left + 126, top + 220).size(92, 20).build());
        back.active = index > 0;
    }

    private void advance() {
        if (!replacements.containsKey(missing.get(index))) return;
        if (index + 1 < missing.size()) { index++; rebuildWidgets(); return; }
        try {
            onImport.accept(payload.remapPresets(replacements));
        } catch (RuntimeException exception) {
            notice = UiText.get("Could not import. Check the selected presets.", "Nie udało się zaimportować. Sprawdź wybrane szablony.");
        }
    }

    @Override protected void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 344, top + 256);
        super.extractContent(context, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, font, title, width / 2f, top + 7);
        context.text(font, UiText.get("Missing preset. Choose a replacement:",
                "Brak szablonu. Wybierz zamiennik:"), left + 12, top + 35, 0xFFB0B0B0);
        String name = payload.names().getOrDefault(missing.get(index), missing.get(index));
        context.text(font, font.plainSubstrByWidth(name, 275), left + 12, top + 49, 0xFFE0E0E0);
        context.text(font, (index + 1) + "/" + missing.size(), left + 302, top + 49, 0xFF999999);
        if (!notice.isEmpty()) context.centeredText(font, font.plainSubstrByWidth(notice, 324),
                width / 2, top + 245, 0xFFFF657A);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) { }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
