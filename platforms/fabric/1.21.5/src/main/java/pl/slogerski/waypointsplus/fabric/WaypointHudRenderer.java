package pl.slogerski.waypointsplus.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointAppearance;

import java.util.ArrayList;
import java.util.List;

final class WaypointHudRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double MAX_BILLBOARD_DISTANCE = 24.0;
    private static final double VIEW_CULL_DOT = -0.15;
    private static long cachedRevision = Long.MIN_VALUE;
    private static String cachedServerKey;
    private static String cachedProfile;
    private static String cachedDimension;
    private static List<PreparedWaypoint> cachedWaypoints = List.of();

    private WaypointHudRenderer() { }

    static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointHudRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.player == null || client.world == null || matrices == null) return;
        WaypointConfigStore store = WaypointsPlusClient.config();
        store.reloadWaypointsIfChanged();
        WaypointSettings settings = store.settings();
        if (!settings.enabled) return;

        String dimension = client.world.getRegistryKey().getValue().toString();
        String serverKey = ServerScope.current();
        String profile = store.activeProfile(serverKey);
        store.claimLegacy(serverKey);
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();

        List<PreparedWaypoint> visible = new ArrayList<>();
        for (PreparedWaypoint prepared : activeWaypoints(store, serverKey, profile, dimension)) {
            if (isInView(camera.getYaw(), camera.getPitch(), cameraPos.x, cameraPos.y, cameraPos.z,
                    prepared.target())) {
                visible.add(prepared);
            }
        }
        if (settings.laserEnabled) {
            for (PreparedWaypoint prepared : visible) {
                drawLaser(matrices, buffers, cameraPos, prepared.target(),
                        parseArgb(prepared.waypoint().colorArgb(), settings.markerArgb));
            }
            if (!visible.isEmpty()) buffers.draw(RenderLayer.getDebugQuads());
        }
        for (PreparedWaypoint prepared : visible) {
            renderLabel(client, matrices, buffers, camera, cameraPos,
                    prepared.waypoint(), prepared.target(), settings);
        }
        buffers.draw();
    }

    private static void renderLabel(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider.Immediate buffers,
                                    Camera camera, Vec3d cameraPos, Waypoint waypoint,
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
        Text text = Text.literal(label);
        int textWidth = client.textRenderer.getWidth(text);
        float x = -textWidth / 2.0f;
        int color = parseArgb(waypoint.colorArgb(), settings.markerArgb);
        int background = settings.background
                ? WaypointAppearance.backgroundArgb(waypoint, settings.backgroundArgb, color, settings.markerTintPercent)
                : 0;

        matrices.push();
        matrices.translate(dx, dy, dz);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);

        drawRoundedPanel(buffers, matrices.peek().getPositionMatrix(), x - 3.0f, -7.0f,
                x + textWidth + 3.0f, 8.0f, background, color);
        buffers.draw(RenderLayer.getTextBackgroundSeeThrough());
        client.textRenderer.draw(text, x, -3.0f, color, false, matrices.peek().getPositionMatrix(),
                buffers, TextRenderer.TextLayerType.SEE_THROUGH, 0, FULL_BRIGHT);
        buffers.draw();
        matrices.pop();
    }

    private static void drawRoundedPanel(VertexConsumerProvider buffers, Matrix4f matrix,
                                         float left, float top, float right, float bottom,
                                         int background, int border) {
        VertexConsumer vertices = buffers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
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

    private static void drawLaser(MatrixStack matrices, VertexConsumerProvider buffers, Vec3d cameraPos,
                                  DisplayTarget target, int waypointColor) {
        int color = 0xB0000000 | (waypointColor & 0x00FFFFFF);
        float bottom = (float)(-64.0 - cameraPos.y);
        float top = (float)(384.0 - cameraPos.y);
        float halfWidth = 0.055f;
        matrices.push();
        matrices.translate(target.x - cameraPos.x, 0.0, target.z - cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vertices = buffers.getBuffer(RenderLayer.getDebugQuads());
        vertices.vertex(matrix, -halfWidth, bottom, 0).color(color);
        vertices.vertex(matrix, -halfWidth, top, 0).color(color);
        vertices.vertex(matrix, halfWidth, top, 0).color(color);
        vertices.vertex(matrix, halfWidth, bottom, 0).color(color);
        vertices.vertex(matrix, 0, bottom, -halfWidth).color(color);
        vertices.vertex(matrix, 0, top, -halfWidth).color(color);
        vertices.vertex(matrix, 0, top, halfWidth).color(color);
        vertices.vertex(matrix, 0, bottom, halfWidth).color(color);
        matrices.pop();
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, float left, float top,
                             float right, float bottom, float z, int color) {
        vertices.vertex(matrix, left, top, z).color(color).light(FULL_BRIGHT);
        vertices.vertex(matrix, left, bottom, z).color(color).light(FULL_BRIGHT);
        vertices.vertex(matrix, right, bottom, z).color(color).light(FULL_BRIGHT);
        vertices.vertex(matrix, right, top, z).color(color).light(FULL_BRIGHT);
    }

    private static List<PreparedWaypoint> activeWaypoints(WaypointConfigStore store, String serverKey,
                                                          String profile, String dimension) {
        long revision = store.waypointRevision();
        if (revision == cachedRevision && serverKey.equals(cachedServerKey)
                && profile.equals(cachedProfile) && dimension.equals(cachedDimension)) {
            return cachedWaypoints;
        }
        List<PreparedWaypoint> prepared = new ArrayList<>();
        for (Waypoint waypoint : store.waypoints()) {
            if (!serverKey.equals(waypoint.serverKey()) || !profile.equals(waypoint.profile())) continue;
            DisplayTarget target = convert(waypoint, dimension);
            if (target != null) prepared.add(new PreparedWaypoint(waypoint, target));
        }
        cachedRevision = revision;
        cachedServerKey = serverKey;
        cachedProfile = profile;
        cachedDimension = dimension;
        cachedWaypoints = List.copyOf(prepared);
        return cachedWaypoints;
    }

    private static boolean isInView(float yawDegrees, float pitchDegrees,
                                    double cameraX, double cameraY, double cameraZ,
                                    DisplayTarget target) {
        double dx = target.x - cameraX;
        double dy = target.y + 1.5 - cameraY;
        double dz = target.z - cameraZ;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0e-6) return true;
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        double dot = (dx * (-Math.sin(yaw) * cosPitch)
                + dy * -Math.sin(pitch)
                + dz * (Math.cos(yaw) * cosPitch)) / Math.sqrt(lengthSquared);
        return dot >= VIEW_CULL_DOT;
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
        if (distance < 1000.0) return Math.round(distance) + " m";
        String formatted = String.format(java.util.Locale.ROOT, "%.1f km", distance / 1000.0);
        return UiText.get(formatted, formatted.replace('.', ','));
    }

    private record PreparedWaypoint(Waypoint waypoint, DisplayTarget target) { }

    private record DisplayTarget(double x, double y, double z) { }
}
