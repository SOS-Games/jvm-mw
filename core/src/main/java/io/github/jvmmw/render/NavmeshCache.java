package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;

import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps decoded OpenMW navmesh tiles around when the walk grid moves.
 * Sqlite runs on one worker so the carpet and Detour come up as they do
 * now. Cracked Recast tiles are rebaked later on a second thread — walking
 * does not wait, and holes stay until that bake finishes. A Recast tile that
 * straddles TES cell edges waits until every overlapping land cell is loaded,
 * including the neighbor across a Recast tile edge that sits on a TES cell
 * line (world X=0). Otherwise the bake sees a cliff and eats a thin strip.
 */
public final class NavmeshCache {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> have
        = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Tile>> stored
        = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> patched
        = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> tileCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> polyCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> patchCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> sources = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Tile>> ready
        = new ConcurrentHashMap<>();
    private static final Set<String> interiorsDone = ConcurrentHashMap.newKeySet();
    private static final Set<String> patching = ConcurrentHashMap.newKeySet();
    private static final Set<String> bakedReady = ConcurrentHashMap.newKeySet();
    private static final float GEN_LIFT = 12f;
    private static final float GEN_STEP = 128f;
    private static final AtomicInteger jobs = new AtomicInteger();
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "navmesh");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService PATCH = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "navmesh-patch");
        t.setDaemon(true);
        return t;
    });

    private NavmeshCache() {
    }

    static void request(EsmFile.LoadedCell cell, CollisionWorld collision) {
        if (cell == null) {
            return;
        }
        jobs.incrementAndGet();
        EX.execute(() -> {
            try {
                load(cell, collision);
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("JVM-MW", "navmesh", e);
            } finally {
                jobs.decrementAndGet();
            }
        });
    }

    static String source(String world) {
        if (jobs.get() > 0) {
            return "load";
        }
        return sources.getOrDefault(world, "none");
    }

    static boolean busy() {
        return jobs.get() > 0;
    }

    static Tile poll(String world) {
        ConcurrentLinkedQueue<Tile> q = ready.get(world);
        return q == null ? null : q.poll();
    }

    static int tiles(String world) {
        AtomicInteger n = tileCounts.get(world);
        return n == null ? 0 : n.get();
    }

    static int polys(String world) {
        AtomicInteger n = polyCounts.get(world);
        return n == null ? 0 : n.get();
    }

    static int patches(String world) {
        AtomicInteger n = patchCounts.get(world);
        return n == null ? 0 : n.get();
    }

    private static void load(EsmFile.LoadedCell cell, CollisionWorld collision) {
        String world = NavmeshDb.worldspace(cell);
        if (cell.interior && interiorsDone.contains(world)) {
            return;
        }
        int added = NavmeshDb.loadMissing(cell, key -> has(world, key), tile -> put(world, tile, "db"));
        if (cell.interior) {
            interiorsDone.add(world);
        }
        if (added == 0 && tiles(world) == 0) {
            for (Tile tile : NavmeshBaker.bakeRuntime(collision, cell)) {
                put(world, tile, "bake");
            }
            return;
        }
        if (!"bake".equals(sources.get(world))) {
            PATCH.execute(() -> {
                try {
                    runPatch(world, cell, collision);
                } catch (Exception e) {
                    com.badlogic.gdx.Gdx.app.error("JVM-MW", "navmesh-patch", e);
                }
            });
        }
    }

    private static void runPatch(String world, EsmFile.LoadedCell cell, CollisionWorld collision) {
        List<Tile> haveTiles = snapshot(world);
        if (haveTiles.isEmpty()) {
            return;
        }
        List<int[]> fail;
        if (cell.interior) {
            fail = NavmeshGaps.failingInterior(haveTiles);
        } else {
            int gx0 = cell.gridX;
            int gy0 = cell.gridY;
            int gx1 = cell.gridX;
            int gy1 = cell.gridY;
            if (!cell.tiles.isEmpty()) {
                gx0 = gy0 = Integer.MAX_VALUE;
                gx1 = gy1 = Integer.MIN_VALUE;
                for (EsmFile.GridTile tile : cell.tiles) {
                    gx0 = Math.min(gx0, tile.gridX);
                    gy0 = Math.min(gy0, tile.gridY);
                    gx1 = Math.max(gx1, tile.gridX);
                    gy1 = Math.max(gy1, tile.gridY);
                }
            }
            fail = NavmeshGaps.failingExterior(gx0, gy0, gx1, gy1, haveTiles, cell);
        }
        List<int[]> work = new ArrayList<>();
        for (int[] xy : fail) {
            int tx = xy[0];
            int ty = xy[1];
            long key = key(tx, ty);
            String id = world + "/" + key;
            if (bakedReady.contains(id)) {
                continue;
            }
            if (!cell.interior && !landCoversTile(cell, tx, ty)) {
                continue;
            }
            if (!patching.add(id)) {
                continue;
            }
            work.add(xy);
            queueGenerating(world, cell, tx, ty);
        }
        addSeamNeighbors(work, world, cell);
        for (int[] xy : work) {
            int tx = xy[0];
            int ty = xy[1];
            long key = key(tx, ty);
            String id = world + "/" + key;
            try {
                float[] geom = NavmeshBaker.gatherTesRecast(collision, cell, tx, ty);
                Tile baked = NavmeshBaker.bakeRecastTile(geom, tx, ty);
                if (baked != null) {
                    replace(world, baked);
                    markPatched(world, key);
                    bakedReady.add(id);
                }
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("JVM-MW", "navmesh-patch", e);
            } finally {
                patching.remove(id);
            }
        }
    }

    private static boolean landCoversTile(EsmFile.LoadedCell cell, int tx, int ty) {
        if (cell == null) {
            return false;
        }
        float tileTes = NavmeshDb.tileSizeTes();
        float cellTes = LandRecord.CELL_SIZE;
        float minX = tx * tileTes;
        float maxX = minX + tileTes;
        float minY = ty * tileTes;
        float maxY = minY + tileTes;
        int gx0 = LandRecord.cellGrid(minX);
        int gx1 = LandRecord.cellGrid(maxX - 0.01f);
        int gy0 = LandRecord.cellGrid(minY);
        int gy1 = LandRecord.cellGrid(maxY - 0.01f);
        if (onCellLine(minX, cellTes)) {
            gx0 = Math.min(gx0, LandRecord.cellGrid(minX - 1f));
        }
        if (onCellLine(maxX, cellTes)) {
            gx1 = Math.max(gx1, LandRecord.cellGrid(maxX));
        }
        if (onCellLine(minY, cellTes)) {
            gy0 = Math.min(gy0, LandRecord.cellGrid(minY - 1f));
        }
        if (onCellLine(maxY, cellTes)) {
            gy1 = Math.max(gy1, LandRecord.cellGrid(maxY));
        }
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gy = gy0; gy <= gy1; gy++) {
                if (!hasLand(cell, gx, gy)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasLand(EsmFile.LoadedCell cell, int gx, int gy) {
        if (cell.land != null && cell.land.gridX == gx && cell.land.gridY == gy) {
            return true;
        }
        for (LandRecord land : cell.lands) {
            if (land.gridX == gx && land.gridY == gy) {
                return true;
            }
        }
        return false;
    }

    private static void addSeamNeighbors(List<int[]> work, String world, EsmFile.LoadedCell cell) {
        if (cell != null && cell.interior) {
            return;
        }
        float ts = NavmeshDb.tileSizeTes();
        float cellTes = LandRecord.CELL_SIZE;
        int n = work.size();
        for (int i = 0; i < n; i++) {
            int tx = work.get(i)[0];
            int ty = work.get(i)[1];
            float x0 = tx * ts;
            float x1 = x0 + ts;
            float y0 = ty * ts;
            float y1 = y0 + ts;
            if (onCellLine(x0, cellTes)) {
                offerWork(work, world, cell, tx - 1, ty);
            }
            if (onCellLine(x1, cellTes)) {
                offerWork(work, world, cell, tx + 1, ty);
            }
            if (onCellLine(y0, cellTes)) {
                offerWork(work, world, cell, tx, ty - 1);
            }
            if (onCellLine(y1, cellTes)) {
                offerWork(work, world, cell, tx, ty + 1);
            }
        }
    }

    private static boolean onCellLine(float tes, float cellTes) {
        float r = tes / cellTes;
        return Math.abs(r - Math.round(r)) * cellTes < 2f;
    }

    private static void offerWork(List<int[]> work, String world, EsmFile.LoadedCell cell, int tx, int ty) {
        long key = key(tx, ty);
        String id = world + "/" + key;
        if (bakedReady.contains(id) || !has(world, key)) {
            return;
        }
        if (cell != null && !landCoversTile(cell, tx, ty)) {
            return;
        }
        if (!patching.add(id)) {
            return;
        }
        work.add(new int[] { tx, ty });
        queueGenerating(world, cell, tx, ty);
    }

    private static void queueGenerating(String world, EsmFile.LoadedCell cell, int tx, int ty) {
        Tile mark = generatingPatch(world, cell, tx, ty);
        ready.computeIfAbsent(world, w -> new ConcurrentLinkedQueue<>()).add(mark);
    }

    private static Tile generatingPatch(String world, EsmFile.LoadedCell cell, int tx, int ty) {
        Tile mark = new Tile();
        mark.world = world;
        mark.x = tx;
        mark.y = ty;
        mark.generating = true;
        float tileTes = NavmeshDb.tileSizeTes();
        float x0 = tx * tileTes;
        float x1 = (tx + 1) * tileTes;
        float y0 = ty * tileTes;
        float y1 = (ty + 1) * tileTes;
        if (cell != null && !cell.interior) {
            int nx = Math.max(1, Math.round((x1 - x0) / GEN_STEP));
            int ny = Math.max(1, Math.round((y1 - y0) / GEN_STEP));
            float dx = (x1 - x0) / nx;
            float dy = (y1 - y0) / ny;
            float[] xs = new float[nx + 1];
            float[] ys = new float[ny + 1];
            float[][] zs = new float[ny + 1][nx + 1];
            for (int i = 0; i <= nx; i++) {
                xs[i] = x0 + i * dx;
            }
            for (int j = 0; j <= ny; j++) {
                ys[j] = y0 + j * dy;
            }
            for (int j = 0; j <= ny; j++) {
                for (int i = 0; i <= nx; i++) {
                    zs[j][i] = NavmeshBaker.tesHeight(cell, xs[i], ys[j]) + GEN_LIFT;
                }
            }
            for (int j = 0; j < ny; j++) {
                for (int i = 0; i < nx; i++) {
                    mark.tris.add(new float[] {
                        xs[i], ys[j], zs[j][i],
                        xs[i + 1], ys[j], zs[j][i + 1],
                        xs[i], ys[j + 1], zs[j + 1][i]
                    });
                    mark.tris.add(new float[] {
                        xs[i + 1], ys[j], zs[j][i + 1],
                        xs[i + 1], ys[j + 1], zs[j + 1][i + 1],
                        xs[i], ys[j + 1], zs[j + 1][i]
                    });
                }
            }
            return mark;
        }
        float z = generatingHeight(world, tx, ty);
        mark.tris.add(new float[] { x0, y0, z, x0, y1, z, x1, y0, z });
        mark.tris.add(new float[] { x1, y0, z, x0, y1, z, x1, y1, z });
        return mark;
    }

    private static float generatingHeight(String world, int tx, int ty) {
        ConcurrentHashMap<Long, Tile> map = stored.get(world);
        Tile have = map == null ? null : map.get(key(tx, ty));
        if (have == null || have.tris.isEmpty()) {
            return GEN_LIFT;
        }
        float sum = 0f;
        int n = 0;
        for (float[] tri : have.tris) {
            sum += tri[2] + tri[5] + tri[8];
            n += 3;
        }
        return n == 0 ? GEN_LIFT : sum / n + 4f;
    }

    private static List<Tile> snapshot(String world) {
        ConcurrentHashMap<Long, Tile> map = stored.get(world);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(map.values());
    }

    static void markSeen(String world, int tx, int ty) {
        have.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).putIfAbsent(key(tx, ty), Boolean.TRUE);
    }

    private static boolean has(String world, long key) {
        ConcurrentHashMap<Long, Boolean> map = have.get(world);
        return map != null && map.containsKey(key);
    }

    private static boolean isPatched(String world, long key) {
        ConcurrentHashMap<Long, Boolean> map = patched.get(world);
        return map != null && map.containsKey(key);
    }

    private static void markPatched(String world, long key) {
        patched.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).putIfAbsent(key, Boolean.TRUE);
    }

    private static void put(String world, Tile tile, String origin) {
        long key = key(tile.x, tile.y);
        ConcurrentHashMap<Long, Boolean> map = have.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        if (map.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        tile.world = world;
        stored.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).put(key, tile);
        tileCounts.computeIfAbsent(world, w -> new AtomicInteger()).incrementAndGet();
        polyCounts.computeIfAbsent(world, w -> new AtomicInteger()).addAndGet(Math.max(1, tile.polys));
        sources.merge(world, origin, (a, b) -> "db".equals(a) || "db".equals(b) ? "db" : b);
        NavmeshQuery.addTile(world, tile);
        ready.computeIfAbsent(world, w -> new ConcurrentLinkedQueue<>()).add(tile);
    }

    private static void replace(String world, Tile tile) {
        long key = key(tile.x, tile.y);
        tile.world = world;
        ConcurrentHashMap<Long, Tile> map = stored.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        Tile old = map.put(key, tile);
        have.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).put(key, Boolean.TRUE);
        if (old == null) {
            tileCounts.computeIfAbsent(world, w -> new AtomicInteger()).incrementAndGet();
            polyCounts.computeIfAbsent(world, w -> new AtomicInteger()).addAndGet(Math.max(1, tile.polys));
        } else {
            polyCounts.computeIfAbsent(world, w -> new AtomicInteger())
                .addAndGet(Math.max(1, tile.polys) - Math.max(1, old.polys));
        }
        patchCounts.computeIfAbsent(world, w -> new AtomicInteger()).incrementAndGet();
        NavmeshQuery.replaceTile(world, tile);
        ready.computeIfAbsent(world, w -> new ConcurrentLinkedQueue<>()).add(tile);
    }

    public static long key(int tx, int ty) {
        return ((long) tx << 32) | (ty & 0xffffffffL);
    }

    public static final class Tile {
        public String world = "";
        public int x;
        public int y;
        public int polys;
        public boolean tesSpace = true;
        public boolean generating;
        public PolyMesh mesh;
        public PolyMeshDetail detail;
        public final List<float[]> tris = new ArrayList<>();
    }
}
