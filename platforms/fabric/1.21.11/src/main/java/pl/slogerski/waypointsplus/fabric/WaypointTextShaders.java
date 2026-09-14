package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class WaypointTextShaders {
    private static final RenderPipeline TEXT = createPipeline(false);
    private static final RenderPipeline INTENSITY_TEXT = createPipeline(true);
    private static volatile boolean serverShaders;
    private static volatile int resourceGeneration;

    private final Map<RenderLayer, RenderLayer> layers = new IdentityHashMap<>();
    private final Map<RenderLayer, List<TextDrawable>> batches = new IdentityHashMap<>();
    private final List<RenderLayer> activeLayers = new ArrayList<>();
    private final TextRenderer.GlyphDrawer collector = new TextRenderer.GlyphDrawer() {
        @Override
        public void drawGlyph(TextDrawable.DrawnGlyphRect glyph) {
            collect(glyph);
        }

        @Override
        public void drawRectangle(TextDrawable glyph) {
            collect(glyph);
        }
    };
    private int cachedGeneration = -1;

    static void reload(ResourceManager manager) {
        try (var packs = manager.streamResourcePacks()) {
            serverShaders = packs.filter(pack -> pack.getInfo().source() == ResourcePackSource.SERVER)
                    .anyMatch(WaypointTextShaders::hasShaders);
        }
        resourceGeneration++;
    }

    void beginFrame() {
        int generation = resourceGeneration;
        if (cachedGeneration != generation) {
            layers.clear();
            batches.clear();
            cachedGeneration = generation;
        }
    }

    void submit(TextRenderer renderer, MatrixStack matrices, RenderCommandQueue queue,
                OrderedText text, float x, float y, int color, int light) {
        if (!serverShaders) {
            queue.submitText(matrices, x, y, text, false,
                    TextRenderer.TextLayerType.SEE_THROUGH, light, color, 0, 0);
            return;
        }
        try {
            renderer.prepare(text, x, y, color, false, false, 0).draw(collector);
            for (RenderLayer layer : activeLayers) {
                TextDrawable[] glyphs = batches.get(layer).toArray(TextDrawable[]::new);
                queue.submitCustom(matrices, layer, (entry, vertices) -> {
                    for (TextDrawable glyph : glyphs) {
                        glyph.render(entry.getPositionMatrix(), vertices, light, false);
                    }
                });
            }
        } finally {
            for (RenderLayer layer : activeLayers) {
                batches.get(layer).clear();
            }
            activeLayers.clear();
        }
    }

    private void collect(TextDrawable glyph) {
        RenderLayer original = glyph.getRenderLayer(TextRenderer.TextLayerType.SEE_THROUGH);
        RenderLayer layer = layers.computeIfAbsent(original, WaypointTextShaders::replaceLayer);
        List<TextDrawable> glyphs = batches.computeIfAbsent(layer, key -> new ArrayList<>());
        if (glyphs.isEmpty()) activeLayers.add(layer);
        glyphs.add(glyph);
    }

    private static RenderLayer replaceLayer(RenderLayer layer) {
        RenderSetup setup = layer.renderSetup;
        RenderPipeline pipeline;
        if (layer.getRenderPipeline() == RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH) {
            pipeline = TEXT;
        } else if (layer.getRenderPipeline() == RenderPipelines.RENDERTYPE_TEXT_INTENSITY_SEETHROUGH) {
            pipeline = INTENSITY_TEXT;
        } else {
            return layer;
        }
        RenderSetup.Builder builder = RenderSetup.builder(pipeline)
                .expectedBufferSize(setup.expectedBufferSize)
                .layeringTransform(setup.layeringTransform)
                .outputTarget(setup.outputTarget)
                .textureTransform(setup.textureTransform)
                .outlineMode(setup.outlineMode);
        setup.textures.forEach((name, texture) -> builder.texture(name, texture.location(), texture.sampler()));
        if (setup.useLightmap) builder.useLightmap();
        if (setup.useOverlay) builder.useOverlay();
        if (setup.hasCrumbling) builder.crumbling();
        if (setup.translucent) builder.translucent();
        return RenderLayer.of("waypointsplus_text", builder.build());
    }

    private static RenderPipeline createPipeline(boolean intensity) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
                .withLocation(Identifier.of("waypointsplus", intensity
                        ? "pipeline/waypoint_text_intensity" : "pipeline/waypoint_text"))
                .withVertexShader(Identifier.of("waypointsplus", "core/waypoint_text"))
                .withFragmentShader(Identifier.of("waypointsplus", "core/waypoint_text"))
                .withSampler("Sampler0")
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        if (intensity) builder.withShaderDefine("WAYPOINT_INTENSITY");
        return RenderPipelines.register(builder.build());
    }

    private static boolean hasShaders(ResourcePack pack) {
        AtomicBoolean found = new AtomicBoolean();
        for (String namespace : pack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
            pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "shaders", (id, supplier) -> {
                String path = id.getPath();
                if (path.endsWith(".vsh") || path.endsWith(".fsh") || path.endsWith(".glsl")
                        || path.endsWith(".csh") || path.endsWith(".json")) {
                    found.set(true);
                }
            });
            if (found.get()) return true;
        }
        return false;
    }
}
