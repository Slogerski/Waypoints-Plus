package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class WaypointSettingsScreen extends Screen {
    private static final int PURPLE = 0xFF7C3AED, MAGENTA = 0xFFD946EF, RED = 0xFFFF405D;
    private static final int FIELD_ACCENT = 0xFFC43D9D;
    private final Screen parent;
    private final int requestedProfileIndex;
    private final Session session;
    private Button saveButton;
    private int left, top;
    private int profileIndex;
    private String profileName = "Default";
    private boolean virtualProfile;

    WaypointSettingsScreen(Screen parent) {
        this(parent, -1, new Session());
    }

    private WaypointSettingsScreen(Screen parent, int profileIndex, Session session) {
        super(Component.literal(UiText.get("Waypoint Settings", "Ustawienia waypointów")));
        this.parent = parent;
        this.requestedProfileIndex = profileIndex;
        this.session = session;
    }

    @Override protected void init() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        WaypointConfigStore store = WaypointsPlusClient.config();
        String serverKey = ServerScope.current();
        java.util.List<String> profiles = store.profiles(serverKey);
        profileIndex = requestedProfileIndex < 0 ? store.activeProfileIndex(serverKey)
                : Math.max(0, Math.min(requestedProfileIndex, profiles.size()));
        virtualProfile = profileIndex == profiles.size();
        profileName = virtualProfile ? UiText.get("New Profile", "Nowy profil") : profiles.get(profileIndex);
        left = width / 2 - 150;
        top = Math.max(5, (height - (virtualProfile ? 240 : 252)) / 2);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> openProfile(profileIndex - 1, profiles.size(), serverKey))
                .pos(width / 2 - 125, top + 27).size(28, 20).build()).active = profileIndex > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> openProfile(profileIndex + 1, profiles.size(), serverKey))
                .pos(width / 2 + 97, top + 27).size(28, 20).build()).active = profileIndex < profiles.size();
        if (virtualProfile) {
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Add Profile", "Dodaj profil")),
                    b -> minecraft.gui.setScreen(new ProfileNameScreen(this, parent)))
                    .pos(left + 35, top + 72).size(230, 20).build());
            addRenderableWidget(Button.builder(Component.literal(UiText.get("Exit", "Wyjdź")), b -> onClose())
                    .pos(left + 82, top + 112).size(136, 20).build());
            return;
        }
        Button edit = addRenderableWidget(Button.builder(Component.literal(UiText.get("Edit", "Edytuj")),
                b -> openProfileEditor())
                .pos(left + 10, top + 52).size(136, 20).build());
        Button remove = addRenderableWidget(Button.builder(Component.literal(UiText.get("Remove", "Usuń")),
                b -> {
                    applyFields();
                    confirmRemoveProfile(serverKey);
                })
                .pos(left + 154, top + 52).size(136, 20).build());
        edit.active = remove.active = !"Default".equals(profileName);
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Language: English", "Język: polski")), b -> {
            applyFields();
            settings.language = "pl".equals(settings.language) ? "en" : "pl";
            markDirty();
            minecraft.gui.setScreen(new WaypointSettingsScreen(parent, profileIndex, session));
        }).pos(left + 10, top + 84).size(280, 20).build());
        addToggle(left + 10, top + 108, 136, UiText.get("Background", "Tło"), settings.background, v -> settings.background = v);
        addToggle(left + 154, top + 108, 136, UiText.get("Coordinates", "Koordynaty"), settings.showCoordinates, v -> settings.showCoordinates = v);
        addToggle(left + 10, top + 132, 136, UiText.get("Distance", "Odległość"), settings.showDistance, v -> settings.showDistance = v);
        addToggle(left + 154, top + 132, 136, UiText.get("Laser", "Laser"), settings.laserEnabled, v -> settings.laserEnabled = v);

        addRenderableWidget(Button.builder(Component.literal(UiText.get("Advanced Settings", "Ustawienia zaawansowane")),
                b -> openAdvancedSettings())
                .pos(left + 10, top + 168).size(280, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("About", "O modzie")),
                b -> openAbout())
                .pos(left + 10, top + 192).size(280, 20).build());
        saveButton = addRenderableWidget(Button.builder(saveLabel(), b -> save())
                .pos(left + 10, top + 216).size(136, 20).build());
        saveButton.active = session.dirty;
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Exit", "Wyjdź")), b -> onClose())
                .pos(left + 154, top + 216).size(136, 20).build());
    }

    private void openProfile(int index, int size, String serverKey) {
        applyFields();
        if (index < size) WaypointsPlusClient.config().selectProfile(serverKey, index);
        minecraft.gui.setScreen(new WaypointSettingsScreen(parent, index, session));
    }

    private void confirmRemoveProfile(String serverKey) {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) WaypointsPlusClient.config().removeProfile(serverKey, profileName);
            minecraft.gui.setScreen(this);
        }, Component.literal(UiText.get("Remove Profile?", "Usunąć profil?")),
                Component.literal(UiText.get("Its waypoints will also be deleted.", "Jego waypointy również zostaną usunięte."))) {
            @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                        WaypointsPlusClient.config().settings().menuBackground)) {
                    super.extractBackground(graphics, mouseX, mouseY, delta);
                }
            }
        });
    }

    private void addToggle(int x, int y, int width, String name, boolean value, Setter setter) {
        addRenderableWidget(Button.builder(Component.literal(name + ": " + (value ? "ON" : "OFF")), b -> {
            applyFields();
            setter.set(!value);
            markDirty();
            minecraft.gui.setScreen(new WaypointSettingsScreen(parent, profileIndex, session));
        }).pos(x, y).size(width, 20).build());
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
        minecraft.gui.setScreen(new AdvancedSettingsScreen(this));
    }

    private void openProfileEditor() {
        applyFields();
        minecraft.gui.setScreen(new ProfileNameScreen(this, parent, profileName));
    }

    private void openAbout() {
        applyFields();
        minecraft.gui.setScreen(new AboutScreen(this));
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

    private Component saveLabel() {
        return Component.literal(session.dirty
                ? UiText.get("Not Saved", "Niezapisane")
                : UiText.get("Saved", "Zapisano"));
    }

    private void updateSaveButton() {
        if (saveButton == null) return;
        saveButton.setMessage(saveLabel());
        saveButton.active = session.dirty;
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int bottom = virtualProfile ? top + 150 : top + 250;
        drawPanel(graphics, left, top, left + 300, bottom);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 8, MAGENTA);
        graphics.centeredText(font, Component.literal(profileName), width / 2, top + 33, 0xFFFFFFFF);
        if (virtualProfile) return;
    }

    static void fieldBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        roundedFill(graphics, x, y, x + width, y + height, 0xA0000000);
        graphics.fill(x + 2, y, x + width - 2, y + 1, FIELD_ACCENT);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, FIELD_ACCENT);
        graphics.fill(x, y + 2, x + 1, y + height - 2, FIELD_ACCENT);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, FIELD_ACCENT);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, FIELD_ACCENT);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, FIELD_ACCENT);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, FIELD_ACCENT);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, FIELD_ACCENT);
    }

    private static void gradientOutline(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            int x1 = left + 2 + (right - left - 4) * i / segments;
            int x2 = left + 2 + (right - left - 4) * (i + 1) / segments;
            graphics.fill(x1, top, x2, top + 1, mix(PURPLE, RED, i / 23.0f));
            graphics.fill(x1, bottom - 1, x2, bottom, mix(PURPLE, RED, i / 23.0f));
        }
        graphics.fill(left, top + 2, left + 1, bottom - 2, PURPLE);
        graphics.fill(right - 1, top + 2, right, bottom - 2, RED);
        graphics.fill(left + 1, top + 1, left + 2, top + 2, PURPLE);
        graphics.fill(right - 2, top + 1, right - 1, top + 2, RED);
        graphics.fill(left + 1, bottom - 2, left + 2, bottom - 1, PURPLE);
        graphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, RED);
    }

    private static int mix(int a, int b, float t) {
        int r = (int)(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    private static void roundedFill(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left + 2, top, right - 2, bottom, color);
        graphics.fill(left, top + 2, right, bottom - 2, color);
    }

    @Override public void onClose() {
        if (session.dirty) session.baseline.restore(WaypointsPlusClient.config().settings());
        Minecraft.getInstance().gui.setScreen(parent);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
        roundedFill(graphics, left, top, right, bottom, 0xE0141824);
        gradientOutline(graphics, left, top, right, bottom);
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
