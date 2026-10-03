package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import pl.slogerski.waypointsplus.core.LaserVisibility;
import pl.slogerski.waypointsplus.core.WaypointGrouping;
import pl.slogerski.waypointsplus.core.WaypointRenderLifecycle;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointDimensionProjection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WaypointHudRenderer {
    private static final boolean VULKAN_RENDERER = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vulkanmod");
    private static final int LABEL_BUFFER_SIZE = 786432;
    private static final double MAX_BILLBOARD_DISTANCE = 24.0;
    private static final VertexConsumerProvider.Immediate TEXT_BUFFERS = VertexConsumerProvider.immediate(new BufferBuilder(LABEL_BUFFER_SIZE));

    private static final List<PreparedWaypoint> VISIBLE_CLASSIC = new ArrayList<>();
    private static final Map<String, PresetBatch> PRESETS = new LinkedHashMap<>();
    private static final WaypointGrouping SMART_GROUPS = new WaypointGrouping();
    private static final SmartWaypointRefresh SMART_REFRESH = new SmartWaypointRefresh();
    private static final List<PreparedWaypoint> SMART_CANDIDATES = new ArrayList<>();
    private static final List<SmartStack> SMART_STACKS = new ArrayList<>();
    private static final WaypointPreset SMART_PRESET = WaypointPreset.designed();
    private static final WaypointProjection PROJECTION = new WaypointProjection();
    private static long cachedRevision = Long.MIN_VALUE;
    private static long cachedPresetRevision = Long.MIN_VALUE;
    private static long cachedLayoutRevision = Long.MIN_VALUE;
    private static String cachedServerKey;
    private static String cachedProfile;
    private static String cachedDimension;
    private static boolean cachedCrossDimensionWaypoints;
    private static int cachedSmartDistance;
    private static List<PreparedWaypoint> cachedWaypoints = List.of();
    private static final WaypointRenderLifecycle LIFECYCLE = new WaypointRenderLifecycle();
    private static final Runnable CLEAR_CACHE = WaypointHudRenderer::clearCache;

    private WaypointHudRenderer() { }

    static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() {
                        return new Identifier("waypointsplus", "text_shaders");
                    }
                    @Override public void reload(ResourceManager manager) {

                        invalidate();
                    }
                });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointHudRenderer::render);
    }

    static void invalidate() {
        LIFECYCLE.invalidate();
    }

    static void clearIfIdle() {
        LIFECYCLE.clearIfIdle(CLEAR_CACHE);
    }

    private static void clearCache() {
        for (PresetBatch batch : PRESETS.values()) batch.close();
        WaypointPresetIcon.clearLayers();
        PRESETS.clear(); VISIBLE_CLASSIC.clear();
        SMART_CANDIDATES.clear(); SMART_GROUPS.clear();
        SMART_STACKS.clear(); SMART_REFRESH.reset();
        cachedWaypoints = List.of();
        cachedRevision = Long.MIN_VALUE;
        cachedPresetRevision = Long.MIN_VALUE;
        cachedLayoutRevision = Long.MIN_VALUE;
    }

    private static void render(WorldRenderContext context) {
        if (!LIFECYCLE.beginFrame(CLEAR_CACHE)) return;
        try {
            renderFrame(context);
        } finally {
            LIFECYCLE.endFrame();
        }
    }

    private static void renderFrame(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.player == null || client.world == null || matrices == null) return;
        WaypointConfigStore store = WaypointsPlusClient.config();
        WaypointSettings settings = store.settings();
        if (!settings.enabled) return;
        String dimension = client.world.getRegistryKey().getValue().toString();
        String serverKey = ServerScope.current();
        String profile = store.activeProfile(serverKey);
        store.claimLegacy(serverKey);
        prepare(store, serverKey, profile, dimension, settings.crossDimensionWaypoints);
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        int bottomY = settings.laserEnabled ? client.world.getBottomY() : 0;
        int topY = settings.laserEnabled ? bottomY + client.world.getHeight() : 0;
        LaserVisibility laserView = settings.laserEnabled
                ? LaserVisibility.fromCamera(camera.getYaw(), camera.getPitch(), cameraPos.x, cameraPos.y, cameraPos.z,
                        bottomY, topY) : null;
        double yaw = Math.toRadians(camera.getYaw()), pitch = Math.toRadians(camera.getPitch());
        double cosPitch = Math.cos(pitch);
        double viewX = -Math.sin(yaw) * cosPitch, viewY = -Math.sin(pitch), viewZ = Math.cos(yaw) * cosPitch;
        VISIBLE_CLASSIC.clear();
        double smartDistanceSquared = (double) settings.smartDistance * settings.smartDistance;
        if (settings.smartWaypoints) {
            for (SmartStack stack : SMART_STACKS) {
                stack.eligibleMembers = 0;
                stack.nearest = null;
                stack.representative = null;
                stack.replacement = null;
                stack.iconMembers = 0;
                stack.visibleMembers = 0;
                stack.memberLeft = stack.memberTop = Float.POSITIVE_INFINITY;
                stack.memberRight = stack.memberBottom = Float.NEGATIVE_INFINITY;
            }
            if (cachedSmartDistance != settings.smartDistance) SMART_REFRESH.reset();
            cachedSmartDistance = settings.smartDistance;
            double playerX = client.player.getX(), playerY = client.player.getY(), playerZ = client.player.getZ();
            for (PreparedWaypoint prepared : cachedWaypoints) {
                DisplayTarget target = prepared.target;
                double dx = target.x - playerX, dy = target.y - playerY, dz = target.z - playerZ;
                prepared.playerDistanceSquared = dx * dx + dy * dy + dz * dz;
                boolean far = prepared.playerDistanceSquared > smartDistanceSquared;
                if (prepared.smartDistanceKnown && prepared.smartFar != far) SMART_REFRESH.membershipChanged();
                prepared.smartDistanceKnown = true;
                prepared.smartFar = far;
                if (far && prepared.stack != null) {
                    SmartStack stack = prepared.stack;
                    stack.eligibleMembers++;
                    if (prepared.batch != null && prepared.batch.preset.icon) stack.iconMembers++;
                    if (stack.nearest == null || prepared.playerDistanceSquared < stack.nearest.playerDistanceSquared) {
                        stack.nearest = prepared;
                    }
                }
            }
        }
        long now = System.nanoTime();
        boolean refreshSmart = SMART_REFRESH.due(settings.smartWaypoints, now);
        if (refreshSmart) {
            SMART_CANDIDATES.clear();
            SMART_GROUPS.begin(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
                    cachedWaypoints.size(), settings.smartDistance);
            PROJECTION.begin(context.projectionMatrix(), RenderSystem.getModelViewMatrix(),
                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }
        for (PresetBatch batch : PRESETS.values()) batch.visible.clear();
        boolean hasLasers = false;
        for (PreparedWaypoint prepared : cachedWaypoints) {
            DisplayTarget target = prepared.target;
            prepared.hidden = false;
            prepared.compact = false;
            if (refreshSmart) prepared.group = -1;
            if (laserView != null && laserView.isPotentiallyVisible(target.x, target.z)) {
                drawLaser(matrices, buffers, cameraPos, target,
                        prepared.label.marker(settings.markerArgb), bottomY, topY);
                hasLasers = true;
            }
            double dx = target.x - cameraPos.x, dy = target.y + 1.5 - cameraPos.y, dz = target.z - cameraPos.z;
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            double dot = dx * viewX + dy * viewY + dz * viewZ;
            if (prepared.batch == null && dot < 0 && dot * dot > 0.0225 * lengthSquared) continue;
            double distance = Math.sqrt(lengthSquared);
            prepared.distance = distance;
            double visibleDistance = Math.min(distance, MAX_BILLBOARD_DISTANCE);
            float scale = 0.025f * settings.scale * (float) Math.max(1, visibleDistance / 10);
            prepared.updated = refreshSmart || !settings.smartWaypoints || prepared.batch == null
                    || !prepared.batch.preset.icon || dot < 0;
            if (prepared.updated) prepared.label.update(client.textRenderer, settings, distance);
            if (prepared.batch != null && dot < -0.15 * distance && visibleDistance > 0
                    && -dot > prepared.label.radius() * scale * distance / visibleDistance) continue;
            if (distance > MAX_BILLBOARD_DISTANCE) {
                double factor = MAX_BILLBOARD_DISTANCE / distance;
                dx *= factor; dy *= factor; dz *= factor;
            }
            prepared.label.matrix.set(matrices.peek().getPositionMatrix()).translate((float) dx, (float) dy, (float) dz)
                    .rotate(camera.getRotation()).scale(-scale, -scale, scale);
            if (settings.smartWaypoints && prepared.smartFar && prepared.stack != null
                    && prepared.batch != null && prepared.batch.preset.icon) {
                SmartStack stack = prepared.stack;
                if (stack.representative == null
                        || prepared.playerDistanceSquared < stack.representative.playerDistanceSquared) {
                    stack.representative = prepared;
                }
            }
            if (prepared.batch == null) {
                VISIBLE_CLASSIC.add(prepared);
                if (refreshSmart) collectSmart(prepared);
            }
            else {
                prepared.batch.visible.add(prepared);
                if (refreshSmart) collectSmart(prepared);
            }
        }
        if (refreshSmart) rebuildSmart(client, settings);
        if (settings.smartWaypoints) {
            VISIBLE_CLASSIC.removeIf(prepared -> prepared.smartFar && prepared.stack != null && prepared.stack.collapsed());
            for (PresetBatch batch : PRESETS.values()) {
                for (PreparedWaypoint prepared : batch.visible) {
                    SmartStack stack = prepared.stack;
                    if (prepared.smartFar && stack != null && stack.collapsed()) {
                        if (stack.representative != prepared) { prepared.hidden = true; continue; }
                        prepared.compact = true;
                        updateSmartLabel(prepared, stack.nearest, stack.eligibleMembers, client, settings);
                    }
                }
                batch.visible.removeIf(prepared -> prepared.hidden);
                for (PreparedWaypoint prepared : batch.visible) {
                    if (!prepared.compact && !prepared.updated) prepared.label.update(client.textRenderer, settings, prepared.distance);
                }
            }
        }
        if (hasLasers) buffers.draw(RenderLayer.getDebugQuads());
        renderLabels(client, buffers);
    }

    private static void renderLabels(MinecraftClient client, VertexConsumerProvider.Immediate buffers) {
        boolean visible = !VISIBLE_CLASSIC.isEmpty();
        for (PresetBatch batch : PRESETS.values()) visible |= !batch.visible.isEmpty();
        if (!visible) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            VertexConsumer panels;
            if (VULKAN_RENDERER) {
                panels = buffers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                BufferBuilder builder = Tessellator.getInstance().getBuffer();
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                panels = builder;
            }
            for (PreparedWaypoint prepared : VISIBLE_CLASSIC) prepared.label.drawPanel(panels);
            for (PresetBatch batch : PRESETS.values()) {
                for (PreparedWaypoint prepared : batch.visible) prepared.renderLabel().drawPanel(panels);
            }
            if (VULKAN_RENDERER) buffers.draw(RenderLayer.getTextBackgroundSeeThrough());
            else Tessellator.getInstance().draw();
            VertexConsumerProvider.Immediate textTarget = VULKAN_RENDERER ? buffers : TEXT_BUFFERS;
            for (PresetBatch batch : PRESETS.values()) {
                if (batch.preset.icon && !batch.iconPrepared && !batch.visible.isEmpty()) {
                    batch.iconPrepared = true;
                    batch.icon = new WaypointPresetIcon(batch.preset);
                    break;
                }
            }
            for (PresetBatch batch : PRESETS.values()) {
                if (batch.icon == null || batch.visible.isEmpty()) continue;
                for (PreparedWaypoint prepared : batch.visible) {
                    WaypointPreset layout = prepared.compact ? SMART_PRESET : batch.preset;
                    WaypointLabel label = prepared.renderLabel();
                    batch.icon.draw(label.matrix, textTarget, label.iconX(), label.iconY(), layout.iconScale);
                }
            }
            if (!PRESETS.isEmpty()) textTarget.draw();
            for (PreparedWaypoint prepared : VISIBLE_CLASSIC) prepared.label.drawText(client.textRenderer, textTarget);
            for (PresetBatch batch : PRESETS.values()) {
                for (PreparedWaypoint prepared : batch.visible) prepared.renderLabel().drawText(client.textRenderer, textTarget);
            }
            textTarget.draw();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static void rebuildSmart(MinecraftClient client, WaypointSettings settings) {
        SMART_CANDIDATES.removeIf(prepared -> prepared.stack != null && prepared.stack.valid());
        for (SmartStack stack : SMART_STACKS) {
            if (!stack.collapsed() || stack.visibleMembers != stack.eligibleMembers) continue;
            PreparedWaypoint prepared = stack.representative;
            updateSmartLabel(prepared, stack.nearest, stack.eligibleMembers, client, settings);
            WaypointLabel label = prepared.smartLabel;
            if (!PROJECTION.project(label.matrix, label.visualLeft(), label.visualTop(),
                    label.visualRight() - label.visualLeft(), label.visualBottom() - label.visualTop())
                    || !SMART_GROUPS.visibleBounds(PROJECTION.left, PROJECTION.top, PROJECTION.right, PROJECTION.bottom)) continue;
            prepared.screenLeft = PROJECTION.left; prepared.screenTop = PROJECTION.top;
            prepared.screenRight = PROJECTION.right; prepared.screenBottom = PROJECTION.bottom;
            SMART_CANDIDATES.add(prepared);
        }
        PreparedWaypoint[] candidatesByGroup = new PreparedWaypoint[SMART_CANDIDATES.size()];
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            SmartStack stack = prepared.stack != null && prepared.stack.valid() ? prepared.stack : null;
            prepared.group = SMART_GROUPS.add(prepared.screenLeft, prepared.screenTop,
                    prepared.screenRight, prepared.screenBottom,
                    stack == null ? prepared.playerDistanceSquared : stack.nearest.playerDistanceSquared,
                    stack != null || prepared.batch != null && prepared.batch.preset.icon,
                    stack == null ? 1 : stack.eligibleMembers, stack != null && stack.dense);
            if (prepared.group >= 0) {
                candidatesByGroup[prepared.group] = prepared;
                if (stack != null) SMART_GROUPS.memberBounds(prepared.group,
                        stack.memberLeft, stack.memberTop, stack.memberRight, stack.memberBottom);
            }
        }
        SMART_CANDIDATES.removeIf(prepared -> prepared.group < 0);
        SMART_GROUPS.finish();
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            if (!SMART_GROUPS.collapsed(prepared.group) || !SMART_GROUPS.representative(prepared.group)) continue;
            updateSmartLabel(prepared, smartNearest(candidatesByGroup[SMART_GROUPS.nearestIndex(prepared.group)]),
                    SMART_GROUPS.groupSize(prepared.group), client, settings);
            WaypointLabel label = prepared.smartLabel;
            if (PROJECTION.project(label.matrix, label.visualLeft(), label.visualTop(),
                    label.visualRight() - label.visualLeft(), label.visualBottom() - label.visualTop())) {
                SMART_GROUPS.includeStackBounds(prepared.group, PROJECTION.left, PROJECTION.top, PROJECTION.right, PROJECTION.bottom);
            }
        }
        SMART_GROUPS.finish();
        SmartStack[] replacements = new SmartStack[candidatesByGroup.length];
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            if (!SMART_GROUPS.collapsed(prepared.group)) continue;
            int representative = SMART_GROUPS.representativeIndex(prepared.group);
            if (replacements[representative] == null) {
                replacements[representative] = new SmartStack(candidatesByGroup[representative],
                        smartNearest(candidatesByGroup[SMART_GROUPS.nearestIndex(prepared.group)]),
                        SMART_GROUPS.denseGroup(prepared.group));
            }
            SmartStack replacement = replacements[representative];
            SmartStack previous = prepared.stack;
            if (previous != null && previous.valid()) {
                previous.replacement = replacement;
                replacement.eligibleMembers += previous.eligibleMembers;
                replacement.iconMembers += previous.iconMembers;
            } else {
                replacement.eligibleMembers++;
                if (prepared.batch != null && prepared.batch.preset.icon) replacement.iconMembers++;
            }
        }
        for (PreparedWaypoint prepared : cachedWaypoints) {
            SmartStack previous = prepared.stack;
            if (prepared.smartFar && previous != null && previous.valid()) {
                if (previous.replacement != null) prepared.stack = previous.replacement;
            } else {
                prepared.stack = prepared.group >= 0 && SMART_GROUPS.collapsed(prepared.group)
                        ? replacements[SMART_GROUPS.representativeIndex(prepared.group)] : null;
            }
        }
        SMART_STACKS.removeIf(stack -> !stack.valid() || stack.replacement != null);
        for (SmartStack stack : replacements) if (stack != null) SMART_STACKS.add(stack);
    }

    private static PreparedWaypoint smartNearest(PreparedWaypoint prepared) {
        return prepared.stack != null && prepared.stack.valid() ? prepared.stack.nearest : prepared;
    }

    private static void updateSmartLabel(PreparedWaypoint prepared, PreparedWaypoint nearest, int count,
                                         MinecraftClient client, WaypointSettings settings) {
        if (prepared.smartLabel == null || prepared.smartLabelSource != nearest || prepared.smartLabelCount != count) {
            String name = nearest.waypoint.name() + " (" + count + ")";
            Waypoint original = nearest.waypoint;
            Waypoint grouped = new Waypoint(original.id(), name, original.serverKey(), original.profile(),
                    original.dimension(), original.x(), original.y(), original.z(), original.colorArgb());
            DisplayTarget target = nearest.target;
            prepared.smartLabel = new WaypointLabel(grouped, target.x, target.y, target.z, SMART_PRESET);
            prepared.smartLabelSource = nearest;
            prepared.smartLabelCount = count;
        }
        prepared.smartLabel.update(client.textRenderer, settings, Math.sqrt(nearest.playerDistanceSquared));
        prepared.smartLabel.matrix.set(prepared.label.matrix);
    }

    private static void collectSmart(PreparedWaypoint prepared) {
        if (!prepared.smartFar) return;
        float left = prepared.label.visualLeft();
        float top = prepared.label.visualTop();
        float width = prepared.label.visualRight() - left;
        float height = prepared.label.visualBottom() - top;
        if (!PROJECTION.project(prepared.label.matrix, left, top, width, height)) return;
        prepared.screenLeft = PROJECTION.left; prepared.screenTop = PROJECTION.top;
        prepared.screenRight = PROJECTION.right; prepared.screenBottom = PROJECTION.bottom;
        SMART_CANDIDATES.add(prepared);
        if (prepared.stack != null && SMART_GROUPS.visibleBounds(PROJECTION.left, PROJECTION.top,
                PROJECTION.right, PROJECTION.bottom)) {
            SmartStack stack = prepared.stack;
            stack.visibleMembers++;
            float x = PROJECTION.left * 0.5f + PROJECTION.right * 0.5f;
            float y = PROJECTION.top * 0.5f + PROJECTION.bottom * 0.5f;
            stack.memberLeft = Math.min(stack.memberLeft, x); stack.memberTop = Math.min(stack.memberTop, y);
            stack.memberRight = Math.max(stack.memberRight, x); stack.memberBottom = Math.max(stack.memberBottom, y);
        }
    }

    private static void prepare(WaypointConfigStore store, String serverKey, String profile,
                                String dimension, boolean crossDimensionWaypoints) {
        long revision = store.waypointRevision(), presetRevision = WaypointPresetStore.revision();
        if (revision == cachedRevision && presetRevision == cachedPresetRevision && serverKey.equals(cachedServerKey)
                && profile.equals(cachedProfile) && dimension.equals(cachedDimension)
                && crossDimensionWaypoints == cachedCrossDimensionWaypoints) return;
        Map<String, PresetBatch> previous = new LinkedHashMap<>(PRESETS);
        long layoutRevision = WaypointPresetStore.layoutRevision();
        if (layoutRevision != cachedLayoutRevision) {
            for (PresetBatch batch : previous.values()) batch.close();
            previous.clear();
        }
        PRESETS.clear();
        List<PreparedWaypoint> prepared = new ArrayList<>();
        Map<String, WaypointPreset> definitions = new java.util.HashMap<>();
        for (Waypoint waypoint : store.waypoints()) {
            if (!serverKey.equals(waypoint.serverKey()) || !profile.equals(waypoint.profile())) continue;
            double scale = WaypointDimensionProjection.scale(dimension, waypoint.dimension(), crossDimensionWaypoints);
            if (Double.isNaN(scale)) continue;
            DisplayTarget target = new DisplayTarget(Math.floor(waypoint.x() * scale) + 0.5, waypoint.y(),
                    Math.floor(waypoint.z() * scale) + 0.5);
            String presetId = WaypointPresetStore.selected(waypoint.id());
            PresetBatch batch = null;
            if (!"default".equals(presetId)) {
                WaypointPreset definition = definitions.computeIfAbsent(presetId, WaypointPresetStore::find);
                String item = definition != null && definition.icon && !definition.pngIcon
                        ? WaypointPresetStore.item(waypoint.id()) : "";
                String batchKey = presetId + "|" + item;
                batch = PRESETS.get(batchKey);
                if (batch == null) {
                    batch = previous.remove(batchKey);
                    if (batch == null) {
                        if (definition != null) {
                            WaypointPreset preset = item.isEmpty() ? definition : WaypointPresetStore.copy(definition);
                            if (!item.isEmpty()) preset.item = item;
                            batch = new PresetBatch(preset);
                        }
                    }
                    if (batch != null) PRESETS.put(batchKey, batch);
                }
            }
            prepared.add(new PreparedWaypoint(waypoint, target, batch,
                    new WaypointLabel(waypoint, target.x, target.y, target.z, batch == null ? null : batch.preset)));
        }
        for (PresetBatch batch : previous.values()) batch.close();
        SMART_STACKS.clear(); SMART_CANDIDATES.clear(); SMART_REFRESH.reset();
        cachedWaypoints = List.copyOf(prepared);
        cachedRevision = store.waypointRevision(); cachedPresetRevision = presetRevision;
        cachedLayoutRevision = layoutRevision;
        cachedServerKey = serverKey; cachedProfile = profile; cachedDimension = dimension;
        cachedCrossDimensionWaypoints = crossDimensionWaypoints;
    }

    private static void drawLaser(MatrixStack matrices, VertexConsumerProvider buffers, Vec3d cameraPos,
                                  DisplayTarget target, int waypointColor, int bottomY, int topY) {
        int color = 0xB0000000 | (waypointColor & 0x00FFFFFF);
        float bottom = (float) (bottomY - cameraPos.y), top = (float) (topY - cameraPos.y);
        float halfWidth = LaserVisibility.HALF_WIDTH;
        matrices.push();
        matrices.translate(target.x - cameraPos.x, 0.0, target.z - cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vertices = buffers.getBuffer(RenderLayer.getDebugQuads());
        vertices.vertex(matrix, -halfWidth, bottom, 0).color(color).next();
        vertices.vertex(matrix, -halfWidth, top, 0).color(color).next();
        vertices.vertex(matrix, halfWidth, top, 0).color(color).next();
        vertices.vertex(matrix, halfWidth, bottom, 0).color(color).next();
        vertices.vertex(matrix, 0, bottom, -halfWidth).color(color).next();
        vertices.vertex(matrix, 0, top, -halfWidth).color(color).next();
        vertices.vertex(matrix, 0, top, halfWidth).color(color).next();
        vertices.vertex(matrix, 0, bottom, halfWidth).color(color).next();
        matrices.pop();
    }

    private record DisplayTarget(double x, double y, double z) { }
    private static final class PreparedWaypoint {
        final Waypoint waypoint;
        final DisplayTarget target;
        final PresetBatch batch;
        final WaypointLabel label;
        WaypointLabel smartLabel;
        PreparedWaypoint smartLabelSource;
        int smartLabelCount;
        SmartStack stack;
        boolean hidden, compact, updated;
        boolean smartDistanceKnown, smartFar;
        int group;
        double distance, playerDistanceSquared;
        float screenLeft, screenTop, screenRight, screenBottom;

        PreparedWaypoint(Waypoint waypoint, DisplayTarget target, PresetBatch batch, WaypointLabel label) {
            this.waypoint = waypoint; this.target = target; this.batch = batch; this.label = label;
        }

        WaypointLabel renderLabel() { return compact ? smartLabel : label; }
    }

    private static final class SmartStack {
        PreparedWaypoint representative;
        PreparedWaypoint nearest;
        SmartStack replacement;
        final boolean dense;
        int eligibleMembers, iconMembers, visibleMembers;
        float memberLeft, memberTop, memberRight, memberBottom;

        SmartStack(PreparedWaypoint representative, PreparedWaypoint nearest, boolean dense) {
            this.representative = representative;
            this.nearest = nearest;
            this.dense = dense;
        }

        boolean valid() { return eligibleMembers >= 3 && iconMembers > 0; }
        boolean collapsed() { return valid() && representative != null; }
    }

    private static final class PresetBatch implements AutoCloseable {
        final WaypointPreset preset;
        WaypointPresetIcon icon;
        boolean iconPrepared;
        final List<PreparedWaypoint> visible = new ArrayList<>();
        PresetBatch(WaypointPreset preset) {
            this.preset = preset;
        }
        @Override public void close() { if (icon != null) icon.close(); visible.clear(); }
    }
}
