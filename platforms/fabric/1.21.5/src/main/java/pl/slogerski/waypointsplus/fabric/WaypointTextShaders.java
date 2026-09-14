package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class WaypointTextShaders implements VertexConsumerProvider {
    private static final RenderPipeline TEXT = createPipeline(false);
    private static final RenderPipeline INTENSITY_TEXT = createPipeline(true);
    private static volatile boolean serverShaders;
    private static volatile int resourceGeneration;

    private VertexConsumerProvider delegate;
    private final Map<RenderLayer, RenderLayer> layers = new IdentityHashMap<>();
    private int cachedGeneration = -1;

    WaypointTextShaders(VertexConsumerProvider delegate) {
        this.delegate = delegate;
    }

    static void reload(ResourceManager manager) {
        try (var packs = manager.streamResourcePacks()) {
            serverShaders = packs.filter(pack -> pack.getInfo().source() == ResourcePackSource.SERVER)
                    .anyMatch(WaypointTextShaders::hasShaders);
        }
        resourceGeneration++;
    }

    VertexConsumerProvider current(VertexConsumerProvider provider) {
        delegate = provider;
        return current();
    }

    VertexConsumerProvider current() {
        int generation = resourceGeneration;
        if (cachedGeneration != generation) {
            layers.clear();
            cachedGeneration = generation;
        }
        return serverShaders ? this : delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return delegate.getBuffer(layers.computeIfAbsent(layer, WaypointTextShaders::replaceLayer));
    }

    private static RenderLayer replaceLayer(RenderLayer layer) {
        if (!(layer instanceof RenderLayer.MultiPhase phase)) return layer;
        RenderPipeline pipeline;
        if (phase.pipeline == RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH) {
            pipeline = TEXT;
        } else if (phase.pipeline == RenderPipelines.RENDERTYPE_TEXT_INTENSITY_SEETHROUGH) {
            pipeline = INTENSITY_TEXT;
        } else {
            return layer;
        }
        return RenderLayer.of("waypointsplus_text", layer.getExpectedBufferSize(),
                false, layer.isTranslucent(), pipeline, phase.phases);
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
