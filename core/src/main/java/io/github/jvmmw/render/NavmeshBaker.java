package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;

import org.recast4j.recast.AreaModification;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastBuilder.RecastBuilderResult;
import org.recast4j.recast.RecastConfig;
import org.recast4j.recast.RecastConstants.PartitionType;
import org.recast4j.recast.geom.SimpleInputGeomProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Walkable Recast surface for the loaded cells. Prefers OpenMW’s navmesh.db
 * (umo) and keeps tiles when the 5×5 moves. If that file is missing, bakes
 * the center cell from land and shack collision. Straight wander dests
 * query the same tiles through Detour.
 */
public final class NavmeshBaker {
    public static final float SCALE = 0.029411764705882353f;
    static final float HALF_X = 29.279995f;
    static final float HALF_Y = 28.479998f;
    static final float HALF_Z = 66.5f;
    private static final float CELL_SIZE = 0.2f;
    private static final float CELL_HEIGHT = 0.2f;
    private static final int TILE_SIZE = 128;
    private static final int BORDER = 16;
    private static final float LIFT = 6f;

    static float walkableHeight() {
        return 2f * HALF_Z * SCALE;
    }

    static float walkableRadius() {
        return (float) (Math.max(HALF_X, HALF_Y) * Math.sqrt(2.0) * SCALE);
    }

    static float walkableClimb() {
        return 34f * SCALE;
    }

    private NavmeshBaker() {
    }

    public static Result bake(CollisionWorld collision, EsmFile.LoadedCell cell) {
        NavmeshCache.request(cell, collision);
        return new Result();
    }

    static List<NavmeshCache.Tile> bakeRuntime(CollisionWorld collision, EsmFile.LoadedCell cell) {
        List<NavmeshCache.Tile> out = new ArrayList<>();
        if (collision == null || cell == null) {
            return out;
        }
        List<Float> gl = new ArrayList<>();
        float minX;
        float maxX;
        float minZ;
        float maxZ;
        if (cell.interior) {
            minX = minZ = Float.NEGATIVE_INFINITY;
            maxX = maxZ = Float.POSITIVE_INFINITY;
        } else {
            float originX = cell.gridX * (float) LandRecord.CELL_SIZE;
            float originY = cell.gridY * (float) LandRecord.CELL_SIZE;
            float pad = BORDER * CELL_SIZE / SCALE;
            minX = originX - pad;
            maxX = originX + LandRecord.CELL_SIZE + pad;
            minZ = -(originY + LandRecord.CELL_SIZE) - pad;
            maxZ = -originY + pad;
            LandRecord land = centerLand(cell);
            if (land != null) {
                addLand(land, gl);
            }
        }
        collision.appendObjectTris(minX, maxX, minZ, maxZ, gl);
        if (gl.size() < 9) {
            return out;
        }
        float[] verts = new float[gl.size()];
        int[] faces = new int[gl.size() / 3];
        for (int i = 0; i < gl.size(); i += 3) {
            verts[i] = gl.get(i) * SCALE;
            verts[i + 1] = gl.get(i + 1) * SCALE;
            verts[i + 2] = gl.get(i + 2) * SCALE;
            faces[i / 3] = i / 3;
        }
        SimpleInputGeomProvider geom = new SimpleInputGeomProvider(verts, faces);
        RecastConfig cfg = recastConfig();
        RecastBuilder builder = new RecastBuilder();
        @SuppressWarnings("unchecked")
        List<RecastBuilderResult> tiles = builder.buildTiles(geom, cfg, Optional.empty());
        float inv = 1f / SCALE;
        for (RecastBuilderResult tile : tiles) {
            if (tile == null) {
                continue;
            }
            PolyMesh mesh = tile.getMesh();
            PolyMeshDetail detail = tile.getMeshDetail();
            NavmeshCache.Tile packed = new NavmeshCache.Tile();
            packed.x = tile.tileX;
            packed.y = tile.tileZ;
            packed.mesh = mesh;
            packed.detail = detail;
            packed.tesSpace = false;
            if (mesh != null && mesh.npolys > 0) {
                packed.polys = mesh.npolys;
            }
            if (detail != null && detail.ntris > 0) {
                for (int m = 0; m < detail.nmeshes; m++) {
                    int vertBase = detail.meshes[m * 4];
                    int triBase = detail.meshes[m * 4 + 2];
                    int triCount = detail.meshes[m * 4 + 3];
                    for (int t = 0; t < triCount; t++) {
                        int to = (triBase + t) * 4;
                        float[] tri = new float[9];
                        for (int k = 0; k < 3; k++) {
                            int vi = vertBase + detail.tris[to + k];
                            float glX = detail.verts[vi * 3] * inv;
                            float glY = detail.verts[vi * 3 + 1] * inv;
                            float glZ = detail.verts[vi * 3 + 2] * inv;
                            int o = k * 3;
                            tri[o] = glX;
                            tri[o + 1] = -glZ;
                            tri[o + 2] = glY + LIFT;
                        }
                        packed.tris.add(tri);
                    }
                }
            }
            if (packed.polys > 0 || !packed.tris.isEmpty()) {
                if (packed.polys <= 0) {
                    packed.polys = 1;
                }
                out.add(packed);
            }
        }
        return out;
    }

    private static RecastConfig recastConfig() {
        float height = walkableHeight();
        float radius = walkableRadius();
        float climb = walkableClimb();
        return new RecastConfig(true, TILE_SIZE, TILE_SIZE, BORDER, PartitionType.WATERSHED, CELL_SIZE, CELL_HEIGHT,
            46f, true, true, true, height, radius, climb, 64 * CELL_SIZE * CELL_SIZE, 400 * CELL_SIZE * CELL_SIZE,
            12f, 1.3f, 6, true, 6f, 1f, new AreaModification(63));
    }

    private static LandRecord centerLand(EsmFile.LoadedCell cell) {
        if (cell.land != null && cell.land.gridX == cell.gridX && cell.land.gridY == cell.gridY) {
            return cell.land;
        }
        for (LandRecord land : cell.lands) {
            if (land.gridX == cell.gridX && land.gridY == cell.gridY) {
                return land;
            }
        }
        return cell.land;
    }

    private static void addLand(LandRecord land, List<Float> gl) {
        int size = LandRecord.SIZE;
        float step = LandRecord.CELL_SIZE / (float) (size - 1);
        float ox = land.gridX * (float) LandRecord.CELL_SIZE;
        float oy = land.gridY * (float) LandRecord.CELL_SIZE;
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                addLandVert(gl, land, ox, oy, step, x, y);
                addLandVert(gl, land, ox, oy, step, x + 1, y);
                addLandVert(gl, land, ox, oy, step, x, y + 1);
                addLandVert(gl, land, ox, oy, step, x + 1, y);
                addLandVert(gl, land, ox, oy, step, x + 1, y + 1);
                addLandVert(gl, land, ox, oy, step, x, y + 1);
            }
        }
    }

    private static void addLandVert(List<Float> gl, LandRecord land, float ox, float oy, float step, int x, int y) {
        gl.add(ox + x * step);
        gl.add(land.height(x, y));
        gl.add(-(oy + y * step));
    }

    public static final class Result {
        public int polys;
        public int tiles;
        public String source = "none";
        public final List<float[]> tris = new ArrayList<>();
    }
}
