package pl.slogerski.waypointsplus.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import pl.slogerski.waypointsplus.fabric.remote.RemoteAboutAd;
import pl.slogerski.waypointsplus.fabric.remote.RemoteAboutAdSnapshot;
import pl.slogerski.waypointsplus.fabric.remote.RemoteContentService;
import pl.slogerski.waypointsplus.fabric.remote.RemoteContentSession;
import pl.slogerski.waypointsplus.fabric.remote.RemoteLinks;

import java.util.Optional;

final class AboutScreen extends Screen {
    private static final int PURPLE = 0xFF7C3AED, RED = 0xFFFF405D;
    private static final int AD_BORDER = 0x82A78BFA;
    private static final Identifier AVATAR =
            Identifier.of("waypointsplus", "textures/gui/avatar.png");
    private final Screen parent;
    private int left, top;
    private RemoteContentSession<RemoteLinks> linksSession;
    private RemoteContentSession<RemoteAboutAdSnapshot> adSession;
    private RemoteLinks links = RemoteLinks.FALLBACK;
    private RemoteAboutAdSnapshot aboutAd = RemoteAboutAdSnapshot.empty();
    private RemoteImageTexture adTexture;
    private boolean removed;

    AboutScreen(Screen parent) {
        super(Text.literal("Waypoints Plus"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = width / 2 - 150;
        top = Math.max(1, height / 2 - 119);
        openRemoteContent();
        addLinkButtons();
        addDrawableChild(
                ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                        .dimensions(left + 10, top + 208, 280, 20)
                        .build());
    }

    private void openRemoteContent() {
        if (linksSession != null) return;
        RemoteContentService service = RemoteContentService.getDefault();
        linksSession = service.openLinks();
        adSession = service.openAboutAd();
        links = linksSession.snapshot();
        applyAboutAd(adSession.snapshot());
        linksSession.refresh().thenAccept(this::applyLinksLater);
        adSession.refresh().thenAccept(this::applyAboutAdLater);
    }

    private void applyLinksLater(RemoteLinks value) {
        if (value.equals(links)) return;
        client.execute(
                () -> {
                    if (client.currentScreen == this && !removed) {
                        links = value;
                        rebuildButtons();
                    }
                });
    }

    private void applyAboutAdLater(RemoteAboutAdSnapshot value) {
        client.execute(
                () -> {
                    if (client.currentScreen == this && !removed) applyAboutAd(value);
                });
    }

    private void applyAboutAd(RemoteAboutAdSnapshot value) {
        if (value.equals(aboutAd)) return;
        aboutAd = value;
        if (adTexture != null) adTexture.close();
        adTexture = null;
        value.image()
                .ifPresent(
                        image -> {
                            adTexture = new RemoteImageTexture();
                            adTexture.load(image.file());
                        });
    }

    private void rebuildButtons() {
        clearChildren();
        addLinkButtons();
        addDrawableChild(
                ButtonWidget.builder(Text.literal(UiText.get("Exit", "Wyjdź")), b -> close())
                                        .dimensions(left + 10, top + 208, 280, 20)
                        .build());
    }

    private void addLinkButtons() {
        int right = left + 92;
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Modrinth"), b -> open(links.modrinth()))
                        .dimensions(right, top + 78, 63, 20)
                        .build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("CurseForge"), b -> open(links.curseForge()))
                        .dimensions(right + 67, top + 78, 63, 20)
                        .build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Source"), b -> open(links.source()))
                        .dimensions(right + 134, top + 78, 64, 20)
                        .build());
        ButtonWidget discord =
                ButtonWidget.builder(
                                Text.literal(
                                        links.discord().isEmpty()
                                                ? UiText.get(
                                                        "Discord: Unavailable",
                                                        "Discord: niedostępny")
                                                : "Discord"),
                                b -> open(links.discord()))
                        .dimensions(right, top + 106, 63, 20)
                        .build();
        discord.active = !links.discord().isEmpty();
        addDrawableChild(discord);
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Buy Me a Coffee"), b -> open(links.coffee()))
                        .dimensions(right + 67, top + 106, 131, 20)
                        .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        roundedFill(context, left, top, left + 300, top + 238, 0xE0141824);
        gradientOutline(context, left, top, left + 300, top + 238);
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                AVATAR,
                left + 11,
                top + 35,
                0.0f,
                0.0f,
                70,
                94,
                355,
                475,
                355,
                475);
        renderAd(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 13, 0xFFD946EF);
        context.drawTextWithShadow(
                textRenderer,
                UiText.get("Author: Slogerski", "Autor: Slogerski"),
                left + 92,
                top + 40,
                0xFFFFFFFF);
        context.drawTextWithShadow(
                textRenderer,
                UiText.get("Version: ", "Wersja: ") + version(),
                left + 92,
                top + 56,
                0xFFD9E2F0);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderAd(DrawContext context) {
        int x = left + 10, y = top + 134, w = 280, h = 64;
        context.fill(x, y, x + w, y + h, 0xA0090D16);
        context.fill(x, y, x + w, y + 1, AD_BORDER);
        context.fill(x, y + h - 1, x + w, y + h, AD_BORDER);
        context.fill(x, y, x + 1, y + h, AD_BORDER);
        context.fill(x + w - 1, y, x + w, y + h, AD_BORDER);
        if (adTexture != null && adTexture.identifier() != null) {
            int availableW = w - 2;
            int availableH = h - 2;
            int drawW = availableW;
            int drawH = availableH;
            if (adTexture.width() * availableH > adTexture.height() * availableW)
                drawH = Math.max(1, adTexture.height() * availableW / adTexture.width());
            else drawW = Math.max(1, adTexture.width() * availableH / adTexture.height());
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    adTexture.identifier(),
                    x + (w - drawW) / 2,
                    y + (h - drawH) / 2,
                    0,
                    0,
                    drawW,
                    drawH,
                    drawW,
                    drawH,
                    drawW,
                    drawH);
            return;
        }
        Optional<RemoteAboutAd> ad = aboutAd.ad();
        String language = WaypointsPlusClient.config().settings().language;
        context.drawCenteredTextWithShadow(
                textRenderer,
                textRenderer.trimToWidth(
                        ad.map(value -> value.title().forLanguage(language))
                                .orElse("Waypoints Plus"),
                        w - 12),
                x + w / 2,
                y + 18,
                0xFFFFFFFF);
        context.drawCenteredTextWithShadow(
                textRenderer,
                textRenderer.trimToWidth(
                        ad.map(value -> value.text().forLanguage(language)).orElse(""), w - 12),
                x + w / 2,
                y + 37,
                0xFFCBD5E1);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubled) {
        if (event.button() == 0
                && event.x() >= left + 10
                && event.x() < left + 290
                && event.y() >= top + 134
                && event.y() < top + 198) {
            aboutAd.ad()
                    .map(RemoteAboutAd::targetUrl)
                    .filter(url -> !url.isEmpty())
                    .ifPresent(AboutScreen::open);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private static String version() {
        return FabricLoader.getInstance()
                .getModContainer("waypointsplus")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
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
            int color = mix(PURPLE, RED, i / 23.0f);
            c.fill(x1, t, x2, t + 1, color);
            c.fill(x1, b - 1, x2, b, color);
        }
        c.fill(l, t + 2, l + 1, b - 2, PURPLE);
        c.fill(r - 1, t + 2, r, b - 2, RED);
        c.fill(l + 1, t + 1, l + 2, t + 2, PURPLE);
        c.fill(r - 2, t + 1, r - 1, t + 2, RED);
        c.fill(l + 1, b - 2, l + 2, b - 1, PURPLE);
        c.fill(r - 2, b - 2, r - 1, b - 1, RED);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(
                this, width, height, WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        if (!removed) {
            removed = true;
            if (linksSession != null) linksSession.close();
            if (adSession != null) adSession.close();
            if (adTexture != null) adTexture.close();
            linksSession = null;
            adSession = null;
            adTexture = null;
            links = RemoteLinks.FALLBACK;
            aboutAd = RemoteAboutAdSnapshot.empty();
        }
        super.removed();
    }
}
