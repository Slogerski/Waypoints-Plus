package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointDimensionFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WaypointManagerScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private final Screen parent;
    private final int requestedPage;
    private final Selection selection;
    private List<Waypoint> entries = List.of();
    private List<String> dimensionFilters = List.of(WaypointDimensionFilter.ALL);
    private int page;
    private String profileName = "Default";

    WaypointManagerScreen(Screen parent) { this(parent, 0, new Selection()); }
    private WaypointManagerScreen(Screen parent, int page, Selection selection) {
        super(Text.literal("Waypoints Plus"));
        this.parent = parent;
        this.requestedPage = page;
        this.selection = selection;
    }

    @Override protected void init() {
        String scope = ServerScope.current();
        WaypointConfigStore store = WaypointsPlusClient.config();
        List<String> profiles = store.profiles(scope);
        int profileIndex = store.activeProfileIndex(scope);
        profileName = profiles.get(profileIndex);
        List<Waypoint> profileEntries = store.waypoints().stream()
                .filter(w -> scope.equals(w.serverKey()) && profileName.equals(w.profile())).toList();
        dimensionFilters = WaypointDimensionFilter.available(
                profileEntries.stream().map(Waypoint::dimension).toList());
        if (!dimensionFilters.contains(selection.dimensionFilter)) {
            selection.dimensionFilter = WaypointDimensionFilter.ALL;
        }
        entries = profileEntries.stream()
                .filter(w -> WaypointDimensionFilter.matches(selection.dimensionFilter, w.dimension())).toList();
        selection.ids.retainAll(new java.util.HashSet<>(entries.stream().map(Waypoint::id).toList()));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(requestedPage, pages - 1));
        int left = width / 2 - 181;

        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("↓ Import", "↓ Importuj")), b -> importClipboard(scope))
                .dimensions(width / 2 - 181, 25, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            store.selectProfile(scope, profileIndex - 1);
            client.setScreen(new WaypointManagerScreen(parent));
        }).dimensions(width / 2 - 105, 25, 28, 20).build()).active = profileIndex > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            store.selectProfile(scope, profileIndex + 1);
            client.setScreen(new WaypointManagerScreen(parent));
        }).dimensions(width / 2 + 77, 25, 28, 20).build()).active = profileIndex + 1 < profiles.size();
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Export ↑", "Eksportuj ↑")), b -> exportSelected())
                .dimensions(width / 2 + 114, 25, 68, 20).build()).active = !selection.ids.isEmpty();

        boolean allSelected = !entries.isEmpty() && selection.ids.size() == entries.size();
        addDrawableChild(ButtonWidget.builder(Text.empty(), b -> toggleAll(allSelected))
                .dimensions(left, 48, 20, 20).build()).active = !entries.isEmpty();
        String deleteLabel = selection.confirmDelete
                ? UiText.get("Confirm Delete", "Potwierdź usunięcie")
                : UiText.get("Delete Selected", "Usuń zaznaczone");
        addDrawableChild(ButtonWidget.builder(Text.literal(deleteLabel), b -> deleteSelected())
                .dimensions(left + 239, 48, 123, 20).build()).active = !selection.ids.isEmpty();

        int y = 73;
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint waypoint = entries.get(i);
            int rowY = y + (i - from) * 25;
            addDrawableChild(ButtonWidget.builder(Text.empty(),
                    b -> toggle(waypoint)).dimensions(left, rowY, 20, 20).build());
            if (WaypointTeleport.available(client, waypoint)) {
                addDrawableChild(ButtonWidget.builder(Text.literal("/tp"),
                        b -> teleportWaypoint(waypoint))
                        .dimensions(left + 239, rowY, 33, 20).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Edit", "Edytuj")),
                    b -> editWaypoint(waypoint))
                    .dimensions(left + 278, rowY, 58, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> deleteWaypoint(waypoint))
                    .dimensions(left + 342, rowY, 20, 20).build());
        }
        int navY = y + PAGE_SIZE * 25 + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> changePage(page - 1))
                .dimensions(left, navY, 36, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> changePage(page + 1))
                .dimensions(left + 43, navY, 36, 20).build()).active = page + 1 < pages;
        addDrawableChild(ButtonWidget.builder(Text.literal(dimensionFilterLabel()), b -> cycleDimensionFilter())
                .dimensions(left + 85, navY, 75, 20).build()).active = dimensionFilters.size() > 1;
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Settings", "Ustawienia")),
                b -> openSettings()).dimensions(left + 166, navY, 130, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Done", "Gotowe")), b -> close())
                .dimensions(left + 302, navY, 60, 20).build());
    }

    private void toggle(Waypoint waypoint) {
        selection.confirmSingleDelete = null;
        if (!selection.ids.add(waypoint.id())) selection.ids.remove(waypoint.id());
        selection.confirmDelete = false;
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void changePage(int targetPage) {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        client.setScreen(new WaypointManagerScreen(parent, targetPage, selection));
    }

    private void teleportWaypoint(Waypoint waypoint) {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        WaypointTeleport.teleport(client, waypoint);
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void editWaypoint(Waypoint waypoint) {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        client.setScreen(new EditWaypointScreen(this, waypoint));
    }

    private void openSettings() {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        client.setScreen(new WaypointSettingsScreen(this));
    }

    private void cycleDimensionFilter() {
        selection.dimensionFilter = WaypointDimensionFilter.next(selection.dimensionFilter, dimensionFilters);
        selection.ids.clear();
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        client.setScreen(new WaypointManagerScreen(parent, 0, selection));
    }

    private String dimensionFilterLabel() {
        return selection.dimensionFilter.isEmpty()
                ? UiText.get("All", "Wszystkie")
                : WaypointDimensionFilter.label(selection.dimensionFilter);
    }

    private void toggleAll(boolean allSelected) {
        selection.confirmSingleDelete = null;
        if (allSelected) selection.ids.clear();
        else entries.forEach(waypoint -> selection.ids.add(waypoint.id()));
        selection.confirmDelete = false;
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void exportSelected() {
        selection.confirmSingleDelete = null;
        List<Waypoint> selected = entries.stream().filter(w -> selection.ids.contains(w.id())).toList();
        if (selected.isEmpty()) return;
        client.keyboard.setClipboard(WaypointTransfer.exportText(selected));
        selection.message = UiText.get("Copied ", "Skopiowano ") + WaypointCountText.format(selected.size());
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void importClipboard(String scope) {
        selection.confirmSingleDelete = null;
        try {
            List<WaypointTransfer.Entry> imported = WaypointTransfer.importText(client.keyboard.getClipboard());
            if (!WaypointTransfer.customDimensions(imported).isEmpty()) {
                client.setScreen(new ImportDimensionWarningScreen(this, imported,
                        client.world.getRegistryKey().getValue().toString(),
                        values -> finishImport(scope, values)));
                return;
            }
            finishImport(scope, imported);
            return;
        } catch (RuntimeException exception) {
            selection.message = UiText.get("Invalid waypoint data in clipboard", "Nieprawidłowe dane w schowku");
        }
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void finishImport(String scope, List<WaypointTransfer.Entry> imported) {
        int added = WaypointsPlusClient.config().importWaypoints(scope, profileName, imported);
        selection.message = UiText.get("Imported ", "Zaimportowano ") + WaypointCountText.format(added);
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void deleteSelected() {
        selection.confirmSingleDelete = null;
        if (!selection.confirmDelete) {
            selection.confirmDelete = true;
            client.setScreen(new WaypointManagerScreen(parent, page, selection));
            return;
        }
        int removed = selection.ids.size();
        WaypointsPlusClient.config().removeWaypoints(Set.copyOf(selection.ids));
        selection.ids.clear();
        selection.confirmDelete = false;
        selection.message = UiText.get("Deleted ", "Usunięto ") + WaypointCountText.format(removed);
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void deleteWaypoint(Waypoint waypoint) {
        if (!waypoint.id().equals(selection.confirmSingleDelete)) {
            selection.confirmSingleDelete = waypoint.id();
            selection.confirmDelete = false;
            client.setScreen(new WaypointManagerScreen(parent, page, selection));
            return;
        }
        WaypointsPlusClient.config().removeWaypoint(waypoint.id());
        selection.ids.remove(waypoint.id());
        selection.confirmSingleDelete = null;
        client.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context);
        }
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(UiText.get("Profile: ", "Profil: ") + profileName), width / 2, 31, 0xFFD946EF);
        int left = width / 2 - 180;
        context.drawTextWithShadow(textRenderer, selection.ids.size() == entries.size() && !entries.isEmpty() ? "☑" : "☐",
                left + 4, 54, 0xFFACACAC);
        context.drawTextWithShadow(textRenderer, WaypointCountText.format(entries.size()), left + 27, 54, 0xFF555555);
        if (!selection.message.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(selection.message), width / 2, 218, 0xFFBBBBBB);
        }
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint w = entries.get(i);
            int y = 79 + (i - from) * 25;
            String rowDimension = w.dimension();
            String dimensionLabel = WaypointDimensionFilter.label(rowDimension);
            float labelScale = 0.75f;
            float labelX = left - 6 - textRenderer.getWidth(dimensionLabel) * labelScale;
            context.getMatrices().push();
            context.getMatrices().translate(labelX, y + 1, 0);
            context.getMatrices().scale(labelScale, labelScale, 1.0f);
            context.drawTextWithShadow(textRenderer, dimensionLabel, 0, 0,
                    WaypointDimensionFilter.labelColor(rowDimension));
            context.getMatrices().pop();
            context.drawTextWithShadow(textRenderer, selection.ids.contains(w.id()) ? "☑" : "☐",
                    left + 4, y, 0xFFACACAC);
            if (w.id().equals(selection.confirmSingleDelete)) {
                context.drawTextWithShadow(textRenderer, UiText.get("Sure?", "Na pewno?"), left + 366, y, 0xFFFFA0A0);
            }
            String line = WaypointsPlusClient.config().settings().showCoordinates
                    ? w.name() + "  [" + w.x() + ", " + w.y() + ", " + w.z() + "]"
                    : w.name();
            context.drawTextWithShadow(textRenderer, line, left + 27, y, parseColor(w.colorArgb()));
        }
    }

    private static int parseColor(String value) {
        try { return (int)Long.parseLong(value.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return 0xFFFFFFFF; }
    }

    @Override public void renderBackground(DrawContext context) { }

    @Override public void close() { client.setScreen(parent); }

    private static final class Selection {
        final Set<java.util.UUID> ids = new HashSet<>();
        String dimensionFilter = WaypointDimensionFilter.ALL;
        String message = "";
        boolean confirmDelete;
        java.util.UUID confirmSingleDelete;
    }
}
