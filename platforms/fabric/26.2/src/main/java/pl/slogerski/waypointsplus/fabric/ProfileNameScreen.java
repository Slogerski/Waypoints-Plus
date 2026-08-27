package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ProfileNameScreen extends Screen {
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
        name = new EditBox(font, left + 15, top + 51, 250, 10,
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
            minecraft.gui.setScreen(exitScreen);
        } catch (IllegalArgumentException ignored) {
            error = UiText.get("Enter a unique profile name.", "Podaj unikalną nazwę profilu.");
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(graphics, left, top, left + 280, top + 122);
        GuiPalette.input(graphics, left + 10, top + 43, 260, 24);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0f, top + 12.0f);
        graphics.pose().scale(1.2f, 1.2f);
        graphics.centeredText(font, title, 0, 0, 0xFFFFFFFF);
        graphics.pose().popMatrix();
        graphics.text(font, UiText.get("Profile Name", "Nazwa profilu"), left + 10, top + 31, 0xFFD9E2F0, true);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, top + 108, 0xFFFF657A);
    }

    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(exitScreen); }
}
