/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * One cell’s walk graph ready for wander: world TES points, which nodes
 * can reach which, and A* along the edges. Exterior points are local in
 * the ESM; we add the cell origin here so dests match Town. An actor only
 * uses the graph for the cell they spawned in.
 */
public final class PathgridGraph {
    public static final PathgridGraph NONE = new PathgridGraph();

    public final float[] wx;
    public final float[] wy;
    public final float[] wz;
    public final int[] component;
    public final int[][] neighbors;

    private PathgridGraph() {
        wx = wy = wz = new float[0];
        component = new int[0];
        neighbors = new int[0][];
    }

    private PathgridGraph(float[] wx, float[] wy, float[] wz, int[] component, int[][] neighbors) {
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
        this.component = component;
        this.neighbors = neighbors;
    }

    public static PathgridGraph of(EsmFile.LoadedCell cell, float[] spawn) {
        if (cell == null || spawn == null) {
            return NONE;
        }
        if (cell.interior) {
            return build(cell.pathgrid, 0f, 0f);
        }
        int gx = LandRecord.cellGrid(spawn[0]);
        int gy = LandRecord.cellGrid(spawn[1]);
        EsmPathgrid grid = EsmPathgrid.NONE;
        for (EsmFile.GridTile tile : cell.tiles) {
            if (tile.gridX == gx && tile.gridY == gy) {
                grid = tile.pathgrid;
                break;
            }
        }
        return build(grid, gx * (float) LandRecord.CELL_SIZE, gy * (float) LandRecord.CELL_SIZE);
    }

    public static PathgridGraph build(EsmPathgrid grid, float originX, float originY) {
        if (grid == null || grid.points.size() < 2) {
            return NONE;
        }
        int n = grid.points.size();
        float[] wx = new float[n];
        float[] wy = new float[n];
        float[] wz = new float[n];
        for (int i = 0; i < n; i++) {
            EsmPathgrid.Point p = grid.points.get(i);
            wx[i] = originX + p.x;
            wy[i] = originY + p.y;
            wz[i] = p.z;
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        List<Set<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new HashSet<>());
        }
        for (EsmPathgrid.Edge e : grid.edges) {
            if (e.v0 == e.v1 || e.v0 < 0 || e.v1 < 0 || e.v0 >= n || e.v1 >= n) {
                continue;
            }
            adj.get(e.v0).add(e.v1);
            adj.get(e.v1).add(e.v0);
            union(parent, e.v0, e.v1);
        }
        int[] component = new int[n];
        for (int i = 0; i < n; i++) {
            component[i] = find(parent, i);
        }
        int[][] neighbors = new int[n][];
        for (int i = 0; i < n; i++) {
            Set<Integer> set = adj.get(i);
            int[] row = new int[set.size()];
            int k = 0;
            for (int v : set) {
                row[k++] = v;
            }
            neighbors[i] = row;
        }
        return new PathgridGraph(wx, wy, wz, component, neighbors);
    }

    public boolean usable() {
        return wx.length >= 2;
    }

    public int closestInRange(float x, float y, float spawnX, float spawnY, float range) {
        if (!usable() || range <= 0f) {
            return -1;
        }
        float range2 = range * range;
        int best = -1;
        float bestD = Float.POSITIVE_INFINITY;
        for (int i = 0; i < wx.length; i++) {
            if (dist2xy(i, spawnX, spawnY) > range2) {
                continue;
            }
            float dx = wx[i] - x;
            float dy = wy[i] - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public int closest(float x, float y, float z) {
        int best = 0;
        float bestD = dist2(0, x, y, z);
        for (int i = 1; i < wx.length; i++) {
            float d = dist2(i, x, y, z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Nodes the actor may pick as dests: the connected cluster around
     * {@code cx, cy} where each hop stays inside {@code range}. Fewer than
     * three nodes means they should wander at random instead.
     */
    public List<float[]> allowed(float cx, float cy, float cz, float range) {
        List<Integer> cluster = reachable(cx, cy, range);
        if (cluster.size() <= 2) {
            return List.of();
        }
        List<float[]> out = new ArrayList<>(cluster.size());
        for (int i : cluster) {
            out.add(pos(i));
        }
        return out;
    }

    /** Every node in the same connected piece as the closest node to {@code x, y}. */
    public List<float[]> componentOf(float x, float y, float z) {
        List<float[]> out = new ArrayList<>();
        if (!usable()) {
            return out;
        }
        int seed = closest(x, y, z);
        for (int i = 0; i < wx.length; i++) {
            if (component[i] == component[seed]) {
                out.add(pos(i));
            }
        }
        return out;
    }

    private List<Integer> reachable(float spawnX, float spawnY, float range) {
        List<Integer> cluster = new ArrayList<>();
        if (!usable() || range <= 0f) {
            return cluster;
        }
        float range2 = range * range;
        int seed = closestInRange(spawnX, spawnY, spawnX, spawnY, range);
        if (seed < 0) {
            return cluster;
        }
        boolean[] inRange = new boolean[wx.length];
        for (int i = 0; i < wx.length; i++) {
            if (component[i] == component[seed] && dist2xy(i, spawnX, spawnY) <= range2) {
                inRange[i] = true;
            }
        }
        boolean[] seen = new boolean[wx.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(seed);
        seen[seed] = true;
        while (!q.isEmpty()) {
            int i = q.removeFirst();
            cluster.add(i);
            for (int n : neighbors[i]) {
                if (seen[n] || !inRange[n] || dist2xy(n, wx[i], wy[i]) > range2) {
                    continue;
                }
                seen[n] = true;
                q.add(n);
            }
        }
        return cluster;
    }

    /**
     * World TES waypoints from {@code from} to {@code dest}. Drops the node
     * they are already next to. Same start and goal walks straight to dest.
     * When {@code range} is positive, A* stays on nodes within that TES
     * distance of {@code cx, cy} on the ground (XY). Empty means no path.
     */
    public List<float[]> pathTo(float fromX, float fromY, float fromZ, float destX, float destY, float destZ,
        float cx, float cy, float cz, float range) {
        List<float[]> out = new ArrayList<>();
        if (!usable()) {
            return out;
        }
        int start = closestInRange(fromX, fromY, cx, cy, range);
        int goal = closestInRange(destX, destY, cx, cy, range);
        if (start < 0 || goal < 0) {
            return out;
        }
        if (start == goal) {
            out.add(new float[] {destX, destY, destZ});
            return out;
        }
        if (component[start] != component[goal]) {
            return out;
        }
        int[] came = astar(start, goal, cx, cy, cz, range);
        if (came == null) {
            return out;
        }
        List<Integer> nodes = new ArrayList<>();
        int cur = goal;
        nodes.add(cur);
        while (cur != start) {
            cur = came[cur];
            if (cur < 0) {
                return List.of();
            }
            nodes.add(cur);
        }
        Collections.reverse(nodes);
        for (int i = 1; i < nodes.size(); i++) {
            out.add(pos(nodes.get(i)));
        }
        float[] last = out.get(out.size() - 1);
        float dx = last[0] - destX;
        float dy = last[1] - destY;
        if (dx * dx + dy * dy > 1f) {
            out.add(new float[] {destX, destY, destZ});
        }
        return out;
    }

    private int[] astar(int start, int goal, float spawnX, float spawnY, float spawnZ, float range) {
        int n = wx.length;
        float[] g = new float[n];
        Arrays.fill(g, Float.POSITIVE_INFINITY);
        g[start] = 0f;
        int[] came = new int[n];
        Arrays.fill(came, -1);
        boolean[] closed = new boolean[n];
        float range2 = range > 0f ? range * range : Float.POSITIVE_INFINITY;
        record Open(int i, float f) {
        }
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::f));
        open.add(new Open(start, dist(start, goal)));
        while (!open.isEmpty()) {
            int current = open.poll().i;
            if (closed[current]) {
                continue;
            }
            if (current == goal) {
                return came;
            }
            closed[current] = true;
            for (int dest : neighbors[current]) {
                if (closed[dest]) {
                    continue;
                }
                if (dist2xy(dest, spawnX, spawnY) > range2 || dist2xy(dest, wx[current], wy[current]) > range2) {
                    continue;
                }
                float tentative = g[current] + dist(current, dest);
                if (tentative >= g[dest]) {
                    continue;
                }
                came[dest] = current;
                g[dest] = tentative;
                open.add(new Open(dest, tentative + dist(dest, goal)));
            }
        }
        return null;
    }

    private float[] pos(int i) {
        return new float[] {wx[i], wy[i], wz[i]};
    }

    private float dist2xy(int i, float x, float y) {
        float dx = wx[i] - x;
        float dy = wy[i] - y;
        return dx * dx + dy * dy;
    }

    private float dist2(int i, float x, float y, float z) {
        float dx = wx[i] - x;
        float dy = wy[i] - y;
        float dz = wz[i] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private float dist(int a, int b) {
        float dx = wx[a] - wx[b];
        float dy = wy[a] - wy[b];
        float dz = wz[a] - wz[b];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
