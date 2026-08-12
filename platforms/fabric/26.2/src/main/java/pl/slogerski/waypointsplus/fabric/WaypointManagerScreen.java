package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WaypointManagerScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private final Screen parent;
    private final int requestedPage;
    private final Selection selection;
    private List<Waypoint> entries = List.of();
    private int page;
    private String profileName = "Default";

    WaypointManagerScreen(Screen parent) { this(parent, 0, new Selection()); }
    private WaypointManagerScreen(Screen parent, int page, Selection selection) {
        super(Component.literal("Waypoints Plus"));
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
        entries = store.waypoints().stream()
                .filter(w -> scope.equals(w.serverKey()) && profileName.equals(w.profile())).toList();
        selection.ids.retainAll(entries.stream().map(Waypoint::id).toList());
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(requestedPage, pages - 1));
        int left = width / 2 - 181;
        addRenderableWidget(Button.builder(Component.literal(UiText.get("↓ Import", "↓ Importuj")), b -> importClipboard(scope))
                .pos(width / 2 - 181, 25).size(68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            store.selectProfile(scope, profileIndex - 1);
            minecraft.gui.setScreen(new WaypointManagerScreen(parent));
        }).pos(width / 2 - 105, 25).size(28, 20).build()).active = profileIndex > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            store.selectProfile(scope, profileIndex + 1);
            minecraft.gui.setScreen(new WaypointManagerScreen(parent));
        }).pos(width / 2 + 77, 25).size(28, 20).build()).active = profileIndex + 1 < profiles.size();
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Export ↑", "Eksportuj ↑")), b -> exportSelected())
                .pos(width / 2 + 113, 25).size(68, 20).build()).active = !selection.ids.isEmpty();
        boolean allSelected = !entries.isEmpty() && selection.ids.size() == entries.size();
        addRenderableWidget(Button.builder(Component.empty(), b -> toggleAll(allSelected))
                .pos(left, 48).size(20, 20).build()).active = !entries.isEmpty();
        String deleteLabel = selection.confirmDelete
                ? UiText.get("Confirm Delete", "Potwierdź usunięcie")
                : UiText.get("Delete Selected", "Usuń zaznaczone");
        addRenderableWidget(Button.builder(Component.literal(deleteLabel), b -> deleteSelected())
                .pos(left + 239, 48).size(123, 20).build()).active = !selection.ids.isEmpty();
        int y = 73, from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint waypoint = entries.get(i);
            int rowY = y + (i - from) * 25;
            addRenderableWidget(Button.builder(Component.empty(),
                    b -> toggle(waypoint)).pos(left, rowY).size(20, 20).build());
            if (WaypointTeleport.available(minecraft, waypoint)) {
                addRenderableWidget(Button.builder(Component.literal("/tp"),
                        b -> teleportWaypoint(waypoint))
                        .pos(left + 240, rowY).size(33, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Edit", "Edytuj")),
                    b -> editWaypoint(waypoint))
                    .pos(left + 278, rowY).size(58, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> deleteWaypoint(waypoint))
                    .pos(left + 342, rowY).size(20, 20).build());
        }
        int navY = y + PAGE_SIZE * 25 + 6;
        Button previous = Button.builder(Component.literal("<"), b -> changePage(page - 1))
                .pos(left, navY).size(36, 20).build(); previous.active = page > 0; addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), b -> changePage(page + 1))
                .pos(left + 43, navY).size(36, 20).build(); next.active = page + 1 < pages; addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Settings", "Ustawienia")),
                b -> openSettings()).pos(left + 85, navY).size(191, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Done", "Gotowe")), b -> onClose())
                .pos(left + 282, navY).size(80, 20).build());
    }

    private void toggle(Waypoint waypoint) {
        selection.confirmSingleDelete = null;
        if (!selection.ids.add(waypoint.id())) selection.ids.remove(waypoint.id());
        selection.confirmDelete = false;
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void teleportWaypoint(Waypoint waypoint) {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        WaypointTeleport.teleport(minecraft, waypoint);
    }

    private void changePage(int targetPage) {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, targetPage, selection));
    }

    private void editWaypoint(Waypoint waypoint) {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        minecraft.gui.setScreen(new EditWaypointScreen(this, waypoint));
    }

    private void openSettings() {
        selection.confirmSingleDelete = null;
        selection.confirmDelete = false;
        minecraft.gui.setScreen(new WaypointSettingsScreen(this));
    }

    private void toggleAll(boolean allSelected) {
        selection.confirmSingleDelete = null;
        if (allSelected) selection.ids.clear();
        else entries.forEach(waypoint -> selection.ids.add(waypoint.id()));
        selection.confirmDelete = false;
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void exportSelected() {
        selection.confirmSingleDelete = null;
        List<Waypoint> selected = entries.stream().filter(w -> selection.ids.contains(w.id())).toList();
        if (selected.isEmpty()) return;
        minecraft.keyboardHandler.setClipboard(WaypointTransfer.exportText(selected));
        selection.message = UiText.get("Copied ", "Skopiowano ") + WaypointCountText.format(selected.size());
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void importClipboard(String scope) {
        selection.confirmSingleDelete = null;
        try {
            List<WaypointTransfer.Entry> imported = WaypointTransfer.importText(minecraft.keyboardHandler.getClipboard());
            int added = WaypointsPlusClient.config().importWaypoints(scope, profileName, imported);
            selection.message = UiText.get("Imported ", "Zaimportowano ") + WaypointCountText.format(added);
        } catch (RuntimeException exception) {
            selection.message = UiText.get("Invalid waypoint data in clipboard", "Nieprawidłowe dane w schowku");
        }
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void deleteSelected() {
        selection.confirmSingleDelete = null;
        if (!selection.confirmDelete) {
            selection.confirmDelete = true;
            minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
            return;
        }
        int removed = selection.ids.size();
        WaypointsPlusClient.config().removeWaypoints(Set.copyOf(selection.ids));
        selection.ids.clear();
        selection.confirmDelete = false;
        selection.message = UiText.get("Deleted ", "Usunięto ") + WaypointCountText.format(removed);
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    private void deleteWaypoint(Waypoint waypoint) {
        if (!waypoint.id().equals(selection.confirmSingleDelete)) {
            selection.confirmSingleDelete = waypoint.id();
            selection.confirmDelete = false;
            minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
            return;
        }
        WaypointsPlusClient.config().removeWaypoint(waypoint.id());
        selection.ids.remove(waypoint.id());
        selection.confirmSingleDelete = null;
        minecraft.gui.setScreen(new WaypointManagerScreen(parent, page, selection));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(UiText.get("Profile: ", "Profil: ") + profileName), width / 2, 31, 0xFFD946EF);
        int left = width / 2 - 180;
        graphics.text(font, selection.ids.size() == entries.size() && !entries.isEmpty() ? "☑" : "☐",
                left + 4, 54, 0xFFACACAC, true);
        graphics.text(font, WaypointCountText.format(entries.size()), left + 26, 54, 0xFF555555, true);
        if (!selection.message.isEmpty()) {
            graphics.centeredText(font, Component.literal(selection.message), width / 2, 218, 0xFFBBBBBB);
        }
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint w = entries.get(i);
            graphics.text(font, selection.ids.contains(w.id()) ? "☑" : "☐",
                    left + 4, 79 + (i - from) * 25, 0xFFACACAC, true);
            if (w.id().equals(selection.confirmSingleDelete)) {
                graphics.text(font, UiText.get("Sure?", "Na pewno?"),
                        left + 365, 79 + (i - from) * 25, 0xFFFFA0A0, true);
            }
            String line = WaypointsPlusClient.config().settings().showCoordinates
                    ? w.name() + "  [" + w.x() + ", " + w.y() + ", " + w.z() + "]"
                    : w.name();
            graphics.text(font, line,
                    left + 26, 79 + (i - from) * 25, parseColor(w.colorArgb()), true);
        }
    }

    private static int parseColor(String value) {
        try { return (int)Long.parseLong(value.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return 0xFFFFFFFF; }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    private static final class Selection {
        final Set<java.util.UUID> ids = new HashSet<>();
        String message = "";
        boolean confirmDelete;
        java.util.UUID confirmSingleDelete;
    }
}
