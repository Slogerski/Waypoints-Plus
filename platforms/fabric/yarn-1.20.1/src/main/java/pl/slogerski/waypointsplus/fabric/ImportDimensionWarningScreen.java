package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class ImportDimensionWarningScreen extends Screen {
    private static final int ROWS_PER_PAGE = 3;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private final Screen parent;
    private final Consumer<List<WaypointTransfer.Entry>> importer;
    private final State state;
    private final boolean mapping;
    private final int page;

    ImportDimensionWarningScreen(Screen parent, List<WaypointTransfer.Entry> entries, String currentDimension,
                                 Consumer<List<WaypointTransfer.Entry>> importer) {
        this(parent, importer, new State(entries, currentDimension), false, 0);
    }

    private ImportDimensionWarningScreen(Screen parent, Consumer<List<WaypointTransfer.Entry>> importer,
                                         State state, boolean mapping, int page) {
        super(Text.literal(UiText.get("Custom Dimension Warning", "Ostrzeżenie o niestandardowych wymiarach")));
        this.parent = parent;
        this.importer = importer;
        this.state = state;
        this.mapping = mapping;
        this.page = page;
    }

    @Override protected void init() {
        if (!mapping) {
            int y = height / 2 + 28;
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Cancel", "Anuluj")), b -> close())
                    .dimensions(width / 2 - 156, y, 96, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Map", "Zmapuj")), b -> openMapping(0))
                    .dimensions(width / 2 - 48, y, 96, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Keep", "Zachowaj")), b -> importer.accept(state.entries))
                    .dimensions(width / 2 + 60, y, 96, 20).build());
            return;
        }

        int left = width / 2 - 142;
        int from = page * ROWS_PER_PAGE;
        int to = Math.min(state.dimensions.size(), from + ROWS_PER_PAGE);
        for (int i = from; i < to; i++) {
            String source = state.dimensions.get(i);
            int y = 66 + (i - from) * 38;
            addTargetButton(source, OVERWORLD, "Overworld", left, y, 56);
            addTargetButton(source, NETHER, "Nether", left + 59, y, 48);
            addTargetButton(source, END, "End", left + 110, y, 40);
            addTargetButton(source, state.currentDimension, displayDimension(state.currentDimension),
                    left + 153, y, 130);
        }

        int pages = (state.dimensions.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
        int bottom = Math.min(height - 28, 199);
        if (pages > 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> openMapping(page - 1))
                    .dimensions(width / 2 - 35, bottom - 24, 32, 20).build()).active = page > 0;
            addDrawableChild(ButtonWidget.builder(Text.literal("\u003e"), b -> openMapping(page + 1))
                    .dimensions(width / 2 + 3, bottom - 24, 32, 20).build()).active = page + 1 < pages;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wstecz")), b -> openWarning())
                .dimensions(width / 2 - 89, bottom, 86, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Import", "Importuj")), b -> importMapped())
                .dimensions(width / 2 + 3, bottom, 86, 20).build());
    }

    private void addTargetButton(String source, String target, String label, int x, int y, int width) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), b -> selectTarget(source, target))
                .dimensions(x, y, width, 20).build();
        button.active = !target.equals(state.targets.get(source));
        addDrawableChild(button);
    }

    private void selectTarget(String source, String target) {
        state.targets.put(source, target);
        openMapping(page);
    }

    private void importMapped() {
        Map<String, String> replacements = new LinkedHashMap<>();
        for (String source : state.dimensions) {
            replacements.put(source, state.targets.get(source));
        }
        importer.accept(WaypointTransfer.remapDimensions(state.entries, replacements));
    }

    private void openWarning() {
        client.setScreen(new ImportDimensionWarningScreen(parent, importer, state, false, 0));
    }

    private void openMapping(int targetPage) {
        int pages = Math.max(1, (state.dimensions.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        client.setScreen(new ImportDimensionWarningScreen(parent, importer, state, true,
                Math.max(0, Math.min(targetPage, pages - 1))));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context);
        }
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFD946EF);
        if (!mapping) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(UiText.get("The import contains custom dimensions.",
                            "Import zawiera niestandardowe wymiary.")), width / 2, height / 2 - 18, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(UiText.get("Accept them unchanged or replace their destinations.",
                            "Zaakceptuj je bez zmian albo zamień ich wymiary docelowe.")),
                    width / 2, height / 2 - 4, 0xFFACACAC);
            return;
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(UiText.get("Choose a destination for each imported dimension.",
                        "Wybierz wymiar docelowy dla każdego importowanego wymiaru.")),
                width / 2, 36, 0xFFACACAC);
        int left = width / 2 - 142;
        int from = page * ROWS_PER_PAGE;
        int to = Math.min(state.dimensions.size(), from + ROWS_PER_PAGE);
        for (int i = from; i < to; i++) {
            String source = state.dimensions.get(i);
            context.drawTextWithShadow(textRenderer, source, left, 54 + (i - from) * 38, 0xFFFFFFFF);
        }
    }

    @Override public void renderBackground(DrawContext context) { }

    @Override public void close() { client.setScreen(parent); }

    private static final class State {
        final List<WaypointTransfer.Entry> entries;
        final List<String> dimensions;
        final Map<String, String> targets = new LinkedHashMap<>();
        final String currentDimension;
        State(List<WaypointTransfer.Entry> entries, String currentDimension) {
            this.entries = entries;
            this.dimensions = WaypointTransfer.customDimensions(entries);
            this.currentDimension = currentDimension;
            for (String dimension : dimensions) {
                targets.put(dimension, OVERWORLD);
            }
        }
    }

    private static String displayDimension(String dimension) {
        int separator = dimension.indexOf(':');
        String name = separator >= 0 ? dimension.substring(separator + 1) : dimension;
        while (name.startsWith("worlds/") || name.startsWith("world/") || name.startsWith("server/")) {
            name = name.substring(name.indexOf('/') + 1);
        }
        return name;
    }
}
