package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import pl.slogerski.waypointsplus.core.LaserVisibility;
import pl.slogerski.waypointsplus.core.Waypoint;
import pl.slogerski.waypointsplus.core.WaypointDimensionProjection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WaypointHudRenderer {
    private static final int LABEL_BUFFER_SIZE = 786432;
    private static final double MAX_BILLBOARD_DISTANCE = 24.0;
    private static final WaypointTextShaders TEXT_SHADERS = new WaypointTextShaders();
    private static final List<PreparedWaypoint> VISIBLE_CLASSIC = new ArrayList<>();
    private static final Map<String, PresetBatch> PRESETS = new LinkedHashMap<>();
    private static final SmartWaypointGroups SMART_GROUPS = new SmartWaypointGroups();
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
    private static volatile boolean resourcesChanged;

    private WaypointHudRenderer() { }

    static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() {
                        return Identifier.of("waypointsplus", "text_shaders");
                    }
                    @Override public void reload(ResourceManager manager) {
                        WaypointTextShaders.reload(manager);
                        resourcesChanged = true;
                    }
                });
        WorldRenderEvents.END_MAIN.register(WaypointHudRenderer::render);
    }

    static void clear() {
        for (PresetBatch batch : PRESETS.values()) batch.close();
        PRESETS.clear(); VISIBLE_CLASSIC.clear();
        SMART_CANDIDATES.clear(); SMART_GROUPS.clear();
        SMART_STACKS.clear(); SMART_REFRESH.reset();
        cachedWaypoints = List.of();
        cachedRevision = Long.MIN_VALUE;
        cachedPresetRevision = Long.MIN_VALUE;
        cachedLayoutRevision = Long.MIN_VALUE;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrices();
        if (client.player == null || client.world == null || matrices == null) return;
        if (resourcesChanged) { resourcesChanged = false; clear(); }
        WaypointConfigStore store = WaypointsPlusClient.config();
        WaypointSettings settings = store.settings();
        if (!settings.enabled) return;
        String dimension = client.world.getRegistryKey().getValue().toString();
        String serverKey = ServerScope.current();
        String profile = store.activeProfile(serverKey);
        store.claimLegacy(serverKey);
        prepare(store, serverKey, profile, dimension, settings.crossDimensionWaypoints);
        Camera camera = context.gameRenderer().getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        OrderedRenderCommandQueue buffers = context.commandQueue();
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
            if (cachedSmartDistance != settings.smartDistance) SMART_REFRESH.reset();
            cachedSmartDistance = settings.smartDistance;
            double playerX = client.player.getX(), playerY = client.player.getY(), playerZ = client.player.getZ();
            for (PreparedWaypoint prepared : cachedWaypoints) {
                DisplayTarget target = prepared.target;
                double dx = target.x - playerX, dy = target.y - playerY, dz = target.z - playerZ;
                prepared.playerDistanceSquared = dx * dx + dy * dy + dz * dz;
                boolean far = prepared.playerDistanceSquared > smartDistanceSquared;
                if (prepared.smartDistanceKnown && prepared.smartFar != far) SMART_REFRESH.reset();
                prepared.smartDistanceKnown = true;
                prepared.smartFar = far;
            }
        }
        long now = System.nanoTime();
        boolean refreshSmart = SMART_REFRESH.due(settings.smartWaypoints, now);
        if (refreshSmart) {
            SMART_CANDIDATES.clear();
            SMART_GROUPS.begin(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
                    cachedWaypoints.size(), settings.smartDistance);
            PROJECTION.begin(client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue()), RenderSystem.getModelViewMatrix(),
                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }
        if (settings.smartWaypoints) {
            for (SmartStack stack : SMART_STACKS) {
                stack.near = false;
                stack.rebuild = false;
                if (refreshSmart) stack.visibleMembers = 0;
            }
        }
        for (PresetBatch batch : PRESETS.values()) batch.visible.clear();
        boolean hasLasers = false;
        for (PreparedWaypoint prepared : cachedWaypoints) {
            DisplayTarget target = prepared.target;
            prepared.hidden = false;
            prepared.compact = false;
            if (refreshSmart) prepared.group = -1;
            if (settings.smartWaypoints && prepared.stack != null
                    && prepared.playerDistanceSquared <= smartDistanceSquared) prepared.stack.near = true;
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
            prepared.updated = !settings.smartWaypoints || prepared.batch == null || !prepared.batch.preset.icon || dot < 0;
            if (prepared.updated) prepared.label.update(client.textRenderer, settings, distance);
            if (prepared.batch != null && dot < -0.15 * distance && visibleDistance > 0
                    && -dot > prepared.label.radius() * scale * distance / visibleDistance) continue;
            if (distance > MAX_BILLBOARD_DISTANCE) {
                double factor = MAX_BILLBOARD_DISTANCE / distance;
                dx *= factor; dy *= factor; dz *= factor;
            }
            prepared.label.matrix.set(matrices.peek().getPositionMatrix()).translate((float) dx, (float) dy, (float) dz)
                    .rotate(camera.getRotation()).scale(scale, -scale, scale);
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
            VISIBLE_CLASSIC.removeIf(prepared -> prepared.stack != null && !prepared.stack.near);
            for (PresetBatch batch : PRESETS.values()) {
                for (PreparedWaypoint prepared : batch.visible) {
                    SmartStack stack = prepared.stack;
                    if (stack != null && !stack.near) {
                        if (stack.representative != prepared) { prepared.hidden = true; continue; }
                        prepared.compact = true;
                        updateSmartLabel(prepared, stack.nearest, stack.members, client, settings);
                    }
                }
                batch.visible.removeIf(prepared -> prepared.hidden);
                for (PreparedWaypoint prepared : batch.visible) {
                    if (!prepared.compact && !prepared.updated) prepared.label.update(client.textRenderer, settings, prepared.distance);
                }
            }
        }
        TEXT_SHADERS.beginFrame();
        RenderCommandQueue panelQueue = buffers.getBatchingQueue(1);
        RenderCommandQueue iconQueue = buffers.getBatchingQueue(2);
        RenderCommandQueue textQueue = buffers.getBatchingQueue(3);
        for (PreparedWaypoint prepared : VISIBLE_CLASSIC) prepared.label.drawPanel(panelQueue);
        for (PresetBatch batch : PRESETS.values()) {
            for (PreparedWaypoint prepared : batch.visible) prepared.renderLabel().drawPanel(panelQueue);
        }

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
                batch.icon.draw(prepared.label.matrix, iconQueue, layout.iconX, layout.iconY, layout.iconScale);
            }
        }

        for (PreparedWaypoint prepared : VISIBLE_CLASSIC) prepared.label.drawText(client.textRenderer, TEXT_SHADERS, textQueue);
        for (PresetBatch batch : PRESETS.values()) {
            for (PreparedWaypoint prepared : batch.visible) prepared.renderLabel().drawText(client.textRenderer, TEXT_SHADERS, textQueue);
        }

    }

    private static void rebuildSmart(MinecraftClient client, WaypointSettings settings) {
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            SmartStack stack = prepared.stack;
            if (stack != null && stack.visibleMembers == stack.members) stack.rebuild = true;
        }
        SMART_CANDIDATES.removeIf(prepared -> prepared.stack != null && !prepared.stack.rebuild);
        for (PreparedWaypoint prepared : cachedWaypoints) {
            if (prepared.stack != null && prepared.stack.rebuild) prepared.stack = null;
        }
        SMART_STACKS.removeIf(stack -> stack.rebuild);
        PreparedWaypoint[] candidatesByGroup = new PreparedWaypoint[SMART_CANDIDATES.size()];
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            prepared.group = SMART_GROUPS.add(prepared.screenLeft, prepared.screenTop,
                    prepared.screenRight, prepared.screenBottom, Math.sqrt(prepared.playerDistanceSquared),
                    prepared.batch != null && prepared.batch.preset.icon);
            if (prepared.group >= 0) candidatesByGroup[prepared.group] = prepared;
        }
        SMART_CANDIDATES.removeIf(prepared -> prepared.group < 0);
        SMART_GROUPS.finish();
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            if (!SMART_GROUPS.collapsed(prepared.group) || !SMART_GROUPS.representative(prepared.group)) continue;
            updateSmartLabel(prepared, candidatesByGroup[SMART_GROUPS.nearestIndex(prepared.group)],
                    SMART_GROUPS.groupSize(prepared.group), client, settings);
            WaypointLabel label = prepared.smartLabel;
            if (PROJECTION.project(label.matrix, label.left(), label.top(), label.right() - label.left(), label.bottom() - label.top())) {
                SMART_GROUPS.includeStackBounds(prepared.group, PROJECTION.left, PROJECTION.top, PROJECTION.right, PROJECTION.bottom);
            }
        }
        SMART_GROUPS.finish();
        SmartStack[] stacks = new SmartStack[candidatesByGroup.length];
        for (PreparedWaypoint prepared : SMART_CANDIDATES) {
            if (!SMART_GROUPS.collapsed(prepared.group)) continue;
            int representative = SMART_GROUPS.representativeIndex(prepared.group);
            if (stacks[representative] == null) {
                stacks[representative] = new SmartStack(candidatesByGroup[representative],
                        candidatesByGroup[SMART_GROUPS.nearestIndex(prepared.group)]);
                SMART_STACKS.add(stacks[representative]);
            }
            prepared.stack = stacks[representative];
            prepared.stack.members++;
        }
    }

    private static void updateSmartLabel(PreparedWaypoint prepared, PreparedWaypoint nearest, int count,
                                         MinecraftClient client, WaypointSettings settings) {
        String name = nearest.waypoint.name() + " (" + count + ")";
        if (prepared.smartLabel == null || !name.equals(prepared.smartLabelName)) {
            Waypoint original = nearest.waypoint;
            Waypoint grouped = new Waypoint(original.id(), name, original.serverKey(), original.profile(),
                    original.dimension(), original.x(), original.y(), original.z(), original.colorArgb());
            DisplayTarget target = nearest.target;
            prepared.smartLabel = new WaypointLabel(grouped, target.x, target.y, target.z, SMART_PRESET);
            prepared.smartLabelName = name;
        }
        prepared.smartLabel.update(client.textRenderer, settings, nearest.distance);
        prepared.smartLabel.matrix.set(prepared.label.matrix);
    }

    private static void collectSmart(PreparedWaypoint prepared) {
        WaypointPreset preset = prepared.batch == null ? null : prepared.batch.preset;
        boolean hasIcon = preset != null && preset.icon;
        float left = hasIcon ? preset.iconX : prepared.label.left();
        float top = hasIcon ? preset.iconY : prepared.label.top();
        float width = hasIcon ? 16 * preset.iconScale : prepared.label.right() - left;
        float height = hasIcon ? 16 * preset.iconScale : prepared.label.bottom() - top;
        if (!PROJECTION.project(prepared.label.matrix, left, top, width, height)) return;
        prepared.screenLeft = PROJECTION.left; prepared.screenTop = PROJECTION.top;
        prepared.screenRight = PROJECTION.right; prepared.screenBottom = PROJECTION.bottom;
        SMART_CANDIDATES.add(prepared);
        if (prepared.stack != null && SMART_GROUPS.visibleBounds(PROJECTION.left, PROJECTION.top,
                PROJECTION.right, PROJECTION.bottom)) prepared.stack.visibleMembers++;
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
            DisplayTarget target = new DisplayTarget(waypoint.x() * scale, waypoint.y(), waypoint.z() * scale);
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

    private static void drawLaser(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                  Vec3d cameraPos, DisplayTarget target, int waypointColor, int bottomY, int topY) {
        int color = 0xB0000000 | (waypointColor & 0x00FFFFFF);
        float bottom = (float)(bottomY - cameraPos.y);
        float top = (float)(topY - cameraPos.y);
        float halfWidth = LaserVisibility.HALF_WIDTH;
        matrices.push();
        matrices.translate(target.x - cameraPos.x, 0.0, target.z - cameraPos.z);
        queue.getBatchingQueue(0).submitCustom(matrices, RenderLayers.debugQuads(), (entry, vertices) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            vertices.vertex(matrix, -halfWidth, bottom, 0).color(color);
            vertices.vertex(matrix, -halfWidth, top, 0).color(color);
            vertices.vertex(matrix, halfWidth, top, 0).color(color);
            vertices.vertex(matrix, halfWidth, bottom, 0).color(color);
            vertices.vertex(matrix, 0, bottom, -halfWidth).color(color);
            vertices.vertex(matrix, 0, top, -halfWidth).color(color);
            vertices.vertex(matrix, 0, top, halfWidth).color(color);
            vertices.vertex(matrix, 0, bottom, halfWidth).color(color);
        });
        matrices.pop();
    }

    private record DisplayTarget(double x, double y, double z) { }
    private static final class PreparedWaypoint {
        final Waypoint waypoint;
        final DisplayTarget target;
        final PresetBatch batch;
        final WaypointLabel label;
        WaypointLabel smartLabel;
        String smartLabelName;
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
        final PreparedWaypoint representative;
        final PreparedWaypoint nearest;
        boolean near, rebuild;
        int members, visibleMembers;

        SmartStack(PreparedWaypoint representative, PreparedWaypoint nearest) {
            this.representative = representative;
            this.nearest = nearest;
        }
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
