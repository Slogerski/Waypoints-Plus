package pl.slogerski.waypointsplus.fabric;

import pl.slogerski.waypointsplus.fabric.remote.RemoteContentService;
import pl.slogerski.waypointsplus.fabric.remote.RemoteContentSession;
import pl.slogerski.waypointsplus.fabric.remote.RemoteTopDonate;
import pl.slogerski.waypointsplus.fabric.remote.TopDonateEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class WaypointSettingsScreen extends Screen {
    private static final int PURPLE = 0xFF7C3AED, MAGENTA = 0xFFD946EF, RED = 0xFFFF405D;
    private static final int FIELD_ACCENT = 0xFFC43D9D;
    private final Screen parent;
    private final int requestedProfileIndex;
    private final Session session;
    private ButtonWidget saveButton;
    private RemoteContentSession<RemoteTopDonate> topDonateSession;
    private RemoteTopDonate topDonate = RemoteTopDonate.EMPTY;
    private boolean topDonateOpened;
    private boolean topDonateExpanded;
    private int left, top;
    private int profileIndex;
    private String profileName = "Default";
    private boolean virtualProfile;

    WaypointSettingsScreen(Screen parent) {
        this(parent, -1, new Session());
    }

    private WaypointSettingsScreen(Screen parent, int profileIndex, Session session) {
        super(Text.literal(UiText.get("Waypoint Settings", "Ustawienia waypointów")));
        this.parent = parent;
        this.requestedProfileIndex = profileIndex;
        this.session = session;
    }

    @Override protected void init() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        topDonateExpanded = settings.topDonateExpanded;
        WaypointConfigStore store = WaypointsPlusClient.config();
        String serverKey = ServerScope.current();
        java.util.List<String> profiles = store.profiles(serverKey);
        profileIndex = requestedProfileIndex < 0 ? store.activeProfileIndex(serverKey)
                : Math.max(0, Math.min(requestedProfileIndex, profiles.size()));
        virtualProfile = profileIndex == profiles.size();
        profileName = virtualProfile ? UiText.get("New Profile", "Nowy profil") : profiles.get(profileIndex);
        left = width / 2 - 150;
        top = Math.max(5, (height - (virtualProfile ? 240 : 252)) / 2);
        if (hasTopDonatePanelSpace()) {
            if (topDonateExpanded) openTopDonate(false);
            else closeTopDonate();
            String heading = UiText.get("Top Supporters", "Topka wspierających");
            addDrawableChild(ButtonWidget.builder(Text.literal(topDonateExpanded ? "△" : "▽"), b -> {
                toggleTopDonate();
                b.setMessage(Text.literal(topDonateExpanded ? "△" : "▽"));
            }).dimensions(left + 307 + textRenderer.getWidth(heading), top + 5, 14, 14).build());
        } else closeTopDonate();
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> openProfile(profileIndex - 1, profiles.size(), serverKey))
                .dimensions(width / 2 - 125, top + 27, 28, 20).build()).active = profileIndex > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> openProfile(profileIndex + 1, profiles.size(), serverKey))
                .dimensions(width / 2 + 97, top + 27, 28, 20).build()).active = profileIndex < profiles.size();
        if (virtualProfile) {
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Add Profile", "Dodaj profil")),
                    b -> client.setScreen(new ProfileNameScreen(this, parent)))
                    .dimensions(left + 35, top + 72, 230, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                    .dimensions(left + 82, top + 112, 136, 20).build());
            return;
        }
        ButtonWidget edit = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Edit", "Edytuj")),
                b -> openProfileEditor())
                .dimensions(left + 10, top + 52, 136, 20).build());
        ButtonWidget remove = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Remove", "Usuń")),
                b -> {
                    applyFields();
                    confirmRemoveProfile(serverKey);
                })
                .dimensions(left + 154, top + 52, 136, 20).build());
        edit.active = remove.active = !"Default".equals(profileName);
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Language: English", "Język: polski")), b -> {
            applyFields();
            settings.language = "pl".equals(settings.language) ? "en" : "pl";
            markDirty();
            client.setScreen(new WaypointSettingsScreen(parent, profileIndex, session));
        }).dimensions(left + 10, top + 84, 280, 20).build());
        addToggle(left + 10, top + 108, 136, UiText.get("Background", "Tło"), settings.background, v -> settings.background = v);
        addToggle(left + 154, top + 108, 136, UiText.get("Coordinates", "Koordynaty"), settings.showCoordinates, v -> settings.showCoordinates = v);
        addToggle(left + 10, top + 132, 136, UiText.get("Distance", "Odległość"), settings.showDistance, v -> settings.showDistance = v);
        addToggle(left + 154, top + 132, 136, UiText.get("Laser", "Laser"), settings.laserEnabled, v -> settings.laserEnabled = v);

        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")),
                b -> openAdvancedSettings())
                .dimensions(left + 10, top + 168, 280, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("About", "O modzie")),
                b -> openAbout())
                .dimensions(left + 10, top + 192, 280, 20).build());
        saveButton = addDrawableChild(ButtonWidget.builder(saveLabel(), b -> save())
                .dimensions(left + 10, top + 216, 136, 20).build());
        saveButton.active = session.dirty;
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                .dimensions(left + 154, top + 216, 136, 20).build());
    }

    private void openProfile(int index, int size, String serverKey) {
        applyFields();
        if (index < size) WaypointsPlusClient.config().selectProfile(serverKey, index);
        client.setScreen(new WaypointSettingsScreen(parent, index, session));
    }

    private void confirmRemoveProfile(String serverKey) {
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) WaypointsPlusClient.config().removeProfile(serverKey, profileName);
            client.setScreen(this);
        }, Text.literal(UiText.get("Remove Profile?", "Usunąć profil?")),
                Text.literal(UiText.get("Its waypoints will also be deleted.", "Jego waypointy również zostaną usunięte."))) {
            @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
                if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                        WaypointsPlusClient.config().settings().menuBackground)) {
                    super.renderBackground(context, mouseX, mouseY, delta);
                }
            }
        });
    }

    private void addToggle(int x, int y, int width, String name, boolean value, Setter setter) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + (value ? "ON" : "OFF")), b -> {
            applyFields();
            setter.set(!value);
            markDirty();
            client.setScreen(new WaypointSettingsScreen(parent, profileIndex, session));
        }).dimensions(x, y, width, 20).build());
    }

    void save() {
        applyFields();
        saveAdvanced();
    }

    void saveAdvanced() {
        WaypointsPlusClient.config().saveSettings();
        session.saved();
        updateSaveButton();
    }

    private void openAdvancedSettings() {
        applyFields();
        client.setScreen(new AdvancedSettingsScreen(this));
    }

    private void openProfileEditor() {
        applyFields();
        client.setScreen(new ProfileNameScreen(this, parent, profileName));
    }

    private void openAbout() {
        applyFields();
        client.setScreen(new AboutScreen(this));
    }

    private void applyFields() {
    }

    void markDirty() {
        session.dirty = true;
        updateSaveButton();
    }

    boolean hasUnsavedChanges() {
        return session.dirty;
    }

    private Text saveLabel() {
        return Text.literal(session.dirty
                ? UiText.get("Not Saved", "Niezapisane")
                : UiText.get("Saved", "Zapisano"));
    }

    private void updateSaveButton() {
        if (saveButton == null) return;
        saveButton.setMessage(saveLabel());
        saveButton.active = session.dirty;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        int bottom = virtualProfile ? top + 150 : top + 250;
        drawPanel(context, left, top, left + 300, bottom);
        if (hasTopDonatePanelSpace()) drawTopDonate(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, MAGENTA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(profileName), width / 2, top + 33, 0xFFFFFFFF);
        if (virtualProfile) return;
    }

    private boolean hasTopDonatePanelSpace() {
        return left + 303 + 120 <= width - 5;
    }

    private void drawTopDonate(DrawContext context) {
        int panelLeft = left + 303;
        int panelTop = top;
        context.drawTextWithShadow(textRenderer,
                Text.literal(UiText.get("Top Supporters", "Topka wspierających")), panelLeft, panelTop + 8, 0xFF039E00);
        if (!topDonateExpanded) return;
        java.util.List<TopDonateEntry> entries = topDonate.entries();
        if (entries.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal(UiText.get("No data", "Brak danych")),
                    panelLeft, panelTop + 30, 0xED454843);
            return;
        }
        int y = panelTop + 30;
        for (int index = 0; index < Math.min(8, entries.size()); index++) {
            TopDonateEntry entry = entries.get(index);
            String rank = "#" + (index + 1) + " ";
            String donor = compactDonateText(entry.name(), 12) + ": ";
            String amount = compactDonateText(entry.formattedAmount(), 12);
            int rankColor = index == 0 ? 0xFFFFD700 : index == 1 ? 0xFFC0C0C0 : index == 2 ? 0xFFCD7F32 : 0xFF858B94;
            int donorX = panelLeft + textRenderer.getWidth(rank);
            int amountX = donorX + textRenderer.getWidth(donor);
            context.drawTextWithShadow(textRenderer, Text.literal(rank), panelLeft, y, rankColor);
            context.drawTextWithShadow(textRenderer, Text.literal(donor), donorX, y, entry.colorArgb());
            context.drawTextWithShadow(textRenderer, Text.literal(amount), amountX, y,
                    0xFF0BFA07);
            y += 14;
        }
    }

    private static String compactDonateText(String text, int length) {
        return text.length() > length ? text.substring(0, length - 1) + "…" : text;
    }

    private void toggleTopDonate() {
        topDonateExpanded = !topDonateExpanded;
        persistTopDonateState();
        if (topDonateExpanded) openTopDonate(true);
        else closeTopDonate();
    }

    private void persistTopDonateState() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        WaypointSettingsSnapshot current = WaypointSettingsSnapshot.capture(settings);
        session.baseline.restore(settings);
        settings.topDonateExpanded = topDonateExpanded;
        WaypointsPlusClient.config().saveSettings();
        current.restore(settings);
        settings.topDonateExpanded = topDonateExpanded;
    }

    private void openTopDonate(boolean forceRefresh) {
        if (topDonateOpened) return;
        topDonateOpened = true;
        RemoteContentSession<RemoteTopDonate> opened =
                RemoteContentService.getDefault().openTopDonate(forceRefresh);
        topDonateSession = opened;
        topDonate = opened.snapshot();
        opened.refresh().thenAccept(snapshot -> client.execute(() -> {
            if (client.currentScreen == this && topDonateSession == opened) topDonate = snapshot;
        }));
    }

    private void closeTopDonate() {
        if (topDonateSession != null) topDonateSession.close();
        topDonateSession = null;
        topDonate = RemoteTopDonate.EMPTY;
        topDonateOpened = false;
    }

    static void fieldBox(DrawContext context, int x, int y, int width, int height) {
        roundedFill(context, x, y, x + width, y + height, 0xA0000000);
        context.fill(x + 2, y, x + width - 2, y + 1, FIELD_ACCENT);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, FIELD_ACCENT);
        context.fill(x, y + 2, x + 1, y + height - 2, FIELD_ACCENT);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, FIELD_ACCENT);
        context.fill(x + 1, y + 1, x + 2, y + 2, FIELD_ACCENT);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, FIELD_ACCENT);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, FIELD_ACCENT);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, FIELD_ACCENT);
    }

    private static void gradientOutline(DrawContext context, int left, int top, int right, int bottom) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            int x1 = left + 2 + (right - left - 4) * i / segments;
            int x2 = left + 2 + (right - left - 4) * (i + 1) / segments;
            context.fill(x1, top, x2, top + 1, mix(PURPLE, RED, i / 23.0f));
            context.fill(x1, bottom - 1, x2, bottom, mix(PURPLE, RED, i / 23.0f));
        }
        context.fill(left, top + 2, left + 1, bottom - 2, PURPLE);
        context.fill(right - 1, top + 2, right, bottom - 2, RED);
        context.fill(left + 1, top + 1, left + 2, top + 2, PURPLE);
        context.fill(right - 2, top + 1, right - 1, top + 2, RED);
        context.fill(left + 1, bottom - 2, left + 2, bottom - 1, PURPLE);
        context.fill(right - 2, bottom - 2, right - 1, bottom - 1, RED);
    }

    private static int mix(int a, int b, float t) {
        int r = (int)(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    private static void roundedFill(DrawContext context, int left, int top, int right, int bottom, int color) {
        context.fill(left + 2, top, right - 2, bottom, color);
        context.fill(left, top + 2, right, bottom - 2, color);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void removed() {
        closeTopDonate();
        super.removed();
    }

    @Override public void close() {
        if (session.dirty) session.baseline.restore(WaypointsPlusClient.config().settings());
        client.setScreen(parent);
    }

    static void drawPanel(DrawContext context, int left, int top, int right, int bottom) {
        roundedFill(context, left, top, right, bottom, 0xE0141824);
        gradientOutline(context, left, top, right, bottom);
    }

    private static final class Session {
        private WaypointSettingsSnapshot baseline =
                WaypointSettingsSnapshot.capture(WaypointsPlusClient.config().settings());
        private boolean dirty;

        private void saved() {
            baseline = WaypointSettingsSnapshot.capture(WaypointsPlusClient.config().settings());
            dirty = false;
        }
    }
    @FunctionalInterface private interface Setter { void set(boolean value); }
}
