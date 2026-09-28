package pl.slogerski.waypointsplus.fabric;

import java.util.Arrays;

final class SmartWaypointGroups {
    private static final int CELL_SIZE = 16;
    private static final int CELL_SLOTS = 4;
    private static final float MAX_ICON_SIZE = 96;
    private static final int MAX_STACK_CELLS = 256;
    private int columns, rows, generation, count;
    private int[] cells = new int[0], stamps = new int[0], protectedCells = new int[0];
    private int[] parents = new int[0], sizes = new int[0], nearest = new int[0];
    private int[] nearestIcon = new int[0], iconCounts = new int[0];
    private boolean[] close = new boolean[0];
    private float[] left = new float[0], top = new float[0], right = new float[0], bottom = new float[0];
    private float[] stackLeft = new float[0], stackTop = new float[0], stackRight = new float[0], stackBottom = new float[0];
    private double[] distances = new double[0];
    private float largeNearLeft, largeNearTop, largeNearRight, largeNearBottom;
    private double minimumDistance;

    void begin(int width, int height, int capacity, double minimumDistance) {
        this.minimumDistance = minimumDistance;
        int nextColumns = (Math.min(8192, Math.max(1, width)) + CELL_SIZE - 1) / CELL_SIZE;
        int nextRows = (Math.min(8192, Math.max(1, height)) + CELL_SIZE - 1) / CELL_SIZE;
        if (columns != nextColumns || rows != nextRows) {
            columns = nextColumns; rows = nextRows;
            int cellCount = columns * rows;
            if (stamps.length < cellCount) {
                stamps = new int[cellCount];
                protectedCells = new int[cellCount];
                cells = new int[cellCount * CELL_SLOTS];
            }
        }
        if (++generation == 0) {
            Arrays.fill(stamps, 0); Arrays.fill(protectedCells, 0); generation = 1;
        }
        if (parents.length < capacity) {
            int size = Math.max(capacity, Math.max(16, parents.length * 2));
            parents = new int[size]; sizes = new int[size]; nearest = new int[size];
            nearestIcon = new int[size]; iconCounts = new int[size]; close = new boolean[size];
            left = new float[size]; top = new float[size]; right = new float[size]; bottom = new float[size];
            stackLeft = new float[size]; stackTop = new float[size]; stackRight = new float[size]; stackBottom = new float[size];
            distances = new double[size];
        }
        count = 0;
        largeNearLeft = largeNearTop = Float.POSITIVE_INFINITY;
        largeNearRight = largeNearBottom = Float.NEGATIVE_INFINITY;
    }

    boolean visibleBounds(float l, float t, float r, float b) {
        return Float.isFinite(l) && Float.isFinite(t) && Float.isFinite(r) && Float.isFinite(b)
                && r > l && b > t && r > 0 && b > 0 && l < columns * CELL_SIZE && t < rows * CELL_SIZE
                && r - l <= MAX_ICON_SIZE && b - t <= MAX_ICON_SIZE;
    }

    int add(float l, float t, float r, float b, double distance) {
        return add(l, t, r, b, distance, true);
    }

    int add(float l, float t, float r, float b, double distance, boolean hasIcon) {
        if (!Float.isFinite(l) || !Float.isFinite(t) || !Float.isFinite(r) || !Float.isFinite(b)
                || !Double.isFinite(distance) || distance < 0 || r <= l || b <= t
                || r <= 0 || b <= 0 || l >= columns * CELL_SIZE || t >= rows * CELL_SIZE) return -1;
        if (r - l > MAX_ICON_SIZE || b - t > MAX_ICON_SIZE) {
            if (distance <= minimumDistance) {
                largeNearLeft = Math.min(largeNearLeft, l); largeNearTop = Math.min(largeNearTop, t);
                largeNearRight = Math.max(largeNearRight, r); largeNearBottom = Math.max(largeNearBottom, b);
            }
            return -1;
        }
        int x0 = Math.max(0, (int)Math.floor(l / CELL_SIZE));
        int y0 = Math.max(0, (int)Math.floor(t / CELL_SIZE));
        int x1 = Math.min(columns - 1, (int)Math.floor(r / CELL_SIZE));
        int y1 = Math.min(rows - 1, (int)Math.floor(b / CELL_SIZE));
        if (distance <= minimumDistance) {
            for (int y = y0; y <= y1; y++) Arrays.fill(protectedCells, y * columns + x0, y * columns + x1 + 1, generation);
        }
        if (count >= parents.length) return -1;
        int id = count++;
        parents[id] = id; sizes[id] = 1; nearest[id] = id;
        nearestIcon[id] = hasIcon ? id : -1; iconCounts[id] = hasIcon ? 1 : 0;
        close[id] = distance <= minimumDistance;
        left[id] = l; top[id] = t; right[id] = r; bottom[id] = b; distances[id] = distance;
        stackLeft[id] = l; stackTop[id] = t; stackRight[id] = r; stackBottom[id] = b;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int cell = y * columns + x, offset = cell * CELL_SLOTS;
                if (stamps[cell] != generation) {
                    stamps[cell] = generation;
                    Arrays.fill(cells, offset, offset + CELL_SLOTS, -1);
                }
                int free = -1, farthest = offset;
                for (int slot = offset; slot < offset + CELL_SLOTS; slot++) {
                    int other = cells[slot];
                    if (other < 0) { if (free < 0) free = slot; continue; }
                    if (l < right[other] && r > left[other] && t < bottom[other] && b > top[other]) union(id, other);
                    if (distances[other] > distances[cells[farthest]]) farthest = slot;
                }
                if (free >= 0) cells[free] = id;
                else if (distance < distances[cells[farthest]]) cells[farthest] = id;
            }
        }
        return id;
    }

    void finish() {
        joinStacks();
        joinStacks();
        for (int id = 0; id < count; id++) {
            int root = root(id);
            if (close[root] || sizes[root] < 3 || iconCounts[root] == 0) continue;
            if (left[id] < largeNearRight && right[id] > largeNearLeft
                    && top[id] < largeNearBottom && bottom[id] > largeNearTop) { close[root] = true; continue; }
            int x0 = Math.max(0, (int)Math.floor(left[id] / CELL_SIZE));
            int y0 = Math.max(0, (int)Math.floor(top[id] / CELL_SIZE));
            int x1 = Math.min(columns - 1, (int)Math.floor(right[id] / CELL_SIZE));
            int y1 = Math.min(rows - 1, (int)Math.floor(bottom[id] / CELL_SIZE));
            for (int y = y0; y <= y1 && !close[root]; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (protectedCells[y * columns + x] == generation) { close[root] = true; break; }
                }
            }
        }
    }

    boolean collapsed(int id) {
        int root = root(id);
        return sizes[root] >= 3 && iconCounts[root] > 0 && !close[root];
    }

    boolean representative(int id) {
        return representativeIndex(id) == id;
    }

    int representativeIndex(int id) {
        return nearestIcon[root(id)];
    }

    int nearestIndex(int id) { return nearest[root(id)]; }

    int groupSize(int id) { return sizes[root(id)]; }

    void includeStackBounds(int id, float l, float t, float r, float b) {
        if (!Float.isFinite(l) || !Float.isFinite(t) || !Float.isFinite(r) || !Float.isFinite(b) || r <= l || b <= t) return;
        int root = root(id);
        stackLeft[root] = Math.min(stackLeft[root], l); stackTop[root] = Math.min(stackTop[root], t);
        stackRight[root] = Math.max(stackRight[root], r); stackBottom[root] = Math.max(stackBottom[root], b);
    }

    private void joinStacks() {
        Arrays.fill(stamps, 0);
        for (int id = 0; id < count; id++) {
            if (parents[id] != id || sizes[id] < 3 || close[id] || iconCounts[id] == 0) continue;
            int x0 = Math.max(0, (int)Math.floor(stackLeft[id] / CELL_SIZE));
            int y0 = Math.max(0, (int)Math.floor(stackTop[id] / CELL_SIZE));
            int x1 = Math.min(columns - 1, (int)Math.floor(stackRight[id] / CELL_SIZE));
            int y1 = Math.min(rows - 1, (int)Math.floor(stackBottom[id] / CELL_SIZE));
            if ((x1 - x0 + 1) * (y1 - y0 + 1) > MAX_STACK_CELLS) continue;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int cell = y * columns + x, offset = cell * CELL_SLOTS;
                    if (stamps[cell] != generation) {
                        stamps[cell] = generation;
                        Arrays.fill(cells, offset, offset + CELL_SLOTS, -1);
                    }
                    int free = -1, farthest = offset;
                    for (int slot = offset; slot < offset + CELL_SLOTS; slot++) {
                        int other = cells[slot];
                        if (other < 0) { if (free < 0) free = slot; continue; }
                        if (distances[nearest[other]] > distances[nearest[cells[farthest]]]) farthest = slot;
                    }
                    if (free >= 0) cells[free] = id;
                    else if (distances[nearest[id]] < distances[nearest[cells[farthest]]]) cells[farthest] = id;
                }
            }
        }
        for (int id = 0; id < count; id++) {
            boolean isStack = parents[id] == id && sizes[id] >= 3 && !close[id] && iconCounts[id] > 0;
            float l = isStack ? stackLeft[id] : left[id], t = isStack ? stackTop[id] : top[id];
            float r = isStack ? stackRight[id] : right[id], b = isStack ? stackBottom[id] : bottom[id];
            int x0 = Math.max(0, (int)Math.floor(l / CELL_SIZE));
            int y0 = Math.max(0, (int)Math.floor(t / CELL_SIZE));
            int x1 = Math.min(columns - 1, (int)Math.floor(r / CELL_SIZE));
            int y1 = Math.min(rows - 1, (int)Math.floor(b / CELL_SIZE));
            if ((x1 - x0 + 1) * (y1 - y0 + 1) > MAX_STACK_CELLS) continue;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int cell = y * columns + x;
                    if (stamps[cell] != generation) continue;
                    int offset = cell * CELL_SLOTS;
                    for (int slot = offset; slot < offset + CELL_SLOTS; slot++) {
                        if (cells[slot] < 0) continue;
                        int stack = root(cells[slot]);
                        if (l < stackRight[stack] && r > stackLeft[stack]
                                && t < stackBottom[stack] && b > stackTop[stack]) union(id, stack);
                    }
                }
            }
        }
    }

    private int root(int id) {
        while (id != parents[id]) {
            parents[id] = parents[parents[id]];
            id = parents[id];
        }
        return id;
    }

    private void union(int a, int b) {
        a = root(a); b = root(b);
        if (a == b) return;
        if (sizes[a] < sizes[b]) { int swap = a; a = b; b = swap; }
        parents[b] = a; sizes[a] += sizes[b]; close[a] |= close[b];
        iconCounts[a] += iconCounts[b];
        stackLeft[a] = Math.min(stackLeft[a], stackLeft[b]); stackTop[a] = Math.min(stackTop[a], stackTop[b]);
        stackRight[a] = Math.max(stackRight[a], stackRight[b]); stackBottom[a] = Math.max(stackBottom[a], stackBottom[b]);
        int first = nearest[a], second = nearest[b];
        if (distances[second] < distances[first] || (distances[second] == distances[first] && second < first)) {
            nearest[a] = second;
        }
        int firstIcon = nearestIcon[a], secondIcon = nearestIcon[b];
        if (firstIcon < 0 || (secondIcon >= 0 && (distances[secondIcon] < distances[firstIcon]
                || (distances[secondIcon] == distances[firstIcon] && secondIcon < firstIcon)))) {
            nearestIcon[a] = secondIcon;
        }
    }

    void clear() {
        columns = rows = generation = count = 0;
        cells = stamps = protectedCells = parents = sizes = nearest = nearestIcon = iconCounts = new int[0];
        close = new boolean[0];
        left = top = right = bottom = new float[0]; distances = new double[0];
        stackLeft = stackTop = stackRight = stackBottom = new float[0];
    }
}
