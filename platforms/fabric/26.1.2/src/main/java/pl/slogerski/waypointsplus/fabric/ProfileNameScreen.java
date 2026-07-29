package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ProfileNameScreen extends Screen {
    private static final int ACCENT = 0xFFC43D9D;
    private final Screen exitScreen;
    private final Screen settingsParent;
    private final String originalName;
    private EditBox name;
    private int left, top;
    private String error = "";

    ProfileNameScreen(Screen exitScreen, Screen settingsParent) {
        this(exitScreen, settingsParent, null);
    }

    ProfileNameScreen(Screen exitScreen, Screen settingsParent, String originalName) {
        super(Component.literal(originalName == null
                ? UiText.get("Add Profile", "Dodaj profil")
                : UiText.get("Edit Profile", "Edytuj profil")));
        this.exitScreen = exitScreen;
        this.settingsParent = settingsParent;
        this.originalName = originalName;
    }

    @Override protected void init() {
        left = width / 2 - 140;
        top = Math.max(20, height / 2 - 70);
        name = new EditBox(font, left + 15, top + 50, 250, 10,
                Component.literal(UiText.get("Profile Name", "Nazwa profilu")));
        name.setBordered(false);
        name.setMaxLength(48);
        if (originalName != null) name.setValue(originalName);
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Save", "Zapisz")), b -> save())
                .pos(left + 10, top + 82).size(126, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Exit", "Wyjdź")), b -> onClose())
                .pos(left + 144, top + 82).size(126, 20).build());
    }

    private void save() {
        try {
            if (originalName == null) {
                WaypointsPlusClient.config().addProfile(ServerScope.current(), name.getValue());
            } else {
                WaypointsPlusClient.config().renameProfile(ServerScope.current(), originalName, name.getValue());
            }
            minecraft.setScreen(exitScreen);
        } catch (IllegalArgumentException ignored) {
            error = UiText.get("Enter a unique profile name.", "Podaj unikalną nazwę profilu.");
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xC0080B12);
        roundedFill(graphics, left, top, left + 280, top + 122, 0xE0141824);
        outline(graphics, left, top, left + 280, top + 122);
        roundedFill(graphics, left + 10, top + 43, left + 270, top + 67, 0xA0000000);
        outline(graphics, left + 10, top + 43, left + 270, top + 67);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 13, ACCENT);
        graphics.text(font, UiText.get("Profile Name", "Nazwa profilu"), left + 10, top + 31, 0xFFD9E2F0, true);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, top + 108, 0xFFFF657A);
    }

    private static void roundedFill(GuiGraphicsExtractor graphics, int l, int t, int r, int b, int color) {
        graphics.fill(l + 2, t, r - 2, b, color);
        graphics.fill(l, t + 2, r, b - 2, color);
    }

    private static void outline(GuiGraphicsExtractor graphics, int l, int t, int r, int b) {
        graphics.fill(l + 2, t, r - 2, t + 1, ACCENT);
        graphics.fill(l + 2, b - 1, r - 2, b, ACCENT);
        graphics.fill(l, t + 2, l + 1, b - 2, ACCENT);
        graphics.fill(r - 1, t + 2, r, b - 2, ACCENT);
        graphics.fill(l + 1, t + 1, l + 2, t + 2, ACCENT);
        graphics.fill(r - 2, t + 1, r - 1, t + 2, ACCENT);
        graphics.fill(l + 1, b - 2, l + 2, b - 1, ACCENT);
        graphics.fill(r - 2, b - 2, r - 1, b - 1, ACCENT);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(exitScreen); }
}
