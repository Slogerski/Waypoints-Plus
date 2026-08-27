package pl.slogerski.waypointsplus.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
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
                        clearChildren();
                        addLinkButtons();
                        addDrawableChild(
                                ButtonWidget.builder(
                                                Text.literal(UiText.get("Exit", "Wyjdź")),
                                                b -> close())
                                        .dimensions(left + 10, top + 208, 280, 20)
                                        .build());
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
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(
                this, width, height, WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        GuiPalette.panel(context, left, top, left + 300, top + 238);
        context.drawTexture(
                RenderLayer::getGuiTextured,
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
        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0f, top + 12.0f, 0.0f);
        context.getMatrices().scale(1.2f, 1.2f, 1.0f);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFFFFFFF);
        context.getMatrices().pop();
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
        GuiPalette.inputOutline(context, x, y, x + w, y + h);
        if (adTexture != null && adTexture.identifier() != null) {
            int availableW = w - 2;
            int availableH = h - 2;
            int drawW = availableW;
            int drawH = availableH;
            if (adTexture.width() * availableH > adTexture.height() * availableW)
                drawH = Math.max(1, adTexture.height() * availableW / adTexture.width());
            else drawW = Math.max(1, adTexture.width() * availableH / adTexture.height());
            context.drawTexture(
                    RenderLayer::getGuiTextured,
                    adTexture.identifier(),
                    x + (w - drawW) / 2,
                    y + (h - drawH) / 2,
                    0.0f,
                    0.0f,
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= left + 10
                && mouseX < left + 290
                && mouseY >= top + 134
                && mouseY < top + 198) {
            aboutAd.ad()
                    .map(RemoteAboutAd::targetUrl)
                    .filter(url -> !url.isEmpty())
                    .ifPresent(AboutScreen::open);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

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
