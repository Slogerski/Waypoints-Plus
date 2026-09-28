package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

final class WaypointPresetIcon implements AutoCloseable {
    private static final BitSet SLOTS = new BitSet(64);
    private final List<Face> faces = new ArrayList<>();
    private Identifier image;
    private int slot = -1;

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
        slot = SLOTS.nextClearBit(0);
        if (slot >= 64) { slot = -1; return; }
        SLOTS.set(slot);
        NativeImage decoded = NativeImage.read(Base64.getDecoder().decode(preset.png));
        DynamicTexture texture;
        try { texture = new DynamicTexture(() -> "waypointsplus/preset-icon", decoded); }
        catch (RuntimeException exception) { decoded.close(); throw exception; }
        image = Identifier.fromNamespaceAndPath("waypointsplus", "dynamic/preset/" + slot);
        try { Minecraft.getInstance().getTextureManager().register(image, texture); }
        catch (RuntimeException exception) { texture.close(); image = null; throw exception; }
        faces.add(new Face(RenderTypes.textSeeThrough(image), new Point[] {
            new Point(0, 0, 0, 0, 0, -1), new Point(0, 16, 0, 0, 1, -1),
            new Point(16, 16, 0, 1, 1, -1), new Point(16, 0, 0, 1, 0, -1)
        }));
    }
    private void loadItem(WaypointPreset preset) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(preset.item)));
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, null, null, 0);
        PoseStack pose = new PoseStack();
        pose.translate(8, 8, 0); pose.scale(16, -16, 16);
        Vector3f position = new Vector3f();
        int[] total = {0};
        for (int i = 0; i < state.activeLayerCount; i++) {
            var layer = state.layers[i];
            pose.pushPose();
            layer.applyTransform(pose.last());
            if (layer.specialRenderer != null) {
                layer.specialRenderer.submit(layer.argumentForSpecialRendering, pose, new ModelCapture(total),
                        0xF000F0, 0, false, 0);
            }
            var tints = layer.tintLayers();
            for (var quad : layer.prepareQuadList()) {
                if ((total[0] += 4) > 8192) throw new IllegalArgumentException("Preset item model is too complex");
                var material = quad.materialInfo();
                int tint = material.tintIndex();
                int color = material.isTinted() && tints != null && tint >= 0 && tint < tints.size() ? tints.getInt(tint) : -1;
                Point[] points = new Point[4];
                for (int v = 0; v < 4; v++) {
                    pose.last().pose().transformPosition(quad.position(v), position);
                    long uv = quad.packedUV(v);
                    points[v] = new Point(position.x, position.y, position.z,
                            UVPair.unpackU(uv), UVPair.unpackV(uv), color);
                }
                faces.add(new Face(RenderTypes.textSeeThrough(material.sprite().atlasLocation()), points));
            }
            pose.popPose();
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
    }
    void draw(Matrix4f matrix, net.minecraft.client.renderer.OrderedSubmitNodeCollector queue, float x, float y, float scale) {
        PoseStack pose = new PoseStack(); pose.last().pose().set(matrix);
        for (Face face : faces) {
            if (!WaypointHudRenderer.phased()) {
                queue.submitCustomGeometry(pose, face.layer, (entry, vertices) -> {
                    for (Point point : face.points) vertices.addVertex(entry.pose(), x + point.x * scale, y + point.y * scale, 0)
                            .setColor(point.color).setUv(point.u, point.v).setLight(0xF000F0);
                });
                continue;
            }
            queue.submitCustom(net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases.ALWAYS_ON_TOP,
                    new net.minecraft.client.renderer.feature.CustomFeatureRenderer.Submit(pose.last().copy(), face.layer, (entry, vertices) -> {
                for (Point point : face.points) vertices.addVertex(entry.pose(), x + point.x * scale, y + point.y * scale, 0)
                        .setColor(point.color).setUv(point.u, point.v).setLight(0xF000F0);
            }));
        }
    }
    @Override public void close() {
        faces.clear();
        if (image != null) { Minecraft.getInstance().getTextureManager().release(image); image = null; }
        if (slot >= 0) { SLOTS.clear(slot); slot = -1; }
    }

    private final class ModelCapture extends net.minecraft.client.renderer.SubmitNodeStorage {
        private final int[] total;
        ModelCapture(int[] total) { this.total = total; }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state,
                PoseStack pose, RenderType layer, int light, int overlay, int color,
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int outline,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            Capture capture = capture(layer, total);
            model.setupAnim(state);
            model.renderToBuffer(pose, sprite == null ? capture : sprite.wrap(capture), light, overlay, color);
            capture.finish();
        }
    }

    private Capture capture(RenderType layer, int[] total) {
        var binding = layer.state.textures.get("Sampler0");
        if (binding == null) throw new IllegalArgumentException("Preset model has no texture");
        return new Capture(RenderTypes.textSeeThrough(binding.location()), total);
    }

    private final class Capture implements com.mojang.blaze3d.vertex.VertexConsumer {
        private final RenderType layer;
        private final int[] total;
        private final List<Point> pending = new ArrayList<>(4);
        private boolean started;
        private float x, y, z, u, v;
        private int color = -1;
        Capture(RenderType layer, int[] total) { this.layer = layer; this.total = total; }
        void finish() {
            if (!started) return;
            started = false;
            pending.add(new Point(x, y, z, u, v, color));
            if (pending.size() == 4) { faces.add(new Face(layer, pending.toArray(Point[]::new))); pending.clear(); }
        }
        @Override public Capture addVertex(float x, float y, float z) {
            finish();
            if (++total[0] > 8192) throw new IllegalArgumentException("Preset item model is too complex");
            this.x = x; this.y = y; this.z = z; u = 0; v = 0; color = -1; started = true;
            return this;
        }
        @Override public Capture setColor(int r, int g, int b, int a) { color = a << 24 | r << 16 | g << 8 | b; return this; }
        public Capture setColor(int argb) { color = argb; return this; }
        @Override public Capture setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public Capture setUv1(int u, int v) { return this; }
        @Override public Capture setUv2(int u, int v) { return this; }
        @Override public Capture setNormal(float x, float y, float z) { return this; }
        public Capture setUv3(float u, float v) { return this; }
        public Capture setLineWidth(float width) { return this; }
    }

    private record Point(float x, float y, float z, float u, float v, int color) { }
    private record Face(RenderType layer, Point[] points) {
        double depth() { return (points[0].z + points[1].z + points[2].z + points[3].z) / 4; }
    }
}
