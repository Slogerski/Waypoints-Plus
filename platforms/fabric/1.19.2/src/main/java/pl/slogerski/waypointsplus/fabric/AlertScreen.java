package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
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

    @Override public final void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(matrices);
        if (pl.slogerski.waypointsplus.core.UiRenderBudget.shouldRenderBlur(this, width, height,
                WaypointsPlusClient.config().settings().menuBackground)) {
            super.renderBackground(matrices);
        }
        matrices.push();
        matrices.scale(renderScale, renderScale, 1);
        try {
            renderContent(context, matrices, (int) (mouseX / renderScale), (int) (mouseY / renderScale), delta);
        } finally {
            matrices.pop();
        }
    }

    protected void renderContent(DrawContext context, MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public final boolean mouseClicked(double x, double y, int button) {
        return clickContent(x / renderScale, y / renderScale, button);
    }

    protected boolean clickContent(double x, double y, int button) {
        return super.mouseClicked(x, y, button);
    }

    @Override public final boolean mouseScrolled(double x, double y, double vertical) {
        return scrollContent(x / renderScale, y / renderScale, 0, vertical);
    }

    protected boolean scrollContent(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, vertical);
    }

    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return dragContent(x / renderScale, y / renderScale, button, dx / renderScale, dy / renderScale);
    }

    protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / renderScale, y / renderScale, button);
    }

    static void drawTitle(DrawContext context, net.minecraft.client.font.TextRenderer renderer,
                          Text title, float x, float y, MatrixStack matrices) {
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(1.2f, 1.2f, 1);
        context.drawCenteredTextWithShadow(renderer, title, 0, 0, 0xFFFFFFFF);
        matrices.pop();
    }

    @Override public void mouseMoved(double x, double y) {
        super.mouseMoved(x / renderScale, y / renderScale);
    }
}
