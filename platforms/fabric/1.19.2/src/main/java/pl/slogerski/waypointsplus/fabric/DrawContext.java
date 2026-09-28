package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;

final class DrawContext {
    private final MatrixStack matrices;

    DrawContext(MatrixStack matrices) {
        this.matrices = matrices;
    }

    MatrixStack getMatrices() { return matrices; }

    void drawItem(net.minecraft.item.ItemStack stack, int x, int y) {
        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        try {
            modelView.multiplyPositionMatrix(matrices.peek().getPositionMatrix());
            RenderSystem.applyModelViewMatrix();
            MinecraftClient.getInstance().getItemRenderer().renderInGui(stack, x, y);
        } finally {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    void fill(int left, int top, int right, int bottom, int color) {
        DrawableHelper.fill(matrices, left, top, right, bottom, color);
    }

    void enableScissor(int left, int top, int right, int bottom) {
        var window = MinecraftClient.getInstance().getWindow();
        double scale = window.getScaleFactor();
        org.joml.Matrix4f transform = PresetMatrices.read(matrices.peek().getPositionMatrix());
        double x1 = (left * transform.m00() + top * transform.m10() + transform.m30()) * scale;
        double y1 = (left * transform.m01() + top * transform.m11() + transform.m31()) * scale;
        double x2 = (right * transform.m00() + bottom * transform.m10() + transform.m30()) * scale;
        double y2 = (right * transform.m01() + bottom * transform.m11() + transform.m31()) * scale;
        int x = (int)Math.floor(x1);
        int y = window.getFramebufferHeight() - (int)Math.ceil(y2);
        int width = Math.max(0, (int)Math.ceil(x2) - x);
        int height = Math.max(0, (int)Math.ceil(y2) - (int)Math.floor(y1));
        RenderSystem.enableScissor(x, y, width, height);
    }

    void disableScissor() {
        RenderSystem.disableScissor();
    }

    void drawCenteredTextWithShadow(TextRenderer renderer, Text text, int x, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(matrices, renderer, text.asOrderedText(), x, y, color);
    }

    void drawCenteredTextWithShadow(TextRenderer renderer, String text, int x, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(
                matrices, renderer, Text.literal(text).asOrderedText(), x, y, color);
    }

    void drawTextWithShadow(TextRenderer renderer, Text text, int x, int y, int color) {
        DrawableHelper.drawTextWithShadow(matrices, renderer, text, x, y, color);
    }

    void drawTextWithShadow(TextRenderer renderer, String text, int x, int y, int color) {
        DrawableHelper.drawStringWithShadow(matrices, renderer, text, x, y, color);
    }

    void drawTexture(Identifier texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder vertices = Tessellator.getInstance().getBuffer();
        vertices.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        vertices.vertex(matrix, x, y + height, 0.0f).texture(0.0f, 1.0f).next();
        vertices.vertex(matrix, x + width, y + height, 0.0f).texture(1.0f, 1.0f).next();
        vertices.vertex(matrix, x + width, y, 0.0f).texture(1.0f, 0.0f).next();
        vertices.vertex(matrix, x, y, 0.0f).texture(0.0f, 0.0f).next();
        Tessellator.getInstance().draw();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
