package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

final class AboutScreen extends Screen {
    private static final int PURPLE = 0xFF7C3AED, RED = 0xFFFF405D;
    private static final Identifier AVATAR = Identifier.of("waypointsplus", "textures/gui/avatar.png");
    private static final String MODRINTH = "https://modrinth.com/user/Slogerski";
    private static final String CURSEFORGE = "https://www.curseforge.com/members/slogerski/projects";
    private static final String SOURCE = "https://github.com/Slogerski/Waypoints-Plus";
    private static final String COFFEE = "https://buymeacoffee.com/slogerski";
    private final Screen parent;
    private int left, top;

    AboutScreen(Screen parent) {
        super(Text.literal("Waypoints Plus"));
        this.parent = parent;
    }

    @Override protected void init() {
        left = width / 2 - 150;
        top = Math.max(10, height / 2 - 100);
        int right = left + 92;
        addDrawableChild(ButtonWidget.builder(Text.literal("Modrinth"), b -> open(MODRINTH))
                .dimensions(right, top + 78, 63, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CurseForge"), b -> open(CURSEFORGE))
                .dimensions(right + 67, top + 78, 63, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Source"), b -> open(SOURCE))
                .dimensions(right + 134, top + 78, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Buy Me a Coffee"), b -> open(COFFEE))
                .dimensions(right, top + 106, 198, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                .dimensions(left + 10, top + 158, 280, 20).build());
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
        roundedFill(context, left, top, left + 300, top + 188, 0xE0141824);
        gradientOutline(context, left, top, left + 300, top + 188);
        super.render(matrices, mouseX, mouseY, delta);
        context.drawTexture(AVATAR, left + 11, top + 35, 0.0f, 0.0f, 70, 94, 355, 475);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 13, 0xFFD946EF);
        context.drawTextWithShadow(textRenderer, UiText.get("Author: Slogerski", "Autor: Slogerski"),
                left + 92, top + 40, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, UiText.get("Version: ", "Wersja: ") + version(),
                left + 92, top + 56, 0xFFD9E2F0);
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer("waypointsplus")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static void open(String url) {
        Util.getOperatingSystem().open(url);
    }

    private static void roundedFill(DrawContext c, int l, int t, int r, int b, int color) {
        c.fill(l + 2, t, r - 2, b, color);
        c.fill(l, t + 2, r, b - 2, color);
    }

    private static void gradientOutline(DrawContext c, int l, int t, int r, int b) {
        for (int i = 0; i < 24; i++) {
            int x1 = l + 2 + (r - l - 4) * i / 24;
            int x2 = l + 2 + (r - l - 4) * (i + 1) / 24;
            c.fill(x1, t, x2, t + 1, mix(PURPLE, RED, i / 23.0f));
            c.fill(x1, b - 1, x2, b, mix(PURPLE, RED, i / 23.0f));
        }
        c.fill(l, t + 2, l + 1, b - 2, PURPLE);
        c.fill(r - 1, t + 2, r, b - 2, RED);
        c.fill(l + 1, t + 1, l + 2, t + 2, PURPLE);
        c.fill(r - 2, t + 1, r - 1, t + 2, RED);
        c.fill(l + 1, b - 2, l + 2, b - 1, PURPLE);
        c.fill(r - 2, b - 2, r - 1, b - 1, RED);
    }

    private static int mix(int a, int b, float t) {
        int r = (int)(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    @Override public void renderBackground(MatrixStack matrices) { }

    @Override public void close() { client.setScreen(parent); }
}
