package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

abstract class AlertScreen extends Screen {
    private float renderScale = 1;

    AlertScreen(Component title) { super(title); }

    protected void configureScale() {
        double gameScale = minecraft.getWindow().getGuiScale();
        double scale = Math.min(4, gameScale);
        scale = Math.min(scale, minecraft.getWindow().getWidth() / 406.0);
        scale = Math.min(scale, minecraft.getWindow().getHeight() / 278.0);
        renderScale = (float) (Math.max(0.1, scale) / gameScale);
        width = (int) (minecraft.getWindow().getGuiScaledWidth() / renderScale);
        height = (int) (minecraft.getWindow().getGuiScaledHeight() / renderScale);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) super.extractBackground(graphics, mouseX, mouseY, delta);
    }

    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(renderScale, renderScale);
        try { extractContent(graphics, (int) (mouseX / renderScale), (int) (mouseY / renderScale), delta); }
        finally { graphics.pose().popMatrix(); }
    }

    protected void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override public final boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return clickContent(event.x() / renderScale, event.y() / renderScale, event.button());
    }
    protected boolean clickContent(double x, double y, int button) { return super.mouseClicked(mouse(x, y, button), false); }
    @Override public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollContent(x / renderScale, y / renderScale, horizontal, vertical);
    }
    protected boolean scrollContent(double x, double y, double horizontal, double vertical) { return super.mouseScrolled(x, y, horizontal, vertical); }
    @Override public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return dragContent(event.x() / renderScale, event.y() / renderScale, event.button(), dx / renderScale, dy / renderScale);
    }
    protected boolean dragContent(double x, double y, int button, double dx, double dy) { return super.mouseDragged(mouse(x, y, button), dx, dy); }
    @Override public final boolean mouseReleased(MouseButtonEvent event) { return releaseContent(event.x() / renderScale, event.y() / renderScale, event.button()); }
    protected boolean releaseContent(double x, double y, int button) { return super.mouseReleased(mouse(x, y, button)); }
    static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    static void drawTitle(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, Component title, float x, float y) {
        graphics.pose().pushMatrix(); graphics.pose().translate(x, y); graphics.pose().scale(1.2f, 1.2f);
        graphics.centeredText(font, title, 0, 0, 0xFFFFFFFF); graphics.pose().popMatrix();
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / renderScale, y / renderScale); }
}
