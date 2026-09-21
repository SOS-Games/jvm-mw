package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds Recast tiles whose sqlite tris leave a walkable TES cell edge
 * uncovered — including the thin cracks a 12-unit pad used to hide.
 * Ocean with no land is ignored. Fine tiles on the same border are not
 * listed. A TES cell line that is also a Recast tile edge (world X=0)
 * flags both Recast squares, not only the one the sample sits in.
 */
public final class NavmeshGaps {
    private static final float EDGE_STEP = 32f;
    private static final float NEAR = 256f;
    private static final float COVER_PAD = 0f;
    private static final float[] INSETS = {
        0f, 8f, 16f, 32f, 48f, 64f, 96f, 128f, 256f, 870f, 1740f, 2610f, 3480f, 4350f
    };

    private NavmeshGaps() {
    }

    public static List<int[]> failingExterior(int gx0, int gy0, int gx1, int gy1, List<NavmeshCache.Tile> tiles) {
        return failingExterior(gx0, gy0, gx1, gy1, tiles, null);
    }

    public static List<int[]> failingExterior(int gx0, int gy0, int gx1, int gy1, List<NavmeshCache.Tile> tiles,
        EsmFile.LoadedCell cell) {
        return scanExterior(gx0, gy0, gx1, gy1, tiles, cell).tiles();
    }

    public static List<int[]> failingInterior(List<NavmeshCache.Tile> tiles) {
        return scanInterior(tiles).tiles();
    }

    static Report scanExterior(int gx0, int gy0, int gx1, int gy1, List<NavmeshCache.Tile> tiles) {
        return scanExterior(gx0, gy0, gx1, gy1, tiles, null);
    }

    static Report scanExterior(int gx0, int gy0, int gx1, int gy1, List<NavmeshCache.Tile> tiles,
        EsmFile.LoadedCell cell) {
        Report r = new Report();
        TriIndex index = index(tiles);
        float x0 = gx0 * (float) LandRecord.CELL_SIZE;
        float y0 = gy0 * (float) LandRecord.CELL_SIZE;
        float x1 = (gx1 + 1) * (float) LandRecord.CELL_SIZE;
        float y1 = (gy1 + 1) * (float) LandRecord.CELL_SIZE;
        for (int gx = gx0; gx <= gx1 + 1; gx++) {
            float seam = gx * (float) LandRecord.CELL_SIZE;
            sampleSeam(r, index, seam, y0, y1, true, x0, x1, y0, y1, cell);
        }
        for (int gy = gy0; gy <= gy1 + 1; gy++) {
            float seam = gy * (float) LandRecord.CELL_SIZE;
            sampleSeam(r, index, seam, x0, x1, false, x0, x1, y0, y1, cell);
        }
        return r;
    }

    static Report scanInterior(List<NavmeshCache.Tile> tiles) {
        Report r = new Report();
        if (tiles == null || tiles.isEmpty()) {
            return r;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (NavmeshCache.Tile tile : tiles) {
            for (float[] t : tile.tris) {
                for (int k = 0; k < 3; k++) {
                    minX = Math.min(minX, t[k * 3]);
                    maxX = Math.max(maxX, t[k * 3]);
                    minY = Math.min(minY, t[k * 3 + 1]);
                    maxY = Math.max(maxY, t[k * 3 + 1]);
                }
            }
        }
        if (!(minX < maxX && minY < maxY)) {
            return r;
        }
        TriIndex index = index(tiles);
        sampleLine(r, index, minX, minY, minX, maxY, null);
        sampleLine(r, index, maxX, minY, maxX, maxY, null);
        sampleLine(r, index, minX, minY, maxX, minY, null);
        sampleLine(r, index, minX, maxY, maxX, maxY, null);
        return r;
    }

    private static void sampleSeam(Report r, TriIndex index, float seam, float a0, float a1, boolean vert,
        float x0, float x1, float y0, float y1, EsmFile.LoadedCell cell) {
        for (float inset : INSETS) {
            float line = seam + inset;
            if (vert) {
                if (line >= x0 && line <= x1) {
                    sampleSeamLine(r, index, line, a0, line, a1, seam, vert, cell);
                }
                if (inset != 0f) {
                    line = seam - inset;
                    if (line >= x0 && line <= x1) {
                        sampleSeamLine(r, index, line, a0, line, a1, seam, vert, cell);
                    }
                }
            } else {
                if (line >= y0 && line <= y1) {
                    sampleSeamLine(r, index, a0, line, a1, line, seam, vert, cell);
                }
                if (inset != 0f) {
                    line = seam - inset;
                    if (line >= y0 && line <= y1) {
                        sampleSeamLine(r, index, a0, line, a1, line, seam, vert, cell);
                    }
                }
            }
        }
    }

    private static void sampleSeamLine(Report r, TriIndex index, float x0, float y0, float x1, float y1,
        float seam, boolean vert, EsmFile.LoadedCell cell) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        int n = Math.max(1, (int) (len / EDGE_STEP));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = x0 + dx * t;
            float y = y0 + dy * t;
            hitSeam(r, index, x, y, seam, vert, cell);
        }
    }

    private static void hitSeam(Report r, TriIndex index, float x, float y, float seam, boolean vert,
        EsmFile.LoadedCell cell) {
        if (vert) {
            int ty = NavmeshDb.recastTile(y);
            failSide(r, index, cell, x, y, NavmeshDb.recastTile(seam - 1f), ty);
            failSide(r, index, cell, x, y, NavmeshDb.recastTile(seam + 1f), ty);
        } else {
            int tx = NavmeshDb.recastTile(x);
            failSide(r, index, cell, x, y, tx, NavmeshDb.recastTile(seam - 1f));
            failSide(r, index, cell, x, y, tx, NavmeshDb.recastTile(seam + 1f));
        }
    }

    private static void failSide(Report r, TriIndex index, EsmFile.LoadedCell cell, float x, float y, int tx, int ty) {
        if (index.coversTile(x, y, tx, ty)) {
            return;
        }
        if (!index.present.contains(NavmeshCache.key(tx, ty))
            && !NavmeshBaker.walkableLand(cell, x, y)) {
            return;
        }
        r.seams.add(new float[] { x, y });
        addFail(r, index, tx, ty);
    }

    private static void sampleLine(Report r, TriIndex index, float x0, float y0, float x1, float y1,
        EsmFile.LoadedCell cell) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        int n = Math.max(1, (int) (len / EDGE_STEP));
        boolean vert = Math.abs(dx) < Math.abs(dy);
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = x0 + dx * t;
            float y = y0 + dy * t;
            hitSample(r, index, x, y, vert, true, cell);
        }
    }

    private static void hitSample(Report r, TriIndex index, float x, float y, boolean vert, boolean neighbors,
        EsmFile.LoadedCell cell) {
        List<float[]> near = index.near(x, y, NEAR);
        if (covers(x, y, near)) {
            return;
        }
        if (near.isEmpty() && !NavmeshBaker.walkableLand(cell, x, y)) {
            return;
        }
        r.seams.add(new float[] { x, y });
        if (neighbors) {
            if (vert) {
                addFail(r, index, NavmeshDb.recastTile(x - 1f), NavmeshDb.recastTile(y));
                addFail(r, index, NavmeshDb.recastTile(x + 1f), NavmeshDb.recastTile(y));
            } else {
                addFail(r, index, NavmeshDb.recastTile(x), NavmeshDb.recastTile(y - 1f));
                addFail(r, index, NavmeshDb.recastTile(x), NavmeshDb.recastTile(y + 1f));
            }
            return;
        }
        addFail(r, index, NavmeshDb.recastTile(x), NavmeshDb.recastTile(y));
    }

    private static void addFail(Report r, TriIndex index, int tx, int ty) {
        long key = NavmeshCache.key(tx, ty);
        if (index.present.contains(key)) {
            r.keys.add(key);
        }
    }

    private static boolean covers(float x, float y, List<float[]> tris) {
        for (float[] tri : tris) {
            if (inTri(x, y, tri[0], tri[1], tri[3], tri[4], tri[6], tri[7], COVER_PAD)) {
                return true;
            }
        }
        return false;
    }

    private static TriIndex index(List<NavmeshCache.Tile> tiles) {
        return new TriIndex(tiles);
    }

    static final class Report {
        final List<float[]> seams = new ArrayList<>();
        final Set<Long> keys = new LinkedHashSet<>();
        final List<int[]> tiles = new ArrayList<>();

        List<int[]> tiles() {
            if (tiles.isEmpty() && !keys.isEmpty()) {
                for (long key : keys) {
                    tiles.add(new int[] { (int) (key >> 32), (int) key });
                }
            }
            return tiles;
        }
    }

    private static final class TriIndex {
        private static final float BIN = 256f;
        private final java.util.Map<Long, List<float[]>> bins = new java.util.HashMap<>();
        private final java.util.Map<Long, List<float[]>> byTile = new java.util.HashMap<>();
        private final Set<Long> present = new LinkedHashSet<>();

        TriIndex(List<NavmeshCache.Tile> tiles) {
            if (tiles == null) {
                return;
            }
            for (NavmeshCache.Tile tile : tiles) {
                long key = NavmeshCache.key(tile.x, tile.y);
                present.add(key);
                if (!tile.tris.isEmpty()) {
                    byTile.put(key, tile.tris);
                }
                for (float[] tri : tile.tris) {
                    float minX = min(tri[0], tri[3], tri[6]);
                    float maxX = max(tri[0], tri[3], tri[6]);
                    float minY = min(tri[1], tri[4], tri[7]);
                    float maxY = max(tri[1], tri[4], tri[7]);
                    int x0 = (int) Math.floor(minX / BIN);
                    int x1 = (int) Math.floor(maxX / BIN);
                    int y0 = (int) Math.floor(minY / BIN);
                    int y1 = (int) Math.floor(maxY / BIN);
                    for (int bx = x0; bx <= x1; bx++) {
                        for (int by = y0; by <= y1; by++) {
                            bins.computeIfAbsent(NavmeshCache.key(bx, by), k -> new ArrayList<>()).add(tri);
                        }
                    }
                }
            }
        }

        boolean coversTile(float x, float y, int tx, int ty) {
            List<float[]> tris = byTile.get(NavmeshCache.key(tx, ty));
            return tris != null && covers(x, y, tris);
        }

        List<float[]> near(float x, float y, float radius) {
            List<float[]> out = new ArrayList<>();
            int x0 = (int) Math.floor((x - radius) / BIN);
            int x1 = (int) Math.floor((x + radius) / BIN);
            int y0 = (int) Math.floor((y - radius) / BIN);
            int y1 = (int) Math.floor((y + radius) / BIN);
            for (int bx = x0; bx <= x1; bx++) {
                for (int by = y0; by <= y1; by++) {
                    List<float[]> hit = bins.get(NavmeshCache.key(bx, by));
                    if (hit != null) {
                        out.addAll(hit);
                    }
                }
            }
            return out;
        }
    }

    private static boolean inTri(float px, float py, float ax, float ay, float bx, float by, float cx, float cy,
        float pad) {
        if (px < min(ax, bx, cx) - pad || px > max(ax, bx, cx) + pad
            || py < min(ay, by, cy) - pad || py > max(ay, by, cy) + pad) {
            return false;
        }
        float v0x = cx - ax;
        float v0y = cy - ay;
        float v1x = bx - ax;
        float v1y = by - ay;
        float v2x = px - ax;
        float v2y = py - ay;
        float dot00 = v0x * v0x + v0y * v0y;
        float dot01 = v0x * v1x + v0y * v1y;
        float dot02 = v0x * v2x + v0y * v2y;
        float dot11 = v1x * v1x + v1y * v1y;
        float dot12 = v1x * v2x + v1y * v2y;
        float den = dot00 * dot11 - dot01 * dot01;
        if (Math.abs(den) < 1e-8f) {
            return false;
        }
        float u = (dot11 * dot02 - dot01 * dot12) / den;
        float v = (dot00 * dot12 - dot01 * dot02) / den;
        float slack = pad <= 0f ? 0f : 0.08f;
        return u >= -slack && v >= -slack && u + v <= 1f + slack;
    }

    private static float min(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }

    private static float max(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }
}
