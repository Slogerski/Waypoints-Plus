package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointAppearance;
import pl.slogerski.waypointsplus.core.WaypointDimensionProjection;

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
    private static boolean cachedCrossDimensionWaypoints;
    private static List<PreparedWaypoint> cachedWaypoints = List.of();

    private WaypointHudRenderer() { }

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(WaypointHudRenderer::render);
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
        SubmitNodeCollector submits = context.submitNodeCollector();

        List<PreparedWaypoint> visible = new ArrayList<>();
        for (PreparedWaypoint prepared : activeWaypoints(store, serverKey, profile, dimension,
                settings.crossDimensionWaypoints)) {
            if (isInView(camera.yRot(), camera.xRot(), cameraPos.x, cameraPos.y, cameraPos.z,
                    prepared.target())) {
                visible.add(prepared);
            }
        }
        if (settings.laserEnabled) {
            for (PreparedWaypoint prepared : visible) {
                drawLaser(pose, submits, cameraPos, prepared.target(),
                        parseArgb(prepared.waypoint().colorArgb(), settings.markerArgb));
            }
        }
        for (PreparedWaypoint prepared : visible) {
            renderLabel(minecraft, pose, submits, camera, cameraPos,
                    prepared.waypoint(), prepared.target(), settings);
        }
    }

    private static void renderLabel(Minecraft minecraft, PoseStack pose, SubmitNodeCollector submits,
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
        int background = settings.background
                ? WaypointAppearance.backgroundArgb(waypoint, settings.backgroundArgb, color, settings.markerTintPercent)
                : 0;

        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.mulPose(camera.rotation());
        pose.scale(scale, -scale, scale);

        OrderedSubmitNodeCollector panelSubmits = submits.order(0);
        OrderedSubmitNodeCollector textSubmits = submits.order(1);
        panelSubmits.submitCustomGeometry(pose, RenderTypes.textBackgroundSeeThrough(),
                (entry, vertices) -> drawRoundedPanel(vertices, entry.pose(), x - 3.0f, -7.0f,
                        x + textWidth + 3.0f, 8.0f, background, color));
        textSubmits.submitText(pose, x, -3.0f, text.getVisualOrderText(), false,
                Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, color, 0, 0);
        pose.popPose();
    }

    private static void drawRoundedPanel(VertexConsumer vertices, Matrix4fc matrix,
                                         float left, float top, float right, float bottom,
                                         int background, int border) {
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

    private static void drawLaser(PoseStack pose, SubmitNodeCollector submits, Vec3 cameraPos,
                                  DisplayTarget target, int waypointColor) {
        int color = 0xB0000000 | (waypointColor & 0x00FFFFFF);
        float bottom = (float)(-64.0 - cameraPos.y);
        float top = (float)(384.0 - cameraPos.y);
        float halfWidth = 0.055f;
        pose.pushPose();
        pose.translate(target.x - cameraPos.x, 0.0, target.z - cameraPos.z);
        submits.submitCustomGeometry(pose, RenderTypes.debugQuads(), (entry, vertices) -> {
            Matrix4fc matrix = entry.pose();
            vertices.addVertex(matrix, -halfWidth, bottom, 0).setColor(color);
            vertices.addVertex(matrix, -halfWidth, top, 0).setColor(color);
            vertices.addVertex(matrix, halfWidth, top, 0).setColor(color);
            vertices.addVertex(matrix, halfWidth, bottom, 0).setColor(color);
            vertices.addVertex(matrix, 0, bottom, -halfWidth).setColor(color);
            vertices.addVertex(matrix, 0, top, -halfWidth).setColor(color);
            vertices.addVertex(matrix, 0, top, halfWidth).setColor(color);
            vertices.addVertex(matrix, 0, bottom, halfWidth).setColor(color);
        });
        pose.popPose();
    }

    private static void quad(VertexConsumer vertices, Matrix4fc matrix, float left, float top,
                             float right, float bottom, float z, int color) {
        vertices.addVertex(matrix, left, top, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(color).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(color).setLight(FULL_BRIGHT);
    }

    private static List<PreparedWaypoint> activeWaypoints(WaypointConfigStore store, String serverKey,
                                                          String profile, String dimension,
                                                          boolean crossDimensionWaypoints) {
        long revision = store.waypointRevision();
        if (revision == cachedRevision && serverKey.equals(cachedServerKey)
                && profile.equals(cachedProfile) && dimension.equals(cachedDimension)
                && crossDimensionWaypoints == cachedCrossDimensionWaypoints) {
            return cachedWaypoints;
        }
        List<PreparedWaypoint> prepared = new ArrayList<>();
        for (Waypoint waypoint : store.waypoints()) {
            if (!serverKey.equals(waypoint.serverKey()) || !profile.equals(waypoint.profile())) continue;
            DisplayTarget target = convert(waypoint, dimension, crossDimensionWaypoints);
            if (target != null) prepared.add(new PreparedWaypoint(waypoint, target));
        }
        cachedRevision = revision;
        cachedServerKey = serverKey;
        cachedProfile = profile;
        cachedDimension = dimension;
        cachedCrossDimensionWaypoints = crossDimensionWaypoints;
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

    private static DisplayTarget convert(Waypoint waypoint, String currentDimension,
                                         boolean crossDimensionWaypoints) {
        double scale = WaypointDimensionProjection.scale(
                currentDimension, waypoint.dimension(), crossDimensionWaypoints);
        if (Double.isNaN(scale)) return null;
        return new DisplayTarget(waypoint.x() * scale, waypoint.y(), waypoint.z() * scale);
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
