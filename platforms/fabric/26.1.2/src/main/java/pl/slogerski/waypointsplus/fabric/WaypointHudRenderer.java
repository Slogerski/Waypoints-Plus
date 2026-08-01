package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointAppearance;

final class WaypointHudRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double MAX_BILLBOARD_DISTANCE = 24.0;

    private WaypointHudRenderer() { }

    static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WaypointHudRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        WaypointConfigStore store = WaypointsPlusClient.config();
        store.reloadWaypointsIfChanged();
        WaypointSettings settings = store.settings();
        if (!settings.enabled) return;

        String dimension = minecraft.level.dimension().identifier().toString();
        String serverKey = ServerScope.current();
        String profile = store.activeProfile(serverKey);
        store.claimLegacy(serverKey);
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        PoseStack pose = context.poseStack();
        MultiBufferSource.BufferSource buffers = context.bufferSource();

        for (Waypoint waypoint : store.waypoints()) {
            if (!serverKey.equals(waypoint.serverKey()) || !profile.equals(waypoint.profile())) continue;
            DisplayTarget target = convert(waypoint, dimension);
            if (target == null) continue;
            if (settings.laserEnabled) {
                drawLaser(pose, buffers, cameraPos, target,
                        parseArgb(waypoint.colorArgb(), settings.markerArgb));
            }
            renderLabel(minecraft, pose, buffers, camera, cameraPos, waypoint, target, settings);
        }
    }

    private static void renderLabel(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                    Camera camera, Vec3 cameraPos, Waypoint waypoint,
                                    DisplayTarget target, WaypointSettings settings) {
        double dx = target.x - cameraPos.x, dy = target.y + 1.5 - cameraPos.y, dz = target.z - cameraPos.z;
        double actualDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double visibleDistance = Math.min(actualDistance, MAX_BILLBOARD_DISTANCE);
        if (actualDistance > MAX_BILLBOARD_DISTANCE) {
            double factor = MAX_BILLBOARD_DISTANCE / actualDistance;
            dx *= factor;
            dy *= factor;
            dz *= factor;
        }
        float distanceFactor = (float)Math.max(1.0, visibleDistance / 10.0);
        float scale = 0.025f * settings.scale * distanceFactor;
        String label = waypoint.name();
        if (settings.showDistance) label += "  •  " + formatDistance(actualDistance);
        if (settings.showCoordinates) label += "  " + Math.round(target.x) + " " + Math.round(target.y) + " " + Math.round(target.z);
        Component text = Component.literal(label);
        int textWidth = minecraft.font.width(text);
        float x = -textWidth / 2.0f;
        int color = parseArgb(waypoint.colorArgb(), settings.markerArgb);
        int background = settings.background ? WaypointAppearance.backgroundArgb(waypoint, settings.backgroundArgb) : 0;

        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.mulPose(camera.rotation());
        pose.scale(scale, -scale, scale);
        drawRoundedPanel(buffers, pose.last().pose(), x - 3.0f, -7.0f,
                x + textWidth + 3.0f, 8.0f, background, color);
        buffers.endBatch(RenderTypes.textBackgroundSeeThrough());
        minecraft.font.drawInBatch(text, x, -4.0f, color, false, pose.last().pose(),
                buffers, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
        pose.popPose();
    }

    private static void drawRoundedPanel(MultiBufferSource buffers, Matrix4fc matrix,
                                         float left, float top, float right, float bottom,
                                         int background, int border) {
        VertexConsumer vertices = buffers.getBuffer(RenderTypes.textBackgroundSeeThrough());
        if ((background >>> 24) != 0) {
            quad(vertices, matrix, left + 2, top + 1, right - 2, top + 2, 0.01f, background);
            quad(vertices, matrix, left + 1, top + 2, right - 1, bottom - 2, 0.01f, background);
            quad(vertices, matrix, left + 2, bottom - 2, right - 2, bottom - 1, 0.01f, background);
        }
        quad(vertices, matrix, left + 2, top, right - 2, top + 1, 0.01f, border);
        quad(vertices, matrix, left + 2, bottom - 1, right - 2, bottom, 0.01f, border);
        quad(vertices, matrix, left, top + 2, left + 1, bottom - 2, 0.01f, border);
        quad(vertices, matrix, right - 1, top + 2, right, bottom - 2, 0.01f, border);
        quad(vertices, matrix, left + 1, top + 1, left + 2, top + 2, 0.01f, border);
        quad(vertices, matrix, right - 2, top + 1, right - 1, top + 2, 0.01f, border);
        quad(vertices, matrix, left + 1, bottom - 2, left + 2, bottom - 1, 0.01f, border);
        quad(vertices, matrix, right - 2, bottom - 2, right - 1, bottom - 1, 0.01f, border);
    }

    private static void drawLaser(PoseStack pose, MultiBufferSource buffers, Vec3 cameraPos,
                                  DisplayTarget target, int waypointColor) {
        int color = 0xB0000000 | (waypointColor & 0x00FFFFFF);
        float bottom = (float)(-64.0 - cameraPos.y);
        float top = (float)(384.0 - cameraPos.y);
        float halfWidth = 0.055f;
        pose.pushPose();
        pose.translate(target.x - cameraPos.x, 0.0, target.z - cameraPos.z);
        Matrix4fc matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderTypes.debugQuads());
        vertices.addVertex(matrix, -halfWidth, bottom, 0).setColor(color);
        vertices.addVertex(matrix, -halfWidth, top, 0).setColor(color);
        vertices.addVertex(matrix, halfWidth, top, 0).setColor(color);
        vertices.addVertex(matrix, halfWidth, bottom, 0).setColor(color);
        vertices.addVertex(matrix, 0, bottom, -halfWidth).setColor(color);
        vertices.addVertex(matrix, 0, top, -halfWidth).setColor(color);
        vertices.addVertex(matrix, 0, top, halfWidth).setColor(color);
        vertices.addVertex(matrix, 0, bottom, halfWidth).setColor(color);
        pose.popPose();
    }

    private static void quad(VertexConsumer vertices, Matrix4fc matrix, float left, float top,
                             float right, float bottom, float z, int color) {
        vertices.addVertex(matrix, left, top, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(color).setLight(FULL_BRIGHT);
    }

    private static DisplayTarget convert(Waypoint waypoint, String currentDimension) {
        if (currentDimension.equals(waypoint.dimension())) return new DisplayTarget(waypoint.x(), waypoint.y(), waypoint.z());
        if ("minecraft:overworld".equals(currentDimension) && "minecraft:the_nether".equals(waypoint.dimension())) {
            return new DisplayTarget(waypoint.x() * 8.0, waypoint.y(), waypoint.z() * 8.0);
        }
        if ("minecraft:the_nether".equals(currentDimension) && "minecraft:overworld".equals(waypoint.dimension())) {
            return new DisplayTarget(waypoint.x() / 8.0, waypoint.y(), waypoint.z() / 8.0);
        }
        return null;
    }

    private static int parseArgb(String value, int fallback) {
        try { return (int)Long.parseLong(value.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String formatDistance(double distance) {
        return distance >= 1000.0
                ? String.format(java.util.Locale.ROOT, "%.1f km", distance / 1000.0)
                : Math.round(distance) + " m";
    }

    private record DisplayTarget(double x, double y, double z) { }
}
