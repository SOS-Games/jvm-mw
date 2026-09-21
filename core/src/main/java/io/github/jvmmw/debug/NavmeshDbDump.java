package io.github.jvmmw.debug;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmReader;
import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.render.NavmeshBaker;
import io.github.jvmmw.render.NavmeshCache;
import io.github.jvmmw.render.NavmeshDb;
import io.github.jvmmw.render.NavmeshGaps;
import io.github.jvmmw.resource.TestData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless read of OpenMW navmesh.db. Prints Recast tile holes and
 * whether decoded walkable tris cover TES cell edges, then writes a
 * top-down PNG. Then rebakes the failing Recast tiles from land (same
 * baker as the viewer, no shack collision) and writes a second PNG so
 * leftover magenta lines are the gaps generation did not fill.
 */
public final class NavmeshDbDump {
    private static final int CELL = LandRecord.CELL_SIZE;
    private static final float PIX = 16f;
    private static final float EDGE_STEP = 64f;
    private static final float NEAR = 256f;
    private static final int GREEN = 0x2f_c0_58;
    private static final int CYAN = 0x28_c8_e0;
    private static final int PATCH_BOX = 0x1c_48_58;
    private static final int BAKE_FAIL = 0xe0_88_20;
    private static final int BG = 0x18_1c_14;
    private static final int CELL_LINE = 0xe8_d0_40;
    private static final int MISS = 0xc0_28_28;
    private static final int SEAM = 0xff_60_e0;

    private NavmeshDbDump() {
    }

    static void run(String spec) throws Exception {
        Path db = NavmeshDb.dbFile();
        System.out.println("db=" + db.toAbsolutePath());
        System.out.println("exists=" + Files.isRegularFile(db));
        if (!Files.isRegularFile(db)) {
            System.out.println("verdict=no-db");
            return;
        }
        String[] parts = spec == null ? new String[0] : spec.trim().split("[,\\s]+");
        if (parts.length == 1 && parts[0].isEmpty()) {
            parts = new String[0];
        }
        if (parts.length >= 2 && isInt(parts[0]) && isInt(parts[1])) {
            int gx = Integer.parseInt(parts[0]);
            int gy = Integer.parseInt(parts[1]);
            int radius = parts.length >= 3 && isInt(parts[2]) ? Integer.parseInt(parts[2]) : EsmFile.CELL_GRID_RADIUS;
            dumpExterior(gx, gy, radius);
        } else if (parts.length >= 1) {
            dumpInterior(String.join(" ", parts));
        } else {
            dumpExterior(TestData.TOWN_GRID_X, TestData.TOWN_GRID_Y, EsmFile.CELL_GRID_RADIUS);
        }
    }

    private static void dumpExterior(int gx, int gy, int radius) throws Exception {
        int gx0 = gx - radius;
        int gy0 = gy - radius;
        int gx1 = gx + radius;
        int gy1 = gy + radius;
        float tesMinX = gx0 * (float) CELL;
        float tesMaxX = (gx1 + 1) * (float) CELL;
        float tesMinY = gy0 * (float) CELL;
        float tesMaxY = (gy1 + 1) * (float) CELL;
        NavmeshDb.Query q = NavmeshDb.queryExterior(gx0, gy0, gx1, gy1);
        System.out.println("world=" + q.world
            + " cells=(" + gx0 + "," + gy0 + ")..(" + gx1 + "," + gy1 + ")"
            + " tesX=" + (int) tesMinX + ".." + (int) tesMaxX
            + " tesY=" + (int) tesMinY + ".." + (int) tesMaxY);
        printQuery(q);
        List<int[]> missing = missingTiles(q);
        int edgeHoles = 0;
        float tileTes = NavmeshDb.tileSizeTes();
        for (int[] m : missing) {
            float minX = m[0] * tileTes;
            float maxX = (m[0] + 1) * tileTes;
            float minY = m[1] * tileTes;
            float maxY = (m[1] + 1) * tileTes;
            boolean edge = onCellEdge(minX, maxX, tesMinX, tesMaxX) || onCellEdge(minY, maxY, tesMinY, tesMaxY);
            if (edge) {
                edgeHoles++;
            }
            if (missing.size() <= 40 || edge) {
                System.out.println("missing tx=" + m[0] + " ty=" + m[1]
                    + " tes=(" + (int) minX + ".." + (int) maxX + "," + (int) minY + ".." + (int) maxY + ")"
                    + (edge ? " cell-edge" : ""));
            }
        }
        if (missing.size() > 40) {
            System.out.println("missing listed=" + Math.min(missing.size(), 40 + edgeHoles) + " of " + missing.size());
        }
        EdgeStats edges = sampleEdges(gx0, gy0, gx1, gy1, q.tiles);
        System.out.println("cellEdges samples=" + edges.samples
            + " near=" + edges.near
            + " covered=" + edges.covered
            + " seam=" + edges.seam
            + " ocean=" + edges.ocean);
        for (String line : edges.lines) {
            System.out.println(line);
        }
        List<int[]> patch = NavmeshGaps.failingExterior(gx0, gy0, gx1, gy1, q.tiles);
        printPatch(patch);
        Path png = writePng(gx0, gy0, gx1, gy1, q.tiles, List.of(), missing, patch, List.of(), edges, "");
        System.out.println("png=" + png.toAbsolutePath());
        String verdict;
        if (q.decoded == 0) {
            verdict = "empty";
        } else if (edges.near > 0 && edges.seam * 20 > edges.near) {
            verdict = "db-mesh-gap";
        } else if (edgeHoles > 0) {
            verdict = "db-tile-gap";
        } else if (edges.near > 0 && edges.seam == 0) {
            verdict = "db-covers-edges";
        } else {
            verdict = "mixed";
        }
        System.out.println("verdict=" + verdict
            + " recastMissing=" + missing.size()
            + " cellEdgeMissingTiles=" + edgeHoles
            + " seamSamples=" + edges.seam);
        System.out.println("db-covers-edges means the sqlite tris reach cell borders; lines in the viewer are ours.");
        System.out.println("db-mesh-gap / db-tile-gap means OpenMW's navmesh.db already has those cracks.");
        bakeAfter(gx, gy, gx0, gy0, gx1, gy1, q, patch, edges);
    }

    private static void dumpInterior(String name) throws Exception {
        String world = name.toLowerCase(Locale.ROOT);
        NavmeshDb.Query q = NavmeshDb.queryInterior(world);
        System.out.println("world=" + q.world + " interior=true");
        printQuery(q);
        if (q.decoded == 0) {
            System.out.println("verdict=empty");
            return;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int tris = 0;
        for (NavmeshCache.Tile tile : q.tiles) {
            tris += tile.tris.size();
            for (float[] t : tile.tris) {
                for (int k = 0; k < 3; k++) {
                    minX = Math.min(minX, t[k * 3]);
                    maxX = Math.max(maxX, t[k * 3]);
                    minY = Math.min(minY, t[k * 3 + 1]);
                    maxY = Math.max(maxY, t[k * 3 + 1]);
                }
            }
        }
        System.out.println("tris=" + tris + " tesX=" + (int) minX + ".." + (int) maxX
            + " tesY=" + (int) minY + ".." + (int) maxY);
        printPatch(NavmeshGaps.failingInterior(q.tiles));
        Path png = writeInteriorPng(q, minX, minY, maxX, maxY, world);
        System.out.println("png=" + png.toAbsolutePath());
        System.out.println("verdict=interior");
    }

    private static void printQuery(NavmeshDb.Query q) {
        int expect = 0;
        if (q.tx1 > q.tx0 && q.ty1 > q.ty0) {
            expect = (q.tx1 - q.tx0) * (q.ty1 - q.ty0);
        }
        System.out.println("recastRange tx=" + q.tx0 + ".." + (q.tx1 - 1)
            + " ty=" + q.ty0 + ".." + (q.ty1 - 1)
            + " expected=" + expect
            + " blobs=" + q.blobs
            + " decoded=" + q.decoded
            + " fail=" + q.fail
            + " tileTes=" + NavmeshDb.tileSizeTes());
        int tris = 0;
        int polys = 0;
        for (NavmeshCache.Tile tile : q.tiles) {
            tris += tile.tris.size();
            polys += tile.polys;
        }
        System.out.println("polys=" + polys + " tris=" + tris + " scale=" + NavmeshBaker.SCALE);
    }

    private static void printPatch(List<int[]> tiles) {
        System.out.println("patch=" + tiles.size());
        int listed = Math.min(tiles.size(), 40);
        for (int i = 0; i < listed; i++) {
            int[] t = tiles.get(i);
            System.out.println("patch tx=" + t[0] + " ty=" + t[1]);
        }
        if (tiles.size() > listed) {
            System.out.println("patch listed=" + listed + " of " + tiles.size());
        }
    }

    private static void bakeAfter(int gx, int gy, int gx0, int gy0, int gx1, int gy1, NavmeshDb.Query q,
        List<int[]> patch, EdgeStats before) throws Exception {
        if (patch.isEmpty()) {
            System.out.println("after skip=no-patch");
            return;
        }
        EsmFile.LoadedCell cell = EsmFile.loadExterior(EsmReader.open(TestData.esmPath()), gx, gy);
        System.out.println("after lands=" + cell.lands.size() + " tiles=" + cell.tiles.size()
            + " land=" + (cell.land == null ? "none" : cell.land.gridX + "," + cell.land.gridY));
        if (!cell.lands.isEmpty()) {
            LandRecord l0 = cell.lands.get(0);
            System.out.println("after land0=" + l0.gridX + "," + l0.gridY);
        }
        float tileTes = NavmeshDb.tileSizeTes();
        int probeTx = patch.get(0)[0];
        int probeTy = patch.get(0)[1];
        printGeomProbe(cell, probeTx, probeTy, tileTes, "first");
        printGeomProbe(cell, NavmeshDb.recastTile((gx + 0.5f) * CELL), NavmeshDb.recastTile((gy + 0.5f) * CELL),
            tileTes, "center");
        Map<Long, NavmeshCache.Tile> byKey = new LinkedHashMap<>();
        for (NavmeshCache.Tile tile : q.tiles) {
            byKey.put(NavmeshCache.key(tile.x, tile.y), tile);
        }
        List<NavmeshCache.Tile> baked = new ArrayList<>();
        List<int[]> fail = new ArrayList<>();
        int ok = 0;
        int emptyGeom = 0;
        int emptyMesh = 0;
        for (int i = 0; i < patch.size(); i++) {
            int tx = patch.get(i)[0];
            int ty = patch.get(i)[1];
            float[] geom = NavmeshBaker.gatherTesRecast(null, cell, tx, ty);
            if (geom.length < 9) {
                emptyGeom++;
                fail.add(patch.get(i));
            } else {
                NavmeshCache.Tile tile = NavmeshBaker.bakeRecastTile(geom, tx, ty);
                if (tile != null) {
                    byKey.put(NavmeshCache.key(tx, ty), tile);
                    baked.add(tile);
                    ok++;
                } else {
                    emptyMesh++;
                    fail.add(patch.get(i));
                }
            }
            if ((i + 1) % 25 == 0 || i + 1 == patch.size()) {
                System.out.println("bake " + (i + 1) + "/" + patch.size() + " ok=" + ok + " fail=" + fail.size()
                    + " emptyGeom=" + emptyGeom + " emptyMesh=" + emptyMesh);
            }
        }
        java.util.Set<Long> bakedKeys = new java.util.HashSet<>();
        for (NavmeshCache.Tile tile : baked) {
            bakedKeys.add(NavmeshCache.key(tile.x, tile.y));
        }
        List<NavmeshCache.Tile> rest = new ArrayList<>();
        for (NavmeshCache.Tile tile : byKey.values()) {
            if (!bakedKeys.contains(NavmeshCache.key(tile.x, tile.y))) {
                rest.add(tile);
            }
        }
        List<NavmeshCache.Tile> all = new ArrayList<>(byKey.values());
        EdgeStats after = sampleEdges(gx0, gy0, gx1, gy1, all);
        System.out.println("after bakeOk=" + ok + " bakeFail=" + fail.size()
            + " emptyGeom=" + emptyGeom + " emptyMesh=" + emptyMesh
            + " seam " + before.seam + "->" + after.seam
            + " covered " + before.covered + "->" + after.covered);
        for (String line : after.lines) {
            System.out.println("after " + line);
        }
        List<int[]> remain = NavmeshGaps.failingExterior(gx0, gy0, gx1, gy1, all);
        System.out.println("after patchRemain=" + remain.size());
        Path png = writePng(gx0, gy0, gx1, gy1, rest, baked, List.of(), List.of(), fail, after, "-after");
        System.out.println("pngAfter=" + png.toAbsolutePath());
        System.out.println("after PNG: cyan=rebaked, green=sqlite kept, orange=bake empty, magenta=still uncovered.");
    }

    private static void printGeomProbe(EsmFile.LoadedCell cell, int tx, int ty, float tileTes, String label) {
        float minX = tx * tileTes;
        float maxX = (tx + 1) * tileTes;
        float minY = ty * tileTes;
        float maxY = (ty + 1) * tileTes;
        int hits = 0;
        StringBuilder hit = new StringBuilder();
        for (LandRecord land : cell.lands) {
            float ox = land.gridX * (float) CELL;
            float oy = land.gridY * (float) CELL;
            float x1 = ox + CELL;
            float y1 = oy + CELL;
            if (ox <= maxX && x1 >= minX && oy <= maxY && y1 >= minY) {
                hits++;
                if (hit.length() < 80) {
                    hit.append(" (").append(land.gridX).append(',').append(land.gridY).append(')');
                }
            }
        }
        float[] geom = NavmeshBaker.gatherTesRecast(null, cell, tx, ty);
        NavmeshCache.Tile baked = geom.length < 9 ? null : NavmeshBaker.bakeRecastTile(geom, tx, ty);
        System.out.println("after geom " + label + " tx=" + tx + " ty=" + ty
            + " tes=(" + (int) minX + ".." + (int) maxX + "," + (int) minY + ".." + (int) maxY + ")"
            + " landHits=" + hits + hit + " floats=" + geom.length
            + " bake=" + (baked == null ? "null " + NavmeshBaker.lastBakeWhy
                : "polys=" + baked.polys + " tris=" + baked.tris.size()));
    }

    private static List<int[]> missingTiles(NavmeshDb.Query q) {
        List<int[]> missing = new ArrayList<>();
        if (q.tx1 <= q.tx0) {
            return missing;
        }
        for (int tx = q.tx0; tx < q.tx1; tx++) {
            for (int ty = q.ty0; ty < q.ty1; ty++) {
                if (!q.keys.contains(NavmeshCache.key(tx, ty))) {
                    missing.add(new int[] {tx, ty});
                }
            }
        }
        return missing;
    }

    private static boolean onCellEdge(float min, float max, float tesMin, float tesMax) {
        for (float e = tesMin; e <= tesMax + 0.5f; e += CELL) {
            if (min < e + 1f && max > e - 1f) {
                return true;
            }
        }
        return false;
    }

    private static EdgeStats sampleEdges(int gx0, int gy0, int gx1, int gy1, List<NavmeshCache.Tile> tiles) {
        EdgeStats s = new EdgeStats();
        List<float[]> tris = new ArrayList<>();
        for (NavmeshCache.Tile tile : tiles) {
            tris.addAll(tile.tris);
        }
        TriIndex index = new TriIndex(tris);
        for (int gx = gx0 + 1; gx <= gx1; gx++) {
            float x = gx * (float) CELL;
            EdgeHit hit = sampleLine(x, gy0 * (float) CELL, x, (gy1 + 1) * (float) CELL, index);
            addEdge(s, "x=" + (int) x, hit);
        }
        for (int gy = gy0 + 1; gy <= gy1; gy++) {
            float y = gy * (float) CELL;
            EdgeHit hit = sampleLine(gx0 * (float) CELL, y, (gx1 + 1) * (float) CELL, y, index);
            addEdge(s, "y=" + (int) y, hit);
        }
        return s;
    }

    private static void addEdge(EdgeStats s, String name, EdgeHit hit) {
        s.samples += hit.samples;
        s.near += hit.near;
        s.covered += hit.covered;
        s.seam += hit.seam;
        s.ocean += hit.ocean;
        s.points.addAll(hit.seams);
        s.lines.add("edge " + name + " samples=" + hit.samples + " near=" + hit.near
            + " covered=" + hit.covered + " seam=" + hit.seam + " ocean=" + hit.ocean);
    }

    private static EdgeHit sampleLine(float x0, float y0, float x1, float y1, TriIndex index) {
        EdgeHit hit = new EdgeHit();
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        int n = Math.max(1, (int) (len / EDGE_STEP));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = x0 + dx * t;
            float y = y0 + dy * t;
            hit.samples++;
            List<float[]> tris = index.near(x, y, NEAR);
            boolean cover = covers(x, y, tris);
            boolean near = cover || !tris.isEmpty();
            if (!near) {
                hit.ocean++;
                continue;
            }
            hit.near++;
            if (cover) {
                hit.covered++;
            } else {
                hit.seam++;
                hit.seams.add(new float[] {x, y});
            }
        }
        return hit;
    }

    private static boolean covers(float x, float y, List<float[]> tris) {
        for (float[] tri : tris) {
            if (inTri(x, y, tri[0], tri[1], tri[3], tri[4], tri[6], tri[7], 12f)) {
                return true;
            }
        }
        return false;
    }

    private static final class TriIndex {
        private static final float BIN = 256f;
        private final java.util.Map<Long, List<float[]>> bins = new java.util.HashMap<>();

        TriIndex(List<float[]> tris) {
            for (float[] tri : tris) {
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

    private static Path writePng(int gx0, int gy0, int gx1, int gy1, List<NavmeshCache.Tile> green,
        List<NavmeshCache.Tile> cyan, List<int[]> missBoxes, List<int[]> patchBoxes, List<int[]> failBoxes,
        EdgeStats edges, String suffix)
        throws Exception {
        float tesMinX = gx0 * (float) CELL;
        float tesMaxX = (gx1 + 1) * (float) CELL;
        float tesMinY = gy0 * (float) CELL;
        float tesMaxY = (gy1 + 1) * (float) CELL;
        int w = Math.max(1, Math.round((tesMaxX - tesMinX) / PIX));
        int h = Math.max(1, Math.round((tesMaxY - tesMinY) / PIX));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, BG);
            }
        }
        float tileTes = NavmeshDb.tileSizeTes();
        for (int[] m : missBoxes) {
            fillRect(img, tesMinX, tesMinY, w, h, m[0] * tileTes, m[1] * tileTes,
                (m[0] + 1) * tileTes, (m[1] + 1) * tileTes, MISS);
        }
        for (int[] m : patchBoxes) {
            fillRect(img, tesMinX, tesMinY, w, h, m[0] * tileTes, m[1] * tileTes,
                (m[0] + 1) * tileTes, (m[1] + 1) * tileTes, PATCH_BOX);
        }
        for (int[] m : failBoxes) {
            fillRect(img, tesMinX, tesMinY, w, h, m[0] * tileTes, m[1] * tileTes,
                (m[0] + 1) * tileTes, (m[1] + 1) * tileTes, BAKE_FAIL);
        }
        for (NavmeshCache.Tile tile : green) {
            for (float[] t : tile.tris) {
                fillTri(img, tesMinX, tesMinY, w, h, t, GREEN);
            }
        }
        for (NavmeshCache.Tile tile : cyan) {
            for (float[] t : tile.tris) {
                fillTri(img, tesMinX, tesMinY, w, h, t, CYAN);
            }
        }
        for (int gx = gx0; gx <= gx1 + 1; gx++) {
            int x = Math.round((gx * CELL - tesMinX) / PIX);
            vline(img, x, CELL_LINE);
        }
        for (int gy = gy0; gy <= gy1 + 1; gy++) {
            int y = Math.round((gy * CELL - tesMinY) / PIX);
            hline(img, y, CELL_LINE);
        }
        for (float[] p : edges.points) {
            int x = Math.round((p[0] - tesMinX) / PIX);
            int y = Math.round((p[1] - tesMinY) / PIX);
            dot(img, x, h - 1 - y, SEAM);
        }
        Path dir = Path.of("build");
        Files.createDirectories(dir);
        Path out = dir.resolve("navdb-" + gx0 + "_" + gy0 + "-" + gx1 + "_" + gy1 + suffix + ".png");
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private static Path writeInteriorPng(NavmeshDb.Query q, float minX, float minY, float maxX, float maxY,
        String world) throws Exception {
        float pad = 64f;
        minX -= pad;
        minY -= pad;
        maxX += pad;
        maxY += pad;
        int w = Math.max(1, Math.round((maxX - minX) / PIX));
        int h = Math.max(1, Math.round((maxY - minY) / PIX));
        w = Math.min(w, 4096);
        h = Math.min(h, 4096);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, BG);
            }
        }
        for (NavmeshCache.Tile tile : q.tiles) {
            for (float[] t : tile.tris) {
                fillTri(img, minX, minY, w, h, t, GREEN);
            }
        }
        Path dir = Path.of("build");
        Files.createDirectories(dir);
        String safe = world.replaceAll("[^a-z0-9]+", "_");
        Path out = dir.resolve("navdb-" + safe + ".png");
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private static void fillTri(BufferedImage img, float tesMinX, float tesMinY, int w, int h, float[] t, int rgb) {
        float ax = t[0];
        float ay = t[1];
        float bx = t[3];
        float by = t[4];
        float cx = t[6];
        float cy = t[7];
        int x0 = Math.max(0, (int) Math.floor((min(ax, bx, cx) - tesMinX) / PIX) - 1);
        int x1 = Math.min(w - 1, (int) Math.ceil((max(ax, bx, cx) - tesMinX) / PIX) + 1);
        int y0 = Math.max(0, (int) Math.floor((min(ay, by, cy) - tesMinY) / PIX) - 1);
        int y1 = Math.min(h - 1, (int) Math.ceil((max(ay, by, cy) - tesMinY) / PIX) + 1);
        for (int py = y0; py <= y1; py++) {
            float tesY = tesMinY + (py + 0.5f) * PIX;
            for (int px = x0; px <= x1; px++) {
                float tesX = tesMinX + (px + 0.5f) * PIX;
                if (inTri(tesX, tesY, ax, ay, bx, by, cx, cy, 0f)) {
                    img.setRGB(px, h - 1 - py, rgb);
                }
            }
        }
    }

    private static void fillRect(BufferedImage img, float tesMinX, float tesMinY, int w, int h,
        float x0, float y0, float x1, float y1, int rgb) {
        int px0 = Math.max(0, Math.round((x0 - tesMinX) / PIX));
        int px1 = Math.min(w - 1, Math.round((x1 - tesMinX) / PIX));
        int py0 = Math.max(0, Math.round((y0 - tesMinY) / PIX));
        int py1 = Math.min(h - 1, Math.round((y1 - tesMinY) / PIX));
        for (int py = py0; py <= py1; py++) {
            int iy = h - 1 - py;
            if (iy < 0 || iy >= h) {
                continue;
            }
            for (int px = px0; px <= px1; px++) {
                img.setRGB(px, iy, rgb);
            }
        }
    }

    private static void vline(BufferedImage img, int x, int rgb) {
        if (x < 0 || x >= img.getWidth()) {
            return;
        }
        for (int y = 0; y < img.getHeight(); y++) {
            img.setRGB(x, y, rgb);
        }
    }

    private static void hline(BufferedImage img, int ySrc, int rgb) {
        int y = img.getHeight() - 1 - ySrc;
        if (y < 0 || y >= img.getHeight()) {
            return;
        }
        for (int x = 0; x < img.getWidth(); x++) {
            img.setRGB(x, y, rgb);
        }
    }

    private static void dot(BufferedImage img, int x, int y, int rgb) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int px = x + dx;
                int py = y + dy;
                if (px >= 0 && py >= 0 && px < img.getWidth() && py < img.getHeight()) {
                    img.setRGB(px, py, rgb);
                }
            }
        }
    }

    private static float min(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }

    private static float max(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static final class EdgeStats {
        int samples;
        int near;
        int covered;
        int seam;
        int ocean;
        final List<String> lines = new ArrayList<>();
        final List<float[]> points = new ArrayList<>();
    }

    private static final class EdgeHit {
        int samples;
        int near;
        int covered;
        int seam;
        int ocean;
        final List<float[]> seams = new ArrayList<>();
    }
}
