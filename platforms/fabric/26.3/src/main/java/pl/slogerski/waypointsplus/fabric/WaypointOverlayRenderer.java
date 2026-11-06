package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;

import java.util.List;

final class WaypointOverlayRenderer extends RenderTypeFeatureRenderer<WaypointOverlayRenderer.Submit> {
    private static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("waypointsplus:overlay");

    static void register() {
        FeatureRendererRegistry.register(TYPE, WaypointOverlayRenderer::new);
    }

    static void submit(OrderedSubmitNodeCollector queue, PoseStack.Pose pose, RenderType renderType,
                       SubmitNodeCollector.CustomGeometryRenderer geometry) {
        queue.submitCustom(SubmitRenderPhases.SEE_THROUGH_NAME_TAGS,
                new GeometrySubmit(pose, renderType, geometry));
    }

    static void submitText(OrderedSubmitNodeCollector queue, Matrix4fc pose, FormattedCharSequence text, int color) {
        queue.submitCustom(SubmitRenderPhases.SEE_THROUGH_NAME_TAGS, new TextSubmit(pose, text, color));
    }

    @Override
    protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
        GlyphRenderer glyphs = null;
        for (Submit submit : submits) {
            switch (submit) {
                case GeometrySubmit geometry -> geometry.geometry().render(geometry.pose(), getVertexBuilder(geometry.renderType()));
                case TextSubmit text -> {
                    if (glyphs == null) glyphs = new GlyphRenderer();
                    glyphs.pose = text.pose();
                    context.font().prepareText(text.text(), 0, 0, text.color(), false, false, 0).visit(glyphs);
                }
            }
        }
    }

    sealed interface Submit extends TranslucentSubmit {
        @Override default float distanceToCameraSq() { return 0; }
        @Override default FeatureRendererType<Submit> featureType() { return TYPE; }
    }

    record GeometrySubmit(PoseStack.Pose pose, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer geometry)
            implements Submit { }

    record TextSubmit(Matrix4fc pose, FormattedCharSequence text, int color) implements Submit { }

    private final class GlyphRenderer implements Font.GlyphVisitor {
        private Matrix4fc pose;

        @Override
        public void acceptRenderable(TextRenderable renderable) {
            renderable.render(pose, getVertexBuilder(renderable.renderType(Font.DisplayMode.SEE_THROUGH)), 0xF000F0, false);
        }
    }
}
