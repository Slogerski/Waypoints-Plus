package pl.slogerski.waypointsplus.fabric;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

final class WaypointProjection {
    private final Matrix4f viewProjection = new Matrix4f();
    private final Matrix4f transform = new Matrix4f();
    private final Vector4f point = new Vector4f();
    private float halfWidth, halfHeight;
    float left, top, right, bottom;

    void begin(Matrix4fc projection, Matrix4fc modelView, int width, int height) {
        viewProjection.set(projection).mul(modelView);
        halfWidth = width * 0.5f;
        halfHeight = height * 0.5f;
    }

    boolean project(Matrix4fc model, float x, float y, float width, float height) {
        transform.set(viewProjection).mul(model);
        left = top = Float.POSITIVE_INFINITY;
        right = bottom = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            point.set(x + (corner & 1) * width, y + (corner >> 1) * height, 0, 1).mul(transform);
            if (!Float.isFinite(point.w) || point.w <= 0.001f) return false;
            float screenX = (point.x / point.w + 1) * halfWidth;
            float screenY = (1 - point.y / point.w) * halfHeight;
            if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) return false;
            left = Math.min(left, screenX); top = Math.min(top, screenY);
            right = Math.max(right, screenX); bottom = Math.max(bottom, screenY);
        }
        return right > left && bottom > top;
    }
}
