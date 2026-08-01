package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

final class WaypointSettingsScreen extends Screen {
    private static final int PURPLE = 0xFF7C3AED, MAGENTA = 0xFFD946EF, RED = 0xFFFF405D;
    private static final int FIELD_ACCENT = 0xFFC43D9D;
    private final Screen parent;
    private final int requestedProfileIndex;
    private final WaypointSettingsSnapshot originalSettings;
    private TextFieldWidget scale, markerColor, backgroundColor;
    private int left, top;
    private int profileIndex;
    private String profileName = "Default";
    private boolean virtualProfile;
    private boolean saved;

    WaypointSettingsScreen(Screen parent) {
        this(parent, -1, WaypointSettingsSnapshot.capture(WaypointsPlusClient.config().settings()));
    }

    private WaypointSettingsScreen(Screen parent, int profileIndex, WaypointSettingsSnapshot originalSettings) {
        super(Text.literal(UiText.get("Waypoint Settings", "Ustawienia waypointów")));
        this.parent = parent;
        this.requestedProfileIndex = profileIndex;
        this.originalSettings = originalSettings;
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
                b -> client.setScreen(new ProfileNameScreen(this, parent, profileName)))
                .dimensions(left + 10, top + 52, 136, 20).build());
        ButtonWidget remove = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Remove", "Usuń")),
                b -> confirmRemoveProfile(serverKey))
                .dimensions(left + 154, top + 52, 136, 20).build());
        edit.active = remove.active = !"Default".equals(profileName);
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Language: English", "Język: polski")), b -> {
            settings.language = "pl".equals(settings.language) ? "en" : "pl";
            client.setScreen(new WaypointSettingsScreen(parent, profileIndex, originalSettings));
        }).dimensions(left + 10, top + 84, 280, 20).build());
        addToggle(left + 10, top + 108, 136, UiText.get("Background", "Tło"), settings.background, v -> settings.background = v);
        addToggle(left + 154, top + 108, 136, UiText.get("Coordinates", "Koordynaty"), settings.showCoordinates, v -> settings.showCoordinates = v);
        addToggle(left + 10, top + 132, 136, UiText.get("Distance", "Odległość"), settings.showDistance, v -> settings.showDistance = v);
        addToggle(left + 154, top + 132, 136, UiText.get("Laser", "Laser"), settings.laserEnabled, v -> settings.laserEnabled = v);

        scale = field(left + 10, top + 168, 88, String.valueOf(settings.scale), "Scale");
        markerColor = field(left + 106, top + 168, 88, String.format("%08X", settings.markerArgb), "Marker");
        backgroundColor = field(left + 202, top + 168, 88, String.format("%08X", settings.backgroundArgb), "Background");
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("About", "O modzie")),
                b -> client.setScreen(new AboutScreen(this)))
                .dimensions(left + 10, top + 192, 280, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Save", "Zapisz")), b -> save())
                .dimensions(left + 10, top + 216, 136, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                .dimensions(left + 154, top + 216, 136, 20).build());
    }

    private void openProfile(int index, int size, String serverKey) {
        if (index < size) WaypointsPlusClient.config().selectProfile(serverKey, index);
        client.setScreen(new WaypointSettingsScreen(parent, index, originalSettings));
    }

    private void confirmRemoveProfile(String serverKey) {
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) WaypointsPlusClient.config().removeProfile(serverKey, profileName);
            client.setScreen(this);
        }, Text.literal(UiText.get("Remove Profile?", "Usunąć profil?")),
                Text.literal(UiText.get("Its waypoints will also be deleted.", "Jego waypointy również zostaną usunięte."))));
    }

    private TextFieldWidget field(int x, int y, int width, String value, String hint) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + 5, y + 6, width - 10, 10, Text.literal(hint));
        field.setDrawsBackground(false);
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void addToggle(int x, int y, int width, String name, boolean value, Setter setter) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + (value ? "ON" : "OFF")), b -> {
            setter.set(!value);
            client.setScreen(new WaypointSettingsScreen(parent, profileIndex, originalSettings));
        }).dimensions(x, y, width, 20).build());
    }

    private void save() {
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        try { settings.scale = Math.max(0.25f, Math.min(4.0f, Float.parseFloat(scale.getText().replace(',', '.')))); }
        catch (NumberFormatException ignored) { }
        try { settings.markerArgb = (int)Long.parseLong(markerColor.getText().replace("#", ""), 16); }
        catch (NumberFormatException ignored) { }
        try { settings.backgroundArgb = (int)Long.parseLong(backgroundColor.getText().replace("#", ""), 16); }
        catch (NumberFormatException ignored) { }
        saved = true;
        WaypointsPlusClient.config().saveSettings();
        close();
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
        int bottom = virtualProfile ? top + 150 : top + 250;
        roundedFill(context, left, top, left + 300, bottom, 0xE0141824);
        gradientOutline(context, left, top, left + 300, bottom);
        if (!virtualProfile) {
            fieldBox(context, left + 10, top + 168, 88, 20);
            fieldBox(context, left + 106, top + 168, 88, 20);
            fieldBox(context, left + 202, top + 168, 88, 20);
        }
        super.render(matrices, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, MAGENTA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(profileName), width / 2, top + 33, 0xFFFFFFFF);
        if (virtualProfile) return;
        context.drawTextWithShadow(textRenderer, UiText.get("Scale", "Skala"), left + 10, top + 156, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Default Marker", "Domyślny znacznik"), left + 106, top + 156, 0xFFD9E2F0);
        context.drawTextWithShadow(textRenderer, UiText.get("Background ARGB", "Tło ARGB"), left + 202, top + 156, 0xFFD9E2F0);
    }

    private static void fieldBox(DrawContext context, int x, int y, int width, int height) {
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

    @Override public void renderBackground(MatrixStack matrices) { }

    @Override public void close() {
        if (!saved) originalSettings.restore(WaypointsPlusClient.config().settings());
        client.setScreen(parent);
    }
    @FunctionalInterface private interface Setter { void set(boolean value); }
}
