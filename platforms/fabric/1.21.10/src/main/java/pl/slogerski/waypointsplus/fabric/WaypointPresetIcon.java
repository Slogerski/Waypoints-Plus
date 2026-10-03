package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

final class WaypointPresetIcon implements AutoCloseable {
    private static final int MAX_VERTICES = 8192;
    private static final BitSet IMAGE_SLOTS = new BitSet(64);
    private final List<Face> faces = new ArrayList<>();
    private Identifier image;
    private int imageSlot = -1;

    WaypointPresetIcon(WaypointPreset preset) {
        if (!preset.icon) return;
        try {
            if (preset.pngIcon) loadImage(preset);
            else loadItem(preset);
        } catch (IOException | RuntimeException exception) {
            close();
            org.slf4j.LoggerFactory.getLogger("waypointsplus").warn("Cannot prepare preset icon {}", preset.id, exception);
        }
    }

    private void loadImage(WaypointPreset preset) throws IOException {
        if (!WaypointPreset.validPng(preset.png)) return;
        imageSlot = IMAGE_SLOTS.nextClearBit(0);
        if (imageSlot >= 64) { imageSlot = -1; return; }
        IMAGE_SLOTS.set(imageSlot);
        NativeImage decoded = NativeImage.read(Base64.getDecoder().decode(preset.png));
        NativeImageBackedTexture texture;
        try {
            texture = new NativeImageBackedTexture(() -> "waypointsplus/preset-icon", decoded);
        } catch (RuntimeException exception) {
            decoded.close();
            throw exception;
        }
        image = Identifier.of("waypointsplus", "dynamic/preset/" + imageSlot);
        try {
            MinecraftClient.getInstance().getTextureManager().registerTexture(image, texture);
        } catch (RuntimeException exception) {
            texture.close();
            image = null;
            throw exception;
        }
        RenderLayer layer = RenderLayer.getTextSeeThrough(image);
        faces.add(new Face(layer, new Point[] {
                new Point(0, 0, 0, 0, 0, -1), new Point(0, 16, 0, 0, 1, -1),
                new Point(16, 16, 0, 1, 1, -1), new Point(16, 0, 0, 1, 0, -1)
        }));
    }

    private void loadItem(WaypointPreset preset) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack stack = new ItemStack(Registries.ITEM.get(Identifier.of(WaypointPreset.displayItem(preset.item))));
        ItemRenderState state = new ItemRenderState();
        client.getItemModelManager().clearAndUpdate(state, stack, ItemDisplayContext.GUI, null, null, 0);
        MatrixStack matrices = new MatrixStack();
        matrices.translate(8, 8, 0);
        matrices.scale(16, -16, 16);
        int[] count = {0};
        for (int i = 0; i < state.layerCount; i++) {
            var layer = state.layers[i];
            matrices.push();
            layer.transform.apply(false, matrices.peek());
            if (layer.specialModelType != null) {
                layer.specialModelType.render(layer.data, ItemDisplayContext.GUI, matrices, new ModelCapture(count),
                        0xF000F0, OverlayTexture.DEFAULT_UV, false, 0);
            }
            for (var quad : layer.getQuads()) {
                Capture capture = new Capture(RenderLayer.getTextSeeThrough(quad.sprite().getAtlasId()), count);
                int color = quad.hasTint() && quad.tintIndex() < layer.tints.length ? layer.tints[quad.tintIndex()] : -1;
                capture.quad(matrices.peek(), quad, ((color >> 16) & 255) / 255f,
                        ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1, 0xF000F0, OverlayTexture.DEFAULT_UV);
                capture.finish();
            }
            matrices.pop();
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
    }

    void draw(Matrix4f matrix, net.minecraft.client.render.command.RenderCommandQueue queue, float x, float y, float scale) {
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().set(matrix);
        for (Face face : faces) {
            queue.submitCustom(matrices, face.layer, (entry, vertices) -> {
                for (Point point : face.points) {
                    vertices.vertex(entry.getPositionMatrix(), x + point.x * scale, y + point.y * scale, 0)
                            .color(point.color).texture(point.u, point.v).light(0xF000F0);
                }
            });
        }
    }

    @Override public void close() {
        faces.clear();
        if (image != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(image);
            image = null;
        }
        if (imageSlot >= 0) { IMAGE_SLOTS.clear(imageSlot); imageSlot = -1; }
    }

    private record Point(float x, float y, float z, float u, float v, int color) { }
    private record Face(RenderLayer layer, Point[] points) {
        double depth() { return (points[0].z + points[1].z + points[2].z + points[3].z) / 4; }
    }


    private final class ModelCapture extends net.minecraft.client.render.command.OrderedRenderCommandQueueImpl {
        private final int[] total;
        ModelCapture(int[] total) { this.total = total; }
        private Capture capture(RenderLayer layer) {
            Identifier texture = layer instanceof RenderLayer.MultiPhase phase ? phase.phases.texture.getId().orElse(null) : null;
            if (texture == null) throw new IllegalArgumentException("Preset model has no texture");
            return new Capture(RenderLayer.getTextSeeThrough(texture), total);
        }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state,
                MatrixStack matrices, RenderLayer layer, int light, int overlay, int color,
                net.minecraft.client.texture.Sprite sprite, int outline,
                net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
            Capture capture = capture(layer);
            model.setAngles(state);
            model.render(matrices, sprite == null ? capture : sprite.getTextureSpecificVertexConsumer(capture), light, overlay, color);
            capture.finish();
        }
        @Override public void submitModelPart(net.minecraft.client.model.ModelPart part, MatrixStack matrices,
                RenderLayer layer, int light, int overlay, net.minecraft.client.texture.Sprite sprite,
                boolean sheeted, boolean glint, int color,
                net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand crumbling, int outline) {
            Capture capture = capture(layer);
            part.render(matrices, sprite == null ? capture : sprite.getTextureSpecificVertexConsumer(capture), light, overlay, color);
            capture.finish();
        }
    }

    private final class Capture implements VertexConsumer {
        private final RenderLayer layer;
        private final int[] total;
        private final List<Point> pending = new ArrayList<>(4);
        private boolean started;
        private float x, y, z, u, v;
        private int color = -1;

        Capture(RenderLayer layer, int[] total) { this.layer = layer; this.total = total; }

        private void finish() {
            if (!started) return;
            started = false;
            if (layer == null) return;
            pending.add(new Point(x, y, z, u, v, color));
            if (pending.size() == 4) {
                faces.add(new Face(layer, pending.toArray(Point[]::new)));
                pending.clear();
            }
        }

        @Override public VertexConsumer vertex(float x, float y, float z) {
            finish();
            if (++total[0] > MAX_VERTICES) throw new IllegalArgumentException("Preset item model is too complex");
            this.x = x; this.y = y; this.z = z; color = -1; u = 0; v = 0; started = true;
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            color = a << 24 | r << 16 | g << 8 | b; return this;
        }
        @Override public VertexConsumer texture(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
