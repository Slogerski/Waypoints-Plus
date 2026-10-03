package pl.slogerski.waypointsplus.core;

import java.util.Arrays;

public final class WaypointGrouping {
    private static final int CELL_SIZE = 32;
    private static final int MAX_CELLS_PER_WAYPOINT = 256;
    private static final int MAX_CHAIN_DEPTH = 3;
    private static final float GROUP_RADIUS = 96;
    private int columns, rows, count, nodeCount, largeHead, stamp;
    private double minimumDistanceSquared;
    private int[] cellHeads = new int[0];
    private int[] groups = new int[0], sizes = new int[0], icons = new int[0];
    private int[] members = new int[0];
    private int[] directSeeds = new int[0], directCount = new int[0];
    private int[] order = new int[0], sorted = new int[0], queue = new int[0], depth = new int[0];
    private int[] firstNode = new int[0], nodeLength = new int[0], seen = new int[0];
    private int[] largeNext = new int[0], largePrevious = new int[0];
    private int[] nodeOwners = new int[0], nodeCells = new int[0], nodeNext = new int[0], nodePrevious = new int[0];
    private float[] left = new float[0], top = new float[0], right = new float[0], bottom = new float[0];
    private float[] centerX = new float[0], centerY = new float[0];
    private float[] memberLeft = new float[0], memberTop = new float[0], memberRight = new float[0], memberBottom = new float[0];
    private float[] groupLeft = new float[0], groupTop = new float[0], groupRight = new float[0], groupBottom = new float[0];
    private double[] distances = new double[0];
    private boolean[] hasIcon = new boolean[0], dense = new boolean[0];
    private boolean[] retainedDense = new boolean[0];

    public void begin(int width, int height, int capacity, double minimumDistance) {
        columns = (Math.min(8192, Math.max(1, width)) + CELL_SIZE - 1) / CELL_SIZE;
        rows = (Math.min(8192, Math.max(1, height)) + CELL_SIZE - 1) / CELL_SIZE;
        if (cellHeads.length < columns * rows) cellHeads = new int[columns * rows];
        minimumDistanceSquared = minimumDistance * minimumDistance;
        if (groups.length < capacity) {
            int size = Math.max(capacity, Math.max(16, groups.length * 2));
            groups = new int[size]; sizes = new int[size]; icons = new int[size];
            members = new int[size];
            directSeeds = new int[size * 3]; directCount = new int[size];
            order = new int[size]; sorted = new int[size]; queue = new int[size + 1]; depth = new int[size];
            firstNode = new int[size]; nodeLength = new int[size]; seen = new int[size];
            largeNext = new int[size]; largePrevious = new int[size];
            left = new float[size]; top = new float[size]; right = new float[size]; bottom = new float[size];
            centerX = new float[size]; centerY = new float[size];
            memberLeft = new float[size]; memberTop = new float[size]; memberRight = new float[size]; memberBottom = new float[size];
            groupLeft = new float[size]; groupTop = new float[size]; groupRight = new float[size]; groupBottom = new float[size];
            distances = new double[size]; hasIcon = new boolean[size]; dense = new boolean[size];
            retainedDense = new boolean[size];
        }
        count = 0;
    }

    public boolean visibleBounds(float l, float t, float r, float b) {
        return Float.isFinite(l) && Float.isFinite(t) && Float.isFinite(r) && Float.isFinite(b)
                && r > l && b > t && r > 0 && b > 0 && l < columns * CELL_SIZE && t < rows * CELL_SIZE;
    }

    public int add(float l, float t, float r, float b, double distanceSquared, boolean icon) {
        return add(l, t, r, b, distanceSquared, icon, 1, false);
    }

    public int add(float l, float t, float r, float b, double distanceSquared, boolean icon,
                   int memberCount, boolean denseGroup) {
        if (!visibleBounds(l, t, r, b) || !Double.isFinite(distanceSquared)
                || distanceSquared <= minimumDistanceSquared || memberCount < 1 || count >= groups.length) return -1;
        int id = count++;
        left[id] = l - 1; top[id] = t - 1; right[id] = r + 1; bottom[id] = b + 1;
        centerX[id] = l * 0.5f + r * 0.5f; centerY[id] = t * 0.5f + b * 0.5f;
        distances[id] = distanceSquared; hasIcon[id] = icon;
        members[id] = memberCount; retainedDense[id] = denseGroup;
        memberLeft[id] = memberRight[id] = centerX[id];
        memberTop[id] = memberBottom[id] = centerY[id];
        return id;
    }

    public void memberBounds(int id, float l, float t, float r, float b) {
        if (id < 0 || id >= count || !Float.isFinite(l) || !Float.isFinite(t)
                || !Float.isFinite(r) || !Float.isFinite(b) || r < l || b < t) return;
        memberLeft[id] = l; memberTop[id] = t; memberRight[id] = r; memberBottom[id] = b;
    }

    public void finish() {
        Arrays.fill(groups, 0, count, -1);
        Arrays.fill(sizes, 0, count, 0);
        Arrays.fill(directCount, 0, count, 0);
        Arrays.fill(dense, 0, count, false);
        Arrays.fill(icons, 0, count, -1);
        Arrays.fill(cellHeads, 0, columns * rows, -1);
        nodeCount = 0; largeHead = -1;
        for (int id = 0; id < count; id++) { order[id] = id; index(id); }
        sort();
        for (int position = 0; position < count; position++) {
            int anchor = order[position];
            if (groups[anchor] >= 0) continue;
            int head = 0, tail = 1;
            boolean rescanned = false;
            queue[0] = anchor; depth[anchor] = 0;
            assign(anchor, anchor);
            while (head < tail) {
                int current = queue[head++];
                if (depth[current] >= MAX_CHAIN_DEPTH) continue;
                float l = Math.max(left[current], centerX[anchor] - GROUP_RADIUS);
                float t = Math.max(top[current], centerY[anchor] - GROUP_RADIUS);
                float r = Math.min(right[current], centerX[anchor] + GROUP_RADIUS);
                float b = Math.min(bottom[current], centerY[anchor] + GROUP_RADIUS);
                if (r <= l || b <= t) continue;
                if (++stamp == 0) { Arrays.fill(seen, 0); stamp = 1; }
                int x0 = cellX(l), y0 = cellY(t), x1 = cellX(r), y1 = cellY(b);
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        for (int node = cellHeads[y * columns + x]; node >= 0;) {
                            int next = nodeNext[node], other = nodeOwners[node];
                            if (seen[other] != stamp) {
                                seen[other] = stamp;
                                if (joins(anchor, current, other)) {
                                    depth[other] = depth[current] + 1;
                                    queue[tail++] = other;
                                    assign(other, anchor);
                                }
                            }
                            node = next;
                        }
                    }
                }
                for (int other = largeHead; other >= 0;) {
                    int next = largeNext[other];
                    if (joins(anchor, current, other)) {
                        depth[other] = depth[current] + 1;
                        queue[tail++] = other;
                        assign(other, anchor);
                    }
                    other = next;
                }
                if (dense[anchor] && !rescanned) {
                    queue[tail++] = anchor;
                    rescanned = true;
                }
            }
        }
    }

    private boolean joins(int anchor, int current, int other) {
        return groups[other] < 0
                && Math.min(groupLeft[anchor], memberLeft[other]) >= centerX[anchor] - GROUP_RADIUS
                && Math.max(groupRight[anchor], memberRight[other]) <= centerX[anchor] + GROUP_RADIUS
                && Math.min(groupTop[anchor], memberTop[other]) >= centerY[anchor] - GROUP_RADIUS
                && Math.max(groupBottom[anchor], memberBottom[other]) <= centerY[anchor] + GROUP_RADIUS
                && overlaps(current, other)
                && (dense[anchor] || retainedDense[other] || sizes[anchor] + members[other] <= 4 || makesDense(anchor, other));
    }

    private boolean overlaps(int a, int b) {
        return left[a] < right[b] && right[a] > left[b] && top[a] < bottom[b] && bottom[a] > top[b];
    }

    private boolean makesDense(int anchor, int other) {
        if (!overlaps(anchor, other)) return false;
        for (int i = 0; i < directCount[anchor]; i++) {
            if (overlaps(directSeeds[anchor * 3 + i], other)) return true;
        }
        return false;
    }

    private void assign(int id, int anchor) {
        groups[id] = anchor;
        if (sizes[anchor] == 0) {
            groupLeft[anchor] = memberLeft[id]; groupTop[anchor] = memberTop[id];
            groupRight[anchor] = memberRight[id]; groupBottom[anchor] = memberBottom[id];
        } else {
            groupLeft[anchor] = Math.min(groupLeft[anchor], memberLeft[id]);
            groupTop[anchor] = Math.min(groupTop[anchor], memberTop[id]);
            groupRight[anchor] = Math.max(groupRight[anchor], memberRight[id]);
            groupBottom[anchor] = Math.max(groupBottom[anchor], memberBottom[id]);
        }
        sizes[anchor] += members[id];
        dense[anchor] |= retainedDense[id];
        if (!dense[anchor] && id != anchor && overlaps(anchor, id)) {
            if (makesDense(anchor, id)) dense[anchor] = true;
            if (directCount[anchor] < 3) directSeeds[anchor * 3 + directCount[anchor]++] = id;
        }
        if (hasIcon[id] && (icons[anchor] < 0 || before(id, icons[anchor]))) icons[anchor] = id;
        if (firstNode[id] < 0) {
            int previous = largePrevious[id], next = largeNext[id];
            if (previous < 0) largeHead = next;
            else largeNext[previous] = next;
            if (next >= 0) largePrevious[next] = previous;
        } else {
            for (int node = firstNode[id], end = node + nodeLength[id]; node < end; node++) {
                int previous = nodePrevious[node], next = nodeNext[node];
                if (previous < 0) cellHeads[nodeCells[node]] = next;
                else nodeNext[previous] = next;
                if (next >= 0) nodePrevious[next] = previous;
            }
        }
    }

    private void index(int id) {
        int x0 = cellX(left[id]), y0 = cellY(top[id]);
        int x1 = cellX(right[id]), y1 = cellY(bottom[id]);
        int length = (x1 - x0 + 1) * (y1 - y0 + 1);
        if (length > MAX_CELLS_PER_WAYPOINT) {
            firstNode[id] = -1;
            largePrevious[id] = -1; largeNext[id] = largeHead;
            if (largeHead >= 0) largePrevious[largeHead] = id;
            largeHead = id;
            return;
        }
        reserveNodes(nodeCount + length);
        firstNode[id] = nodeCount; nodeLength[id] = length;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int cell = y * columns + x, node = nodeCount++, next = cellHeads[cell];
                nodeOwners[node] = id; nodeCells[node] = cell;
                nodePrevious[node] = -1; nodeNext[node] = next;
                if (next >= 0) nodePrevious[next] = node;
                cellHeads[cell] = node;
            }
        }
    }

    private void reserveNodes(int capacity) {
        if (nodeOwners.length >= capacity) return;
        int size = Math.max(capacity, Math.max(64, nodeOwners.length * 2));
        nodeOwners = Arrays.copyOf(nodeOwners, size); nodeCells = Arrays.copyOf(nodeCells, size);
        nodeNext = Arrays.copyOf(nodeNext, size); nodePrevious = Arrays.copyOf(nodePrevious, size);
    }

    private int cellX(float value) { return Math.max(0, Math.min(columns - 1, (int) Math.floor(value / CELL_SIZE))); }
    private int cellY(float value) { return Math.max(0, Math.min(rows - 1, (int) Math.floor(value / CELL_SIZE))); }

    private boolean before(int a, int b) {
        return distances[a] < distances[b] || (distances[a] == distances[b] && a < b);
    }

    private void sort() {
        for (int step = 1; step < count; step *= 2) {
            for (int start = 0; start < count; start += step * 2) {
                int middle = Math.min(start + step, count), end = Math.min(start + step * 2, count);
                int a = start, b = middle;
                for (int target = start; target < end; target++) {
                    sorted[target] = b >= end || (a < middle && before(order[a], order[b])) ? order[a++] : order[b++];
                }
            }
            int[] swap = order; order = sorted; sorted = swap;
        }
    }

    public boolean collapsed(int id) {
        return id >= 0 && id < count && sizes[groups[id]] >= 3 && icons[groups[id]] >= 0;
    }

    public boolean representative(int id) { return representativeIndex(id) == id; }
    public int representativeIndex(int id) { return icons[groups[id]]; }
    public int nearestIndex(int id) { return groups[id]; }
    public int groupSize(int id) { return sizes[groups[id]]; }
    public boolean denseGroup(int id) { return dense[groups[id]]; }

    public void includeStackBounds(int id, float l, float t, float r, float b) {
        if (!Float.isFinite(l) || !Float.isFinite(t) || !Float.isFinite(r) || !Float.isFinite(b) || r <= l || b <= t) return;
        int anchor = groups[id];
        if (!dense[anchor]) return;
        left[id] = Math.min(left[id], Math.max(l, centerX[anchor] - GROUP_RADIUS));
        top[id] = Math.min(top[id], Math.max(t, centerY[anchor] - GROUP_RADIUS));
        right[id] = Math.max(right[id], Math.min(r, centerX[anchor] + GROUP_RADIUS));
        bottom[id] = Math.max(bottom[id], Math.min(b, centerY[anchor] + GROUP_RADIUS));
    }

    public void clear() {
        columns = rows = count = nodeCount = stamp = 0;
        cellHeads = groups = sizes = icons = members = directSeeds = directCount = order = sorted = queue = depth = firstNode = nodeLength = seen = new int[0];
        largeNext = largePrevious = nodeOwners = nodeCells = nodeNext = nodePrevious = new int[0];
        left = top = right = bottom = centerX = centerY = new float[0];
        memberLeft = memberTop = memberRight = memberBottom = groupLeft = groupTop = groupRight = groupBottom = new float[0];
        distances = new double[0]; hasIcon = dense = retainedDense = new boolean[0];
    }
}
