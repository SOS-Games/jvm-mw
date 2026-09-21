package io.github.jvmmw.render;

import com.badlogic.gdx.graphics.GL20;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Green carpet of Recast walkable polys. F6 hides it. Tiles stay on this
 * overlay when the walk grid swaps — only new Recast tiles are uploaded.
 * Verts are TES xyz parented under the cell root so the same −90° X as
 * land applies. Water cameras skip this. Wander still uses the pathgrid.
 */
public final class NavmeshDebug {
    public static boolean visible = true;

    private static final float[] RGB = {0.25f, 0.85f, 0.35f};
    public static final int MAX_VERTS = 65000;
    private static final Map<String, NavmeshDebug> WORLDS = new HashMap<>();

    private final List<MeshGpu> gpus = new ArrayList<>();
    private SceneNode group;

    static NavmeshDebug of(String world) {
        return WORLDS.computeIfAbsent(world, w -> new NavmeshDebug());
    }

    void attachTo(SceneNode cellRoot) {
        if (cellRoot == null) {
            return;
        }
        if (group == null) {
            group = new SceneNode();
            group.name = "navmesh-debug";
            group.debugDraw = true;
        }
        if (group.parent == cellRoot) {
            return;
        }
        group.removeFromParent();
        cellRoot.addChild(group);
    }

    void detachFrom(SceneNode cellRoot) {
        if (group != null && group.parent == cellRoot) {
            group.removeFromParent();
        }
    }

    void addTile(NavmeshCache.Tile tile) {
        if (group == null || tile == null || tile.tris.isEmpty()) {
            return;
        }
        int start = 0;
        while (start < tile.tris.size()) {
            int n = Math.min(tile.tris.size() - start, MAX_VERTS / 3);
            addChunk(tile.tris, start, n);
            start += n;
        }
    }

    void addChunk(List<float[]> tris, int start, int n) {
        if (group == null || tris == null || n <= 0) {
            return;
        }
        group.meshes.add(new MeshInstance(buildChunk(tris, start, n)));
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

    private MeshGpu buildChunk(List<float[]> tris, int start, int n) {
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
                ny = 0f;
                nz = 1f;
            }
            for (int k = 0; k < 3; k++) {
                int o = k * 3;
                putVert(interleaved, base, tri[o], tri[o + 1], tri[o + 2], nx, ny, nz);
                indices[t * 3 + k] = (short) base;
                base++;
            }
        }
        MeshGpu mesh = style(new MeshGpu(interleaved, indices));
        mesh.cull = false;
        mesh.alphaBlend = true;
        mesh.depthWrite = false;
        mesh.matAlpha = 0.45f;
        return mesh;
    }

    private MeshGpu style(MeshGpu mesh) {
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
        mesh.noSorter = true;
        gpus.add(mesh);
        return mesh;
    }

    private static void putVert(float[] interleaved, int vi, float x, float y, float z,
        float nx, float ny, float nz) {
        int o = vi * MeshGpu.STRIDE_FLOATS;
        interleaved[o] = x;
        interleaved[o + 1] = y;
        interleaved[o + 2] = z;
        interleaved[o + 3] = nx;
        interleaved[o + 4] = ny;
        interleaved[o + 5] = nz;
        interleaved[o + 6] = 0f;
        interleaved[o + 7] = 0f;
        interleaved[o + 8] = RGB[0];
        interleaved[o + 9] = RGB[1];
        interleaved[o + 10] = RGB[2];
        interleaved[o + 11] = 0.45f;
    }
}
