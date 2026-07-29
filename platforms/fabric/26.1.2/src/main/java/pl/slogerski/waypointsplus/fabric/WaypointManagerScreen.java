package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.util.List;

final class WaypointManagerScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private final Screen parent;
    private final int requestedPage;
    private List<Waypoint> entries = List.of();
    private int page;
    private String profileName = "Default";

    WaypointManagerScreen(Screen parent) { this(parent, 0); }
    private WaypointManagerScreen(Screen parent, int page) {
        super(Component.literal("Waypoints Plus")); this.parent = parent; this.requestedPage = page;
    }

    @Override protected void init() {
        String scope = ServerScope.current();
        WaypointConfigStore store = WaypointsPlusClient.config();
        List<String> profiles = store.profiles(scope);
        int profileIndex = store.activeProfileIndex(scope);
        profileName = profiles.get(profileIndex);
        entries = store.waypoints().stream()
                .filter(w -> scope.equals(w.serverKey()) && profileName.equals(w.profile())).toList();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(requestedPage, pages - 1));
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            store.selectProfile(scope, profileIndex - 1);
            minecraft.setScreen(new WaypointManagerScreen(parent));
        }).pos(width / 2 - 105, 25).size(28, 20).build()).active = profileIndex > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            store.selectProfile(scope, profileIndex + 1);
            minecraft.setScreen(new WaypointManagerScreen(parent));
        }).pos(width / 2 + 77, 25).size(28, 20).build()).active = profileIndex + 1 < profiles.size();
        int left = width / 2 - 180, y = 62, from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint waypoint = entries.get(i);
            int rowY = y + (i - from) * 25;
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Edit", "Edytuj")),
                    b -> minecraft.setScreen(new EditWaypointScreen(this, waypoint)))
                    .pos(left + 238, rowY).size(58, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Delete", "Usuń")), b -> {
                WaypointsPlusClient.config().removeWaypoint(waypoint.id());
                minecraft.setScreen(new WaypointManagerScreen(parent, page));
            }).pos(left + 301, rowY).size(59, 20).build());
        }
        int navY = y + PAGE_SIZE * 25 + 6;
        Button previous = Button.builder(Component.literal("<"), b -> minecraft.setScreen(new WaypointManagerScreen(parent, page - 1)))
                .pos(left, navY).size(36, 20).build(); previous.active = page > 0; addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), b -> minecraft.setScreen(new WaypointManagerScreen(parent, page + 1)))
                .pos(left + 42, navY).size(36, 20).build(); next.active = page + 1 < pages; addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")),
                b -> minecraft.setScreen(new WaypointSettingsScreen(this))).pos(left + 84, navY).size(190, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Done", "Gotowe")), b -> onClose())
                .pos(left + 280, navY).size(80, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(UiText.get("Profile: ", "Profil: ") + profileName), width / 2, 31, 0xFFD946EF);
        graphics.centeredText(font, Component.literal(WaypointCountText.format(entries.size())), width / 2, 49, 0xFF555555);
        int left = width / 2 - 180, from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint w = entries.get(i);
            String line = WaypointsPlusClient.config().settings().showCoordinates
                    ? w.name() + "  [" + w.x() + ", " + w.y() + ", " + w.z() + "]"
                    : w.name();
            graphics.text(font, line,
                    left + 4, 68 + (i - from) * 25, parseColor(w.colorArgb()), true);
        }
    }

    private static int parseColor(String value) {
        try { return (int)Long.parseLong(value.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return 0xFFFFFFFF; }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
