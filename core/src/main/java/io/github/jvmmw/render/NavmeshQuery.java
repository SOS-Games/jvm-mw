package io.github.jvmmw.render;

import org.recast4j.detour.DefaultQueryFilter;
import org.recast4j.detour.FindNearestPolyResult;
import org.recast4j.detour.MeshData;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshBuilder;
import org.recast4j.detour.NavMeshDataCreateParams;
import org.recast4j.detour.NavMeshParams;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.Result;
import org.recast4j.detour.StraightPathItem;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walkable-path corners on the F6 carpet. Tiles are the same Recast blobs
 * the overlay already cached; this class turns them into one Detour mesh
 * per world (outdoors vs a named interior) and answers start-to-end queries.
 * Wander that already has a usable pathgrid stays on F5. Only the random
 * straight dest uses this, and only after the worker has finished the
 * current batch — a miss is still a straight line.
 */
public final class NavmeshQuery {
    private static final int MAX_TILES = 8192;
    private static final int MAX_POLYS = 4096;
    private static final int NVP = 6;
    private static final int MAX_PATH = 1024;
    private static final int FLAG_WALK = 1;
    private static final float TILE_NAV = 128f * 0.2f;
    private static final ConcurrentHashMap<String, World> worlds = new ConcurrentHashMap<>();

    private NavmeshQuery() {
    }

    public static void addTile(String world, NavmeshCache.Tile tile) {
        if (world == null || world.isEmpty() || tile == null || tile.mesh == null || tile.mesh.npolys <= 0) {
            return;
        }
        MeshData data = meshData(tile);
        if (data == null) {
            return;
        }
        long key = NavmeshCache.key(tile.x, tile.y);
        World w = worlds.computeIfAbsent(world, NavmeshQuery::newWorld);
        synchronized (w) {
            if (w.tiles.isEmpty()) {
                w.tesSpace = tile.tesSpace;
            }
            if (!w.tiles.add(key)) {
                return;
            }
            try {
                w.mesh.addTile(data, 0, 0);
            } catch (Exception e) {
                w.tiles.remove(key);
            }
        }
    }

    static int lastPath(String world) {
        World w = worlds.get(world);
        if (w == null) {
            return 0;
        }
        synchronized (w) {
            return w.lastPath;
        }
    }

    /** Empty while sqlite/Recast is still running, or when Detour has no path. */
    static List<float[]> wanderPath(String world, float x0, float y0, float z0, float x1, float y1, float z1) {
        if (world == null || world.isEmpty() || NavmeshCache.busy()) {
            return List.of();
        }
        return find(world, x0, y0, z0, x1, y1, z1);
    }

    public static List<float[]> find(String world, float x0, float y0, float z0, float x1, float y1, float z1) {
        World w = worlds.get(world);
        if (w == null) {
            return List.of();
        }
        float[] start = recast(w, x0, y0, z0);
        float[] end = recast(w, x1, y1, z1);
        float[] half = {
            NavmeshBaker.HALF_X * 4f * NavmeshBaker.SCALE,
            NavmeshBaker.HALF_Z * 4f * NavmeshBaker.SCALE,
            NavmeshBaker.HALF_Y * 4f * NavmeshBaker.SCALE
        };
        synchronized (w) {
            if (w.tiles.isEmpty()) {
                return List.of();
            }
            Result<FindNearestPolyResult> nearStart = w.query.findNearestPoly(start, half, w.filter);
            Result<FindNearestPolyResult> nearEnd = w.query.findNearestPoly(end, half, w.filter);
            long startRef = ref(nearStart);
            long endRef = ref(nearEnd);
            if (startRef == 0L || endRef == 0L) {
                return List.of();
            }
            float[] startPos = pos(nearStart, start);
            float[] endPos = pos(nearEnd, end);
            Result<List<Long>> polys = w.query.findPath(startRef, endRef, startPos, endPos, w.filter);
            if (polys == null || polys.result == null || polys.result.isEmpty()) {
                return List.of();
            }
            List<Long> polyPath = polys.result;
            if (polyPath.size() > MAX_PATH) {
                polyPath = polyPath.subList(0, MAX_PATH);
            }
            Result<List<StraightPathItem>> straight = w.query.findStraightPath(startPos, endPos, polyPath, MAX_PATH, 0);
            if (straight == null || straight.result == null || straight.result.size() < 2) {
                return List.of();
            }
            List<float[]> out = new ArrayList<>(straight.result.size());
            for (StraightPathItem item : straight.result) {
                if (item == null || item.getPos() == null) {
                    continue;
                }
                out.add(tes(w, item.getPos()));
            }
            if (out.size() >= 2) {
                w.lastPath = out.size();
            }
            return out.size() >= 2 ? out : List.of();
        }
    }

    private static World newWorld(String ignored) {
        NavMeshParams params = new NavMeshParams();
        params.orig[0] = 0f;
        params.orig[1] = 0f;
        params.orig[2] = 0f;
        params.tileWidth = TILE_NAV;
        params.tileHeight = TILE_NAV;
        params.maxTiles = MAX_TILES;
        params.maxPolys = MAX_POLYS;
        World w = new World();
        w.mesh = new NavMesh(params, NVP);
        w.query = new NavMeshQuery(w.mesh);
        w.filter = new DefaultQueryFilter();
        w.filter.setIncludeFlags(FLAG_WALK);
        w.filter.setExcludeFlags(0);
        return w;
    }

    private static MeshData meshData(NavmeshCache.Tile tile) {
        PolyMesh mesh = tile.mesh;
        PolyMeshDetail detail = tile.detail;
        NavMeshDataCreateParams p = new NavMeshDataCreateParams();
        p.verts = mesh.verts;
        p.vertCount = mesh.nverts;
        p.polys = mesh.polys;
        p.polyFlags = walkFlags(mesh);
        p.polyAreas = mesh.areas;
        p.polyCount = mesh.npolys;
        p.nvp = mesh.nvp;
        if (detail != null && detail.nmeshes > 0) {
            p.detailMeshes = detail.meshes;
            p.detailVerts = detail.verts;
            p.detailVertsCount = detail.nverts;
            p.detailTris = detail.tris;
            p.detailTriCount = detail.ntris;
        }
        p.walkableHeight = NavmeshBaker.walkableHeight();
        p.walkableRadius = NavmeshBaker.walkableRadius();
        p.walkableClimb = NavmeshBaker.walkableClimb();
        p.bmin = new float[] { mesh.bmin[0], mesh.bmin[1], mesh.bmin[2] };
        p.bmax = new float[] { mesh.bmax[0], mesh.bmax[1], mesh.bmax[2] };
        p.cs = mesh.cs;
        p.ch = mesh.ch;
        p.buildBvTree = true;
        p.tileX = tile.x;
        p.tileZ = tile.y;
        p.tileLayer = 0;
        try {
            return NavMeshBuilder.createNavMeshData(p);
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] walkFlags(PolyMesh mesh) {
        int[] flags = new int[mesh.npolys];
        int[] src = mesh.flags;
        for (int i = 0; i < mesh.npolys; i++) {
            int f = src != null && i < src.length ? src[i] : 0;
            int area = mesh.areas != null && i < mesh.areas.length ? mesh.areas[i] : 0;
            flags[i] = f != 0 ? f : (area != 0 ? FLAG_WALK : 0);
        }
        return flags;
    }

    private static long ref(Result<FindNearestPolyResult> r) {
        if (r == null || r.result == null) {
            return 0L;
        }
        return r.result.getNearestRef();
    }

    private static float[] pos(Result<FindNearestPolyResult> r, float[] fallback) {
        float[] p = r.result.getNearestPos();
        return p != null ? p : fallback;
    }

    private static float[] recast(World w, float tesX, float tesY, float tesZ) {
        float s = NavmeshBaker.SCALE;
        return new float[] { tesX * s, tesZ * s, (w.tesSpace ? tesY : -tesY) * s };
    }

    private static float[] tes(World w, float[] recast) {
        float inv = 1f / NavmeshBaker.SCALE;
        float tesY = recast[2] * inv;
        if (!w.tesSpace) {
            tesY = -tesY;
        }
        return new float[] { recast[0] * inv, tesY, recast[1] * inv };
    }

    private static final class World {
        NavMesh mesh;
        NavMeshQuery query;
        DefaultQueryFilter filter;
        boolean tesSpace = true;
        final Set<Long> tiles = new HashSet<>();
        int lastPath;
    }
}
