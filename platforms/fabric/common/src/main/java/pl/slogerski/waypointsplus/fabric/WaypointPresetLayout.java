package pl.slogerski.waypointsplus.fabric;

import java.util.Arrays;

final class WaypointPresetLayout {
    static final int BORDER = 5;
    private final float[][] start = new float[2][6];
    private final float[][] size = new float[2][6];
    private final byte[][] state = new byte[2][6];
    private final boolean[] active = new boolean[6];

    void set(int part, float x, float y, float width, float height, boolean enabled) {
        start[0][part] = x;
        start[1][part] = y;
        size[0][part] = width;
        size[1][part] = height;
        active[part] = enabled;
    }

    void resolve(WaypointPreset preset) {
        active[BORDER] = true;
        for (int axis = 0; axis < 2; axis++) {
            Arrays.fill(state[axis], (byte) 0);
            resolve(preset, BORDER, axis);
            for (int part = 0; part < BORDER; part++) resolve(preset, part, axis);
        }
    }

    private void resolve(WaypointPreset preset, int part, int axis) {
        if (state[axis][part] != 0) return;
        state[axis][part] = 1;
        if (part == BORDER) {
            float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < BORDER; i++) {
                if (!active[i] || !preset.linked(i)) continue;
                resolve(preset, i, axis);
                minimum = Math.min(minimum, start[axis][i]);
                maximum = Math.max(maximum, start[axis][i] + size[axis][i]);
            }
            if (!Float.isFinite(minimum)) {
                minimum = axis == 0 ? -20 : -5;
                maximum = axis == 0 ? 20 : 5;
            }
            start[axis][part] = (float) Math.floor(minimum - preset.padding);
            size[axis][part] = (float) Math.ceil(maximum + preset.padding) - start[axis][part];
        } else {
            WaypointPreset.Anchor anchor = preset.anchor(part, axis);
            if (active[part] && anchor != null && anchor.valid(axis) && active[anchor.target]
                    && state[axis][anchor.target] != 1) {
                resolve(preset, anchor.target, axis);
                start[axis][part] = start[axis][anchor.target]
                        + ((anchor.edge & 1) == 0 ? 0 : size[axis][anchor.target]) + anchor.offset
                        - ((anchor.ownEdge & 1) == 0 ? 0 : size[axis][part]);
            }
        }
        state[axis][part] = 2;
    }

    float left(int part) { return start[0][part]; }
    float top(int part) { return start[1][part]; }
    float right(int part) { return start[0][part] + size[0][part]; }
    float bottom(int part) { return start[1][part] + size[1][part]; }
    float centerX(int part) { return start[0][part] + size[0][part] / 2; }

    float edge(int part, int edge) {
        int axis = edge / 2;
        return start[axis][part] + ((edge & 1) == 0 ? 0 : size[axis][part]);
    }

    int sourceEdge(int part, double x, double y, float zoom) {
        double closest = Math.min(3 / zoom, Math.min(right(part) - left(part), bottom(part) - top(part)) / 5);
        int result = -1;
        for (int edge = 0; edge < 4; edge++) {
            double distance = edgeDistance(part, edge, x, y, 0);
            if (distance < closest) { closest = distance; result = edge; }
        }
        return result;
    }

    int targetEdge(WaypointPreset preset, int part, int ownEdge, double x, double y, float zoom) {
        double closest = 5 / zoom;
        int result = -1;
        for (int target = 0; target <= BORDER; target++) {
            if (!active[target] || (target == BORDER && !preset.border && !preset.background)) continue;
            for (int edge = ownEdge / 2 * 2; edge < ownEdge / 2 * 2 + 2; edge++) {
                if (!canAttach(preset, part, ownEdge, target, edge)) continue;
                double distance = edgeDistance(target, edge, x, y, 3 / zoom);
                if (distance < closest) { closest = distance; result = target * 4 + edge; }
            }
        }
        return result;
    }

    private double edgeDistance(int part, int edge, double x, double y, double padding) {
        boolean vertical = edge < 2;
        double along = vertical ? y : x;
        double from = vertical ? top(part) : left(part), to = vertical ? bottom(part) : right(part);
        if (along < from - padding || along > to + padding) return Double.POSITIVE_INFINITY;
        return Math.abs((vertical ? x : y) - edge(part, edge));
    }

    static boolean valid(WaypointPreset preset) {
        for (int axis = 0; axis < 2; axis++) {
            for (int part = 0; part < BORDER; part++) {
                WaypointPreset.Anchor anchor = preset.anchor(part, axis);
                if (anchor != null && (!anchor.valid(axis) || anchor.target == part)) return false;
            }
            for (int part = 0; part <= BORDER; part++) {
                if (cycle(preset, part, axis, 0, -1, null, -1)) return false;
            }
        }
        return true;
    }

    static boolean canAttach(WaypointPreset preset, int part, int ownEdge, int target, int edge) {
        if (part < 0 || part >= BORDER || target < 0 || target > BORDER || target == part
                || ownEdge < 0 || ownEdge > 3 || edge < 0 || edge > 3 || ownEdge / 2 != edge / 2) return false;
        WaypointPreset.Anchor anchor = new WaypointPreset.Anchor(target, edge, ownEdge, 0);
        int axis = ownEdge / 2;
        for (int i = 0; i <= BORDER; i++) {
            if (cycle(preset, i, axis, 0, part, anchor, target == BORDER ? part : -1)) return false;
        }
        return true;
    }

    private static boolean cycle(WaypointPreset preset, int part, int axis, int path,
                                 int edited, WaypointPreset.Anchor replacement, int unlinked) {
        int bit = 1 << part;
        if ((path & bit) != 0) return true;
        path |= bit;
        if (part == BORDER) {
            for (int i = 0; i < BORDER; i++) {
                if (i != unlinked && preset.linked(i) && cycle(preset, i, axis, path, edited, replacement, unlinked)) return true;
            }
            return false;
        }
        WaypointPreset.Anchor anchor = part == edited ? replacement : preset.anchor(part, axis);
        return anchor != null && (!anchor.valid(axis)
                || cycle(preset, anchor.target, axis, path, edited, replacement, unlinked));
    }

    boolean attach(WaypointPreset preset, int part, int ownEdge, int target, int edge) {
        if (!canAttach(preset, part, ownEdge, target, edge)) return false;
        float sourcePosition = edge(part, ownEdge);
        boolean linked = preset.linked(part);
        if (target == BORDER) {
            preset.setLinked(part, false);
            resolve(preset);
        }
        float offset = sourcePosition - edge(target, edge);
        if (!WaypointPreset.position(offset)) {
            preset.setLinked(part, linked);
            return false;
        }
        preset.setAnchor(part, ownEdge / 2, new WaypointPreset.Anchor(target, edge, ownEdge, offset));
        return true;
    }

    boolean detach(WaypointPreset preset, int part, int axis) {
        if (preset.anchor(part, axis) == null) return true;
        float position = axis == 0 ? (part == 4 ? left(part) : centerX(part)) : top(part);
        if (!WaypointPreset.position(position)) return false;
        preset.setAnchor(part, axis, null);
        if (part == 4) {
            if (axis == 0) preset.iconX = left(part); else preset.iconY = top(part);
        } else {
            if (axis == 0) preset.part(part).x = centerX(part); else preset.part(part).y = top(part);
        }
        return true;
    }

    static float position(WaypointPreset preset, int part, int axis) {
        WaypointPreset.Anchor anchor = preset.anchor(part, axis);
        if (anchor != null) return anchor.offset;
        return part == 4 ? (axis == 0 ? preset.iconX : preset.iconY)
                : (axis == 0 ? preset.part(part).x : preset.part(part).y);
    }

    static void position(WaypointPreset preset, int part, int axis, float value) {
        WaypointPreset.Anchor anchor = preset.anchor(part, axis);
        if (anchor != null) anchor.offset = value;
        else if (part == 4) {
            if (axis == 0) preset.iconX = value; else preset.iconY = value;
        } else {
            if (axis == 0) preset.part(part).x = value; else preset.part(part).y = value;
        }
    }
}
