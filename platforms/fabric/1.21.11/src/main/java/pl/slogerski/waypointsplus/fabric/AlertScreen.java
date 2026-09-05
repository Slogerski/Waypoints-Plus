package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class AlertScreen extends Screen {
    private float renderScale = 1;

    AlertScreen(Text title) {
        super(title);
    }

    protected void configureScale() {
        double gameScale = client.getWindow().getScaleFactor();
        double scale = Math.min(4, gameScale);
        scale = Math.min(scale, client.getWindow().getFramebufferWidth() / 406.0);
        scale = Math.min(scale, client.getWindow().getFramebufferHeight() / 278.0);
        renderScale = (float) (Math.max(0.1, scale) / gameScale);
        width = (int) (client.getWindow().getScaledWidth() / renderScale);
        height = (int) (client.getWindow().getScaledHeight() / renderScale);
    }

    @Override public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(renderScale, renderScale);
        try {
            renderContent(context, (int) (mouseX / renderScale), (int) (mouseY / renderScale), delta);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public final boolean mouseClicked(Click event, boolean doubled) {
        return clickContent(event.x() / renderScale, event.y() / renderScale, event.button());
    }

    protected boolean clickContent(double x, double y, int button) {
        return super.mouseClicked(click(x, y, button), false);
    }

    @Override public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollContent(x / renderScale, y / renderScale, horizontal, vertical);
    }

    protected boolean scrollContent(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public final boolean mouseDragged(Click event, double dx, double dy) {
        return dragContent(event.x() / renderScale, event.y() / renderScale, event.button(),
                dx / renderScale, dy / renderScale);
    }

    protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(click(x, y, button), dx, dy);
    }

    @Override public final boolean mouseReleased(Click event) {
        return releaseContent(event.x() / renderScale, event.y() / renderScale, event.button());
    }

    protected boolean releaseContent(double x, double y, int button) {
        return super.mouseReleased(click(x, y, button));
    }

    static Click click(double x, double y, int button) {
        return new Click(x, y, new MouseInput(button, 0));
    }

    static void drawTitle(DrawContext context, net.minecraft.client.font.TextRenderer renderer,
                          Text title, float x, float y) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(1.2f, 1.2f);
        context.drawCenteredTextWithShadow(renderer, title, 0, 0, 0xFFFFFFFF);
        context.getMatrices().popMatrix();
    }

    @Override public void mouseMoved(double x, double y) {
        super.mouseMoved(x / renderScale, y / renderScale);
    }
}
