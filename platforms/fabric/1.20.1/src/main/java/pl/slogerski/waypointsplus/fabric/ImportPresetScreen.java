package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
        super(Text.literal(UiText.get("Select Preset", "Wybierz szablon")));
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
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(index + 1 == missing.size()
                        ? UiText.get("Import", "Importuj") : UiText.get("Next", "Dalej")), button -> advance())
                .dimensions(left + 226, top + 220, 106, 20).build());
        next.active = replacements.containsKey(source);
        addDrawableChild(new WaypointPresetList(textRenderer, left + 12, top + 66, 320, 144, null,
                replacements.getOrDefault(source, ""), id -> {
                    replacements.put(source, id);
                    next.active = true;
                }));
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Cancel", "Anuluj")), button -> close())
                .dimensions(left + 12, top + 220, 106, 20).build());
        ButtonWidget back = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wstecz")), button -> {
            index--; clearAndInit();
        }).dimensions(left + 126, top + 220, 92, 20).build());
        back.active = index > 0;
    }

    private void advance() {
        if (!replacements.containsKey(missing.get(index))) return;
        if (index + 1 < missing.size()) { index++; clearAndInit(); return; }
        try {
            onImport.accept(payload.remapPresets(replacements));
        } catch (RuntimeException exception) {
            notice = UiText.get("Could not import. Check the selected presets.", "Nie udało się zaimportować. Sprawdź wybrane szablony.");
        }
    }

    @Override protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 344, top + 256);
        super.renderContent(context, mouseX, mouseY, delta);
        WaypointSettingsScreen.drawLargeTitle(context, textRenderer, title, width / 2f, top + 7);
        context.drawTextWithShadow(textRenderer, UiText.get("Missing preset. Choose a replacement:",
                "Brak szablonu. Wybierz zamiennik:"), left + 12, top + 35, 0xFFB0B0B0);
        String name = payload.names().getOrDefault(missing.get(index), missing.get(index));
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, 275), left + 12, top + 49, 0xFFE0E0E0);
        context.drawTextWithShadow(textRenderer, (index + 1) + "/" + missing.size(), left + 302, top + 49, 0xFF999999);
        if (!notice.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(notice, 324),
                width / 2, top + 245, 0xFFFF657A);
    }

    @Override public void renderBackground(DrawContext context) { }
    @Override public void close() { client.setScreen(parent); }
}
