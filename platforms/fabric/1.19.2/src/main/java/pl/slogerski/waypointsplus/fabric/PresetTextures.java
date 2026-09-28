package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Identifier;

final class PresetTextures {
    static void draw(DrawContext context, Identifier id, int x, int y, float u, float v,
                     int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        draw(context, id, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, -1);
    }
    static void draw(DrawContext context, Identifier id, int x, int y, float u, float v,
                     int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        RenderSystem.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale((float) width / regionWidth, (float) height / regionHeight, 1);
        context.drawTexture(id, 0, 0, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        context.getMatrices().pop();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
