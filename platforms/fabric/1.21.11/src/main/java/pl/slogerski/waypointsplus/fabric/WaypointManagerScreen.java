package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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
        super(Text.literal("Waypoints Plus"));
        this.parent = parent;
        this.requestedPage = page;
    }

    @Override protected void init() {
        String scope = ServerScope.current();
        WaypointConfigStore store = WaypointsPlusClient.config();
        List<String> profiles = store.profiles(scope);
        int profileIndex = store.activeProfileIndex(scope);
        profileName = profiles.get(profileIndex);
        entries = WaypointsPlusClient.config().waypoints().stream()
                .filter(w -> scope.equals(w.serverKey()) && profileName.equals(w.profile())).toList();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(requestedPage, pages - 1));
        int left = width / 2 - 180;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            store.selectProfile(scope, profileIndex - 1);
            client.setScreen(new WaypointManagerScreen(parent));
        }).dimensions(width / 2 - 105, 25, 28, 20).build()).active = profileIndex > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            store.selectProfile(scope, profileIndex + 1);
            client.setScreen(new WaypointManagerScreen(parent));
        }).dimensions(width / 2 + 77, 25, 28, 20).build()).active = profileIndex + 1 < profiles.size();
        int y = 62;
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint waypoint = entries.get(i);
            int rowY = y + (i - from) * 25;
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Edit", "Edytuj")),
                    b -> client.setScreen(new EditWaypointScreen(this, waypoint)))
                    .dimensions(left + 238, rowY, 58, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Delete", "Usuń")), b -> {
                WaypointsPlusClient.config().removeWaypoint(waypoint.id());
                client.setScreen(new WaypointManagerScreen(parent, page));
            }).dimensions(left + 301, rowY, 59, 20).build());
        }
        int navY = y + PAGE_SIZE * 25 + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> client.setScreen(new WaypointManagerScreen(parent, page - 1)))
                .dimensions(left, navY, 36, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> client.setScreen(new WaypointManagerScreen(parent, page + 1)))
                .dimensions(left + 42, navY, 36, 20).build()).active = page + 1 < pages;
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Settings", "Ustawienia")),
                b -> client.setScreen(new WaypointSettingsScreen(this))).dimensions(left + 84, navY, 190, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Done", "Gotowe")), b -> close())
                .dimensions(left + 280, navY, 80, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(UiText.get("Profile: ", "Profil: ") + profileName), width / 2, 31, 0xFFD946EF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(WaypointCountText.format(entries.size())), width / 2, 49, 0xFF555555);
        int left = width / 2 - 180;
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            Waypoint w = entries.get(i);
            int y = 68 + (i - from) * 25;
            String line = WaypointsPlusClient.config().settings().showCoordinates
                    ? w.name() + "  [" + w.x() + ", " + w.y() + ", " + w.z() + "]"
                    : w.name();
            context.drawTextWithShadow(textRenderer, line, left + 4, y, parseColor(w.colorArgb()));
        }
    }

    private static int parseColor(String value) {
        try { return (int)Long.parseLong(value.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return 0xFFFFFFFF; }
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override public void close() { client.setScreen(parent); }
}
