package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class WaypointOverlayCheck {
    private WaypointOverlayCheck() {
    }

    public static void main(String[] args) {
        for (int frame = 0; frame < 100; frame++) {
            verifyPhase(false, frame);
            verifyPhase(true, frame);
        }
        System.out.println("Waypoint overlay checks passed.");
    }

    private static void verifyPhase(boolean improvedTransparency, int frame) {
        SubmitNodeStorage storage = new SubmitNodeStorage();
        storage.setUseImprovedTransparency(improvedTransparency);
        List<WaypointOverlayRenderer.Submit> panels = new ArrayList<>();
        List<WaypointOverlayRenderer.Submit> icons = new ArrayList<>();
        List<WaypointOverlayRenderer.Submit> texts = new ArrayList<>();
        for (int waypoint = 0; waypoint < 20; waypoint++) {
            Matrix4f matrix = labelMatrix(frame, waypoint);
            panels.add(geometry(matrix));
            icons.add(geometry(matrix));
            texts.add(text(matrix, 1, -40, -3));
            texts.add(text(matrix, 0.8f, -12, 16));
            texts.add(text(matrix, 0.6f, -67, 40));
        }
        List<WaypointOverlayRenderer.Submit> expected = new ArrayList<>(panels);
        expected.addAll(icons);
        expected.addAll(texts);
        submit(storage, 1, panels);
        submit(storage, 2, icons);
        submit(storage, 3, texts);
        require(!storage.seeThrough().isEmpty(), "Waypoints must use the see-through phase");
        for (var collection : storage.getSubmitsPerOrder().values()) {
            require(collection.alwaysOnTopGizmos.isEmpty(), "Waypoints must not request a depth-clearing phase");
        }
        List<SubmitNode> actual = new ArrayList<>();
        storage.seeThrough().sortInto((submit, ordered) -> actual.add(submit));
        require(actual.size() == expected.size(), "All label parts must be retained");
        for (int i = 0; i < expected.size(); i++) {
            require(actual.get(i) == expected.get(i), "Panel, icon, and text order must be preserved");
        }
        require(storage.seeThrough().isEmpty(), "Submitted geometry must be released after sorting");
    }

    private static void submit(SubmitNodeStorage storage, int order, List<WaypointOverlayRenderer.Submit> submits) {
        for (var submit : submits) {
            require(submit.distanceToCameraSq() == 0, "Waypoint order must not depend on camera or text transforms");
            SubmitRenderPhases.SEE_THROUGH_NAME_TAGS.submit(storage.order(order), submit);
        }
    }

    private static WaypointOverlayRenderer.GeometrySubmit geometry(Matrix4f matrix) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(matrix);
        return new WaypointOverlayRenderer.GeometrySubmit(pose.last().copy(), null, (entry, vertices) -> {});
    }

    private static WaypointOverlayRenderer.TextSubmit text(Matrix4f matrix, float scale, float x, float y) {
        return new WaypointOverlayRenderer.TextSubmit(new Matrix4f(matrix).translate(x, y, 0).scale(scale, scale, 1),
                FormattedCharSequence.EMPTY, -1);
    }

    private static Matrix4f labelMatrix(int frame, int waypoint) {
        Vector3f position = new Vector3f(1000 + waypoint - frame * 0.1f, 1.5f, 300);
        if (waypoint != 0) position.normalize(24);
        else position.normalize(5);
        return new Matrix4f().rotateY(frame * 0.07f).rotateX(frame * 0.03f)
                .translate(position).scale(0.06f, -0.06f, 0.06f);
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
