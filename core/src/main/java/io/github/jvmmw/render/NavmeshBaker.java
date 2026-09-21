package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;

import org.recast4j.recast.AreaModification;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastBuilder.RecastBuilderResult;
import org.recast4j.recast.RecastBuilderConfig;
import org.recast4j.recast.RecastConfig;
import org.recast4j.recast.RecastConstants.PartitionType;
import org.recast4j.recast.geom.SimpleInputGeomProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Walkable Recast surface for the loaded cells. Prefers OpenMW’s navmesh.db
 * (umo) and keeps tiles when the 5×5 moves. If that file is missing, bakes
 * the center cell from land and shack collision. Cracked sqlite tiles are
 * rebaked one Recast square at a time in TES space so they join the live
 * mesh. Straight wander dests query the same tiles through Detour.
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
    public static String lastBakeWhy = "";

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

    /**
     * One Recast tile in sqlite space (TES x/height/y). Used to fill a cracked
     * db tile without rebaking the rest of Town.
     */
    public static NavmeshCache.Tile bakeRecastTile(CollisionWorld collision, EsmFile.LoadedCell cell, int tx, int ty) {
        return bakeRecastTile(gatherTesRecast(collision, cell, tx, ty), tx, ty);
    }

    public static NavmeshCache.Tile bakeRecastTile(float[] packed, int tx, int ty) {
        if (packed == null || packed.length < 9) {
            return null;
        }
        int[] faces = new int[packed.length / 3];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = i;
        }
        SimpleInputGeomProvider geom = new SimpleInputGeomProvider(packed, faces);
        RecastConfig cfg = recastConfig();
        float ymin = packed[1];
        float ymax = packed[1];
        for (int i = 1; i < packed.length; i += 3) {
            ymin = Math.min(ymin, packed[i]);
            ymax = Math.max(ymax, packed[i]);
        }
        float[] bmin = { 0f, ymin - 1f, 0f };
        float[] bmax = { TILE_SIZE * CELL_SIZE, ymax + 1f, TILE_SIZE * CELL_SIZE };
        RecastBuilderConfig tileCfg = new RecastBuilderConfig(cfg, bmin, bmax, tx, ty);
        RecastBuilderResult built = new RecastBuilder().build(geom, tileCfg);
        if (built == null) {
            lastBakeWhy = "built-null";
            return null;
        }
        PolyMesh mesh = built.getMesh();
        PolyMeshDetail detail = built.getMeshDetail();
        if (mesh == null || mesh.npolys <= 0) {
            lastBakeWhy = "npolys=" + (mesh == null ? "null" : mesh.npolys)
                + " nverts=" + (mesh == null ? 0 : mesh.nverts)
                + " cfg=" + tileCfg.bmin[0] + "," + tileCfg.bmin[2]
                + " v0=" + packed[0] + "," + packed[2];
            return null;
        }
        NavmeshCache.Tile out = new NavmeshCache.Tile();
        out.x = tx;
        out.y = ty;
        out.mesh = mesh;
        out.detail = detail;
        out.tesSpace = true;
        out.polys = mesh.npolys;
        float inv = 1f / SCALE;
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
                        int o = k * 3;
                        tri[o] = detail.verts[vi * 3] * inv;
                        tri[o + 1] = detail.verts[vi * 3 + 2] * inv;
                        tri[o + 2] = detail.verts[vi * 3 + 1] * inv + LIFT;
                    }
                    out.tris.add(tri);
                }
            }
        }
        lastBakeWhy = "ok polys=" + mesh.npolys + " tris=" + out.tris.size();
        return out.tris.isEmpty() ? null : out;
    }

    /** Recast verts (x, height, tesY) * scale for one tile plus Recast border. */
    public static float[] gatherTesRecast(CollisionWorld collision, EsmFile.LoadedCell cell, int tx, int ty) {
        float tileTes = TILE_SIZE * CELL_SIZE / SCALE;
        float pad = BORDER * CELL_SIZE / SCALE;
        float minX = tx * tileTes - pad;
        float maxX = (tx + 1) * tileTes + pad;
        float minY = ty * tileTes - pad;
        float maxY = (ty + 1) * tileTes + pad;
        List<Float> recast = new ArrayList<>();
        if (cell != null) {
            if (cell.land != null && landOverlap(cell.land, minX, maxX, minY, maxY)) {
                addLandTes(cell.land, recast, minX, maxX, minY, maxY);
            }
            for (LandRecord land : cell.lands) {
                if (land != cell.land && landOverlap(land, minX, maxX, minY, maxY)) {
                    addLandTes(land, recast, minX, maxX, minY, maxY);
                }
            }
            addSeamStitch(cell, recast, minX, maxX, minY, maxY);
        }
        List<Float> gl = new ArrayList<>();
        if (collision != null) {
            collision.appendObjectTris(minX, maxX, -maxY, -minY, gl);
        }
        for (int i = 0; i + 2 < gl.size(); i += 3) {
            recast.add(gl.get(i) * SCALE);
            recast.add(gl.get(i + 1) * SCALE);
            recast.add(-gl.get(i + 2) * SCALE);
        }
        float[] out = new float[recast.size()];
        for (int i = 0; i < recast.size(); i++) {
            out[i] = recast.get(i);
        }
        return out;
    }

    /** Fills the thin TES cell-edge crack Recast otherwise erodes away. */
    private static void addSeamStitch(EsmFile.LoadedCell cell, List<Float> recast, float minX, float maxX, float minY,
        float maxY) {
        float cellTes = LandRecord.CELL_SIZE;
        float half = 128f;
        float step = 128f;
        int gx0 = (int) Math.floor(minX / cellTes);
        int gx1 = (int) Math.ceil(maxX / cellTes);
        int gy0 = (int) Math.floor(minY / cellTes);
        int gy1 = (int) Math.ceil(maxY / cellTes);
        for (int gx = gx0; gx <= gx1; gx++) {
            float x = gx * cellTes;
            if (x < minX - 1f || x > maxX + 1f) {
                continue;
            }
            for (float y = minY; y < maxY; y += step) {
                float y1 = Math.min(y + step, maxY);
                addStitchQuad(cell, recast, x - half, y, x + half, y, x - half, y1, x + half, y1);
            }
        }
        for (int gy = gy0; gy <= gy1; gy++) {
            float y = gy * cellTes;
            if (y < minY - 1f || y > maxY + 1f) {
                continue;
            }
            for (float x = minX; x < maxX; x += step) {
                float x1 = Math.min(x + step, maxX);
                addStitchQuad(cell, recast, x, y - half, x1, y - half, x, y + half, x1, y + half);
            }
        }
    }

    private static void addStitchQuad(EsmFile.LoadedCell cell, List<Float> recast, float x00, float y00, float x10,
        float y10, float x01, float y01, float x11, float y11) {
        addStitchVert(cell, recast, x00, y00);
        addStitchVert(cell, recast, x00, y01);
        addStitchVert(cell, recast, x10, y10);
        addStitchVert(cell, recast, x10, y10);
        addStitchVert(cell, recast, x00, y01);
        addStitchVert(cell, recast, x11, y11);
    }

    private static void addStitchVert(EsmFile.LoadedCell cell, List<Float> recast, float tesX, float tesY) {
        recast.add(tesX * SCALE);
        recast.add(tesHeight(cell, tesX, tesY) * SCALE);
        recast.add(tesY * SCALE);
    }

    public static float tesHeight(EsmFile.LoadedCell cell, float tesX, float tesY) {
        LandRecord land = landRecord(cell, tesX, tesY);
        if (land == null) {
            return 0f;
        }
        int size = LandRecord.SIZE;
        float step = LandRecord.CELL_SIZE / (float) (size - 1);
        float lx = tesX - land.gridX * (float) LandRecord.CELL_SIZE;
        float ly = tesY - land.gridY * (float) LandRecord.CELL_SIZE;
        float fx = lx / step;
        float fy = ly / step;
        int x0 = Math.max(0, Math.min(size - 2, (int) Math.floor(fx)));
        int y0 = Math.max(0, Math.min(size - 2, (int) Math.floor(fy)));
        float tx = Math.max(0f, Math.min(1f, fx - x0));
        float ty = Math.max(0f, Math.min(1f, fy - y0));
        float h00 = land.height(x0, y0);
        float h10 = land.height(x0 + 1, y0);
        float h01 = land.height(x0, y0 + 1);
        float h11 = land.height(x0 + 1, y0 + 1);
        return h00 * (1f - tx) * (1f - ty) + h10 * tx * (1f - ty) + h01 * (1f - tx) * ty + h11 * tx * ty;
    }

    public static boolean walkableLand(EsmFile.LoadedCell cell, float tesX, float tesY) {
        if (cell == null || cell.interior || landRecord(cell, tesX, tesY) == null) {
            return false;
        }
        return tesHeight(cell, tesX, tesY) > WaterMesh.HEIGHT;
    }

    private static LandRecord landRecord(EsmFile.LoadedCell cell, float tesX, float tesY) {
        int gx = LandRecord.cellGrid(tesX);
        int gy = LandRecord.cellGrid(tesY);
        LandRecord land = landAt(cell, gx, gy);
        if (land == null && tesX == gx * (float) LandRecord.CELL_SIZE) {
            land = landAt(cell, gx - 1, gy);
        }
        if (land == null && tesY == gy * (float) LandRecord.CELL_SIZE) {
            land = landAt(cell, gx, gy - 1);
        }
        return land;
    }

    private static LandRecord landAt(EsmFile.LoadedCell cell, int gx, int gy) {
        if (cell == null) {
            return null;
        }
        if (cell.land != null && cell.land.gridX == gx && cell.land.gridY == gy) {
            return cell.land;
        }
        for (LandRecord land : cell.lands) {
            if (land.gridX == gx && land.gridY == gy) {
                return land;
            }
        }
        return null;
    }

    private static boolean landOverlap(LandRecord land, float minX, float maxX, float minY, float maxY) {
        float ox = land.gridX * (float) LandRecord.CELL_SIZE;
        float oy = land.gridY * (float) LandRecord.CELL_SIZE;
        float x1 = ox + LandRecord.CELL_SIZE;
        float y1 = oy + LandRecord.CELL_SIZE;
        return ox <= maxX && x1 >= minX && oy <= maxY && y1 >= minY;
    }

    private static void addLandTes(LandRecord land, List<Float> recast, float minX, float maxX, float minY,
        float maxY) {
        int size = LandRecord.SIZE;
        float step = LandRecord.CELL_SIZE / (float) (size - 1);
        float ox = land.gridX * (float) LandRecord.CELL_SIZE;
        float oy = land.gridY * (float) LandRecord.CELL_SIZE;
        for (int y = 0; y < size - 1; y++) {
            float vy = oy + y * step;
            if (vy + step < minY || vy > maxY) {
                continue;
            }
            for (int x = 0; x < size - 1; x++) {
                float vx = ox + x * step;
                if (vx + step < minX || vx > maxX) {
                    continue;
                }
                addLandTesVert(recast, land, ox, oy, step, x, y);
                addLandTesVert(recast, land, ox, oy, step, x, y + 1);
                addLandTesVert(recast, land, ox, oy, step, x + 1, y);
                addLandTesVert(recast, land, ox, oy, step, x + 1, y);
                addLandTesVert(recast, land, ox, oy, step, x, y + 1);
                addLandTesVert(recast, land, ox, oy, step, x + 1, y + 1);
            }
        }
    }

    private static void addLandTesVert(List<Float> recast, LandRecord land, float ox, float oy, float step, int x,
        int y) {
        recast.add((ox + x * step) * SCALE);
        recast.add(land.height(x, y) * SCALE);
        recast.add((oy + y * step) * SCALE);
    }

    static RecastConfig recastConfig() {
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
