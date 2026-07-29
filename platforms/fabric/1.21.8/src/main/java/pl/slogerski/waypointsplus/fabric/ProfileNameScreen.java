package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class ProfileNameScreen extends Screen {
    private static final int ACCENT = 0xFFC43D9D;
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
        name = new TextFieldWidget(textRenderer, left + 15, top + 50, 250, 10,
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
        context.fill(0, 0, width, height, 0xC0080B12);
        roundedFill(context, left, top, left + 280, top + 122, 0xE0141824);
        outline(context, left, top, left + 280, top + 122);
        roundedFill(context, left + 10, top + 43, left + 270, top + 67, 0xA0000000);
        outline(context, left + 10, top + 43, left + 270, top + 67);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 13, ACCENT);
        context.drawTextWithShadow(textRenderer, UiText.get("Profile Name", "Nazwa profilu"), left + 10, top + 31, 0xFFD9E2F0);
        if (!error.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2, top + 108, 0xFFFF657A);
    }

    private static void roundedFill(DrawContext context, int l, int t, int r, int b, int color) {
        context.fill(l + 2, t, r - 2, b, color);
        context.fill(l, t + 2, r, b - 2, color);
    }

    private static void outline(DrawContext context, int l, int t, int r, int b) {
        context.fill(l + 2, t, r - 2, t + 1, ACCENT);
        context.fill(l + 2, b - 1, r - 2, b, ACCENT);
        context.fill(l, t + 2, l + 1, b - 2, ACCENT);
        context.fill(r - 1, t + 2, r, b - 2, ACCENT);
        context.fill(l + 1, t + 1, l + 2, t + 2, ACCENT);
        context.fill(r - 2, t + 1, r - 1, t + 2, ACCENT);
        context.fill(l + 1, b - 2, l + 2, b - 1, ACCENT);
        context.fill(r - 2, b - 2, r - 1, b - 1, ACCENT);
    }

    @Override public void close() { client.setScreen(exitScreen); }
}
