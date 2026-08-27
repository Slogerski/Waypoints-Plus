package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class ProfileNameScreen extends Screen {
    private final Screen exitScreen;
    private final Screen settingsParent;
    private final String originalName;
    private TextFieldWidget name;
    private int left, top;
    private String error = "";

    ProfileNameScreen(Screen exitScreen, Screen settingsParent) {
        this(exitScreen, settingsParent, null);
    }

    ProfileNameScreen(Screen exitScreen, Screen settingsParent, String originalName) {
        super(Text.literal(originalName == null
                ? UiText.get("Add Profile", "Dodaj profil")
                : UiText.get("Edit Profile", "Edytuj profil")));
        this.exitScreen = exitScreen;
        this.settingsParent = settingsParent;
        this.originalName = originalName;
    }

    @Override protected void init() {
        left = width / 2 - 140;
        top = Math.max(20, height / 2 - 70);
        name = new TextFieldWidget(textRenderer, left + 15, top + 51, 250, 10,
                Text.literal(UiText.get("Profile Name", "Nazwa profilu")));
        name.setDrawsBackground(false);
        name.setMaxLength(48);
        if (originalName != null) name.setText(originalName);
        addDrawableChild(name);
        setInitialFocus(name);
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Save", "Zapisz")), b -> save())
                .dimensions(left + 10, top + 82, 126, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                .dimensions(left + 144, top + 82, 126, 20).build());
    }

    private void save() {
        try {
            if (originalName == null) {
                WaypointsPlusClient.config().addProfile(ServerScope.current(), name.getText());
            } else {
                WaypointsPlusClient.config().renameProfile(ServerScope.current(), originalName, name.getText());
            }
            client.setScreen(exitScreen);
        } catch (IllegalArgumentException ignored) {
            error = UiText.get("Enter a unique profile name.", "Podaj unikalną nazwę profilu.");
        }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        GuiPalette.panel(context, left, top, left + 280, top + 122);
        GuiPalette.input(context, left + 10, top + 43, 260, 24);
        super.render(context, mouseX, mouseY, delta);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0f, top + 12.0f, 0.0f);
        context.getMatrices().scale(1.2f, 1.2f, 1.0f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFFFFFFF);
        context.getMatrices().pop();
        context.drawTextWithShadow(textRenderer, UiText.get("Profile Name", "Nazwa profilu"), left + 10, top + 31, 0xFFD9E2F0);
        if (!error.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2, top + 108, 0xFFFF657A);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void close() { client.setScreen(exitScreen); }
}
