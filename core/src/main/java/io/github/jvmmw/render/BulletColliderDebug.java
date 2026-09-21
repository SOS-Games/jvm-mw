package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * F7 overlay of Bullet colliders. Yellow is land (HeightMap). Orange is
 * docks and kit (World). Off at load. The meshes are built when F7 is on and dropped when it is off.
 * Water cameras skip this.
 */
public final class BulletColliderDebug {
    public static boolean visible = false;

    private static final float[] RGB_LAND = {0.95f, 0.85f, 0.12f};
    private static final float[] RGB_WORLD = {1f, 0.4f, 0.08f};
    private static final float ALPHA_LAND = 0.4f;
    private static final float ALPHA_WORLD = 0.55f;
    private static final float LIFT = 4f;
    private static final float STEP = LandRecord.CELL_SIZE / (float) (LandRecord.SIZE - 1);
    private static final int MAX_VERTS = 65000;

    private final List<MeshGpu> gpus = new ArrayList<>();
    private SceneNode group;
    private final Vector3 v = new Vector3();
    private final Matrix4 world = new Matrix4();
    private final Matrix4 invCell = new Matrix4();

    public void attach(SceneNode cellRoot, List<CollisionMesh.Pending> pending, List<LandRecord> lands) {
        dispose();
        if (cellRoot == null) {
            return;
        }
        invCell.set(cellRoot.world).inv();
        List<float[]> landTris = new ArrayList<>();
        List<float[]> worldTris = new ArrayList<>();
        if (lands != null) {
            for (LandRecord land : lands) {
                addLandTris(land, landTris);
            }
        }
        if (pending != null) {
            for (CollisionMesh.Pending pnd : pending) {
                addWorldTris(pnd, worldTris);
            }
        }
        if (landTris.isEmpty() && worldTris.isEmpty()) {
            return;
        }
        Gdx.app.log("JVM-MW", "collider overlay landTris=" + landTris.size()
            + " worldTris=" + worldTris.size());
        group = new SceneNode();
        group.name = "bullet-debug";
        group.debugDraw = true;
        addChunks(landTris, RGB_LAND, ALPHA_LAND);
        addChunks(worldTris, RGB_WORLD, ALPHA_WORLD);
        cellRoot.addChild(group);
    }

    public boolean attached() {
        return group != null;
    }

    public static boolean toggleVisible() {
        visible = !visible;
        return visible;
    }

    public void dispose() {
        if (group != null) {
            group.removeFromParent();
            group = null;
        }
        for (MeshGpu gpu : gpus) {
            gpu.dispose();
        }
        gpus.clear();
    }

    private void addLandTris(LandRecord land, List<float[]> out) {
        float originX = land.gridX * (float) LandRecord.CELL_SIZE;
        float originY = land.gridY * (float) LandRecord.CELL_SIZE;
        int n = LandRecord.SIZE;
        for (int y = 0; y < n - 1; y++) {
            for (int x = 0; x < n - 1; x++) {
                landVert(v, originX, originY, land, x, y);
                float ax = v.x;
                float ay = v.y;
                float az = v.z;
                landVert(v, originX, originY, land, x + 1, y);
                float bx = v.x;
                float by = v.y;
                float bz = v.z;
                landVert(v, originX, originY, land, x, y + 1);
                float cx = v.x;
                float cy = v.y;
                float cz = v.z;
                landVert(v, originX, originY, land, x + 1, y + 1);
                float dx = v.x;
                float dy = v.y;
                float dz = v.z;
                out.add(toCell(ax, ay, az, bx, by, bz, cx, cy, cz));
                out.add(toCell(bx, by, bz, dx, dy, dz, cx, cy, cz));
            }
        }
    }

    private void landVert(Vector3 out, float originX, float originY, LandRecord land, int x, int y) {
        float tesX = originX + x * STEP;
        float tesY = originY + y * STEP;
        out.set(tesX, land.height(x, y), -tesY);
    }

    private void addWorldTris(CollisionMesh.Pending pnd, List<float[]> out) {
        if (pnd.mesh == null || pnd.mesh.isEmpty() || pnd.node == null) {
            return;
        }
        world.set(pnd.node.world);
        float[] src = pnd.mesh.tris;
        for (int i = 0; i + 8 < src.length; i += 9) {
            float[] tri = new float[9];
            for (int k = 0; k < 3; k++) {
                v.set(src[i + k * 3], src[i + k * 3 + 1], src[i + k * 3 + 2]).mul(world);
                v.y += LIFT;
                v.mul(invCell);
                tri[k * 3] = v.x;
                tri[k * 3 + 1] = v.y;
                tri[k * 3 + 2] = v.z;
            }
            out.add(tri);
        }
    }

    private float[] toCell(float ax, float ay, float az, float bx, float by, float bz,
        float cx, float cy, float cz) {
        float[] tri = new float[9];
        putCell(tri, 0, ax, ay, az);
        putCell(tri, 3, bx, by, bz);
        putCell(tri, 6, cx, cy, cz);
        return tri;
    }

    private void putCell(float[] tri, int o, float x, float y, float z) {
        v.set(x, y + LIFT, z).mul(invCell);
        tri[o] = v.x;
        tri[o + 1] = v.y;
        tri[o + 2] = v.z;
    }

    private void addChunks(List<float[]> tris, float[] rgb, float alpha) {
        int start = 0;
        while (start < tris.size()) {
            int n = Math.min(tris.size() - start, MAX_VERTS / 3);
            group.meshes.add(new MeshInstance(buildChunk(tris, start, n, rgb, alpha)));
            start += n;
        }
    }

    private MeshGpu buildChunk(List<float[]> tris, int start, int n, float[] rgb, float alpha) {
        float[] interleaved = new float[n * 3 * MeshGpu.STRIDE_FLOATS];
        short[] indices = new short[n * 3];
        int base = 0;
        for (int t = 0; t < n; t++) {
            float[] tri = tris.get(start + t);
            float ax = tri[3] - tri[0];
            float ay = tri[4] - tri[1];
            float az = tri[5] - tri[2];
            float bx = tri[6] - tri[0];
            float by = tri[7] - tri[1];
            float bz = tri[8] - tri[2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                nx = 0f;
                ny = 1f;
                nz = 0f;
            }
            for (int k = 0; k < 3; k++) {
                int o = k * 3;
                int p = base * MeshGpu.STRIDE_FLOATS;
                interleaved[p] = tri[o];
                interleaved[p + 1] = tri[o + 1];
                interleaved[p + 2] = tri[o + 2];
                interleaved[p + 3] = nx;
                interleaved[p + 4] = ny;
                interleaved[p + 5] = nz;
                interleaved[p + 6] = 0f;
                interleaved[p + 7] = 0f;
                interleaved[p + 8] = rgb[0];
                interleaved[p + 9] = rgb[1];
                interleaved[p + 10] = rgb[2];
                interleaved[p + 11] = alpha;
                indices[t * 3 + k] = (short) base;
                base++;
            }
        }
        MeshGpu mesh = new MeshGpu(interleaved, indices);
        int white = GpuCache.whiteId();
        mesh.primitive = GL20.GL_TRIANGLES;
        mesh.baseTex = white;
        mesh.darkTex = white;
        mesh.detailTex = white;
        mesh.glowTex = white;
        mesh.textureId = white;
        mesh.colorMode = MeshGpu.COLOR_EMISSION;
        mesh.ambient[0] = mesh.ambient[1] = mesh.ambient[2] = 0f;
        mesh.diffuse[0] = mesh.diffuse[1] = mesh.diffuse[2] = 0f;
        mesh.cull = false;
        mesh.alphaBlend = true;
        mesh.depthWrite = false;
        mesh.matAlpha = alpha;
        mesh.noSorter = true;
        gpus.add(mesh);
        return mesh;
    }
}
