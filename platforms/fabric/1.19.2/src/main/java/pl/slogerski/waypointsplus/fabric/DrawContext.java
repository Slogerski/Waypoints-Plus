package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
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

/** Compatibility facade for the DrawContext API added after Minecraft 1.19.2. */
final class DrawContext {
    private final MatrixStack matrices;

    DrawContext(MatrixStack matrices) {
        this.matrices = matrices;
    }

    void fill(int left, int top, int right, int bottom, int color) {
        DrawableHelper.fill(matrices, left, top, right, bottom, color);
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
