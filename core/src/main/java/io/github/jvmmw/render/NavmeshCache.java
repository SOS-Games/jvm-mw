package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;

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
 * Sqlite and Recast run on one worker; the overlay only uploads tiles we
 * do not already have. Detour paths use those same tiles once the worker
 * finishes a batch.
 */
public final class NavmeshCache {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> have
        = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> tileCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> polyCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> sources = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Tile>> ready
        = new ConcurrentHashMap<>();
    private static final Set<String> interiorsDone = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger jobs = new AtomicInteger();
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "navmesh");
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
        }
    }

    static void markSeen(String world, int tx, int ty) {
        have.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).putIfAbsent(key(tx, ty), Boolean.TRUE);
    }

    private static boolean has(String world, long key) {
        ConcurrentHashMap<Long, Boolean> map = have.get(world);
        return map != null && map.containsKey(key);
    }

    private static void put(String world, Tile tile, String origin) {
        long key = key(tile.x, tile.y);
        ConcurrentHashMap<Long, Boolean> map = have.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        if (map.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        tile.world = world;
        tileCounts.computeIfAbsent(world, w -> new AtomicInteger()).incrementAndGet();
        polyCounts.computeIfAbsent(world, w -> new AtomicInteger()).addAndGet(Math.max(1, tile.polys));
        sources.merge(world, origin, (a, b) -> "db".equals(a) || "db".equals(b) ? "db" : b);
        NavmeshQuery.addTile(world, tile);
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
        public PolyMesh mesh;
        public PolyMeshDetail detail;
        public final List<float[]> tris = new ArrayList<>();
    }
}
