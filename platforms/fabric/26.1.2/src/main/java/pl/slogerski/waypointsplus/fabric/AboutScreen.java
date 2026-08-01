package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

final class AboutScreen extends Screen {
    private static final int PURPLE = 0xFF7C3AED, RED = 0xFFFF405D;
    private static final Identifier AVATAR = Identifier.fromNamespaceAndPath("waypointsplus", "textures/gui/avatar.png");
    private static final String MODRINTH = "https://modrinth.com/user/Slogerski";
    private static final String CURSEFORGE = "https://www.curseforge.com/members/slogerski/projects";
    private static final String SOURCE = "https://github.com/Slogerski/Waypoints-Plus";
    private static final String COFFEE = "https://buymeacoffee.com/slogerski";
    private final Screen parent;
    private int left, top;

    AboutScreen(Screen parent) {
        super(Component.literal("Waypoints Plus"));
        this.parent = parent;
    }

    @Override protected void init() {
        left = width / 2 - 150;
        top = Math.max(10, height / 2 - 100);
        int right = left + 92;
        addRenderableWidget(Button.builder(Component.literal("Modrinth"), b -> open(MODRINTH))
                .pos(right, top + 78).size(63, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CurseForge"), b -> open(CURSEFORGE))
                .pos(right + 67, top + 78).size(63, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Source"), b -> open(SOURCE))
                .pos(right + 134, top + 78).size(64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Buy Me a Coffee"), b -> open(COFFEE))
                .pos(right, top + 106).size(198, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.get("Exit", "Wyjdź")), b -> onClose())
                .pos(left + 10, top + 158).size(280, 20).build());
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        roundedFill(graphics, left, top, left + 300, top + 188, 0xE0141824);
        gradientOutline(graphics, left, top, left + 300, top + 188);
        graphics.blit(RenderPipelines.GUI_TEXTURED, AVATAR, left + 11, top + 35,
                0.0f, 0.0f, 70, 94, 355, 475, 355, 475);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 13, 0xFFD946EF);
        graphics.text(font, UiText.get("Author: Slogerski", "Autor: Slogerski"), left + 92, top + 40, 0xFFFFFFFF, true);
        graphics.text(font, UiText.get("Version: ", "Wersja: ") + version(), left + 92, top + 56, 0xFFD9E2F0, true);
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer("waypointsplus")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static void open(String url) {
        Util.getPlatform().openUri(url);
    }

    private static void roundedFill(GuiGraphicsExtractor g, int l, int t, int r, int b, int color) {
        g.fill(l + 2, t, r - 2, b, color);
        g.fill(l, t + 2, r, b - 2, color);
    }

    private static void gradientOutline(GuiGraphicsExtractor g, int l, int t, int r, int b) {
        for (int i = 0; i < 24; i++) {
            int x1 = l + 2 + (r - l - 4) * i / 24;
            int x2 = l + 2 + (r - l - 4) * (i + 1) / 24;
            g.fill(x1, t, x2, t + 1, mix(PURPLE, RED, i / 23.0f));
            g.fill(x1, b - 1, x2, b, mix(PURPLE, RED, i / 23.0f));
        }
        g.fill(l, t + 2, l + 1, b - 2, PURPLE);
        g.fill(r - 1, t + 2, r, b - 2, RED);
        g.fill(l + 1, t + 1, l + 2, t + 2, PURPLE);
        g.fill(r - 2, t + 1, r - 1, t + 2, RED);
        g.fill(l + 1, b - 2, l + 2, b - 1, PURPLE);
        g.fill(r - 2, b - 2, r - 1, b - 1, RED);
    }

    private static int mix(int a, int b, float t) {
        int r = (int)(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
