package io.github.jvmmw.render;

import io.github.jvmmw.nif.NiAvObject;
import io.github.jvmmw.nif.NiNode;
import io.github.jvmmw.nif.NiStringExtraData;
import io.github.jvmmw.nif.NiTriBasedGeom;
import io.github.jvmmw.nif.NiTriShapeData;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NifRecord;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triangle collision for one .nif, in that file’s root space. Shared by every
 * placement of the same mesh. Plants tagged NC have none. If the file has a
 * collision node with children, only those triangles are used; otherwise the
 * visible mesh is the collider (including for the camera).
 */
public final class CollisionMesh {
    static final CollisionMesh EMPTY = new CollisionMesh(new float[0]);

    private static final Map<String, CollisionMesh> interned = new HashMap<>();

    /** Packed xyz triples, three verts per triangle. */
    public final float[] tris;
    public final float[] localMin = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
    public final float[] localMax = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};

    private CollisionMesh(float[] tris) {
        this.tris = tris;
        for (int i = 0; i + 2 < tris.length; i += 3) {
            localMin[0] = Math.min(localMin[0], tris[i]);
            localMin[1] = Math.min(localMin[1], tris[i + 1]);
            localMin[2] = Math.min(localMin[2], tris[i + 2]);
            localMax[0] = Math.max(localMax[0], tris[i]);
            localMax[1] = Math.max(localMax[1], tris[i + 1]);
            localMax[2] = Math.max(localMax[2], tris[i + 2]);
        }
    }

    public boolean isEmpty() {
        return tris.length < 9;
    }

    public static CollisionMesh intern(String model) {
        String vfs = TexturePaths.normalizeMeshPath(model);
        CollisionMesh hit = interned.get(vfs);
        if (hit != null) {
            return hit;
        }
        CollisionMesh built = EMPTY;
        try {
            built = build(GpuCache.nif(vfs).nif());
        } catch (Exception ignored) {
            built = EMPTY;
        }
        interned.put(vfs, built);
        return built;
    }

    static CollisionMesh build(NifFile nif) {
        if (noCollision(nif)) {
            return EMPTY;
        }
        NiNode col = findRootCollision(nif);
        boolean autogen = col == null || col.children.isEmpty();
        boolean markers = rootHasMrk(nif);
        List<Float> out = new ArrayList<>();
        Matrix4 id = new Matrix4();
        for (int idx : nif.roots) {
            NifRecord rec = nif.get(idx);
            if (rec instanceof NiAvObject av) {
                walk(nif, av, id, autogen, false, markers, out);
            }
        }
        if (out.size() < 9) {
            return EMPTY;
        }
        float[] tris = new float[out.size()];
        for (int i = 0; i < out.size(); i++) {
            tris[i] = out.get(i);
        }
        return new CollisionMesh(tris);
    }

    private static boolean noCollision(NifFile nif) {
        for (int idx : nif.roots) {
            NifRecord rec = nif.get(idx);
            int extra = rec instanceof NiAvObject av ? av.extra : rec != null ? rec.extra : -1;
            while (extra >= 0) {
                NifRecord e = nif.get(extra);
                if (e instanceof NiStringExtraData str && str.data != null
                    && str.data.regionMatches(true, 0, "NC", 0, 2)
                    && (str.data.length() <= 2 || str.data.charAt(2) != 'C')) {
                    return true;
                }
                extra = e != null ? e.extra : -1;
            }
        }
        return false;
    }

    private static boolean rootHasMrk(NifFile nif) {
        for (int idx : nif.roots) {
            NifRecord rec = nif.get(idx);
            int extra = rec instanceof NiAvObject av ? av.extra : rec != null ? rec.extra : -1;
            while (extra >= 0) {
                NifRecord e = nif.get(extra);
                if (e instanceof NiStringExtraData str && "MRK".equals(str.data)) {
                    return true;
                }
                extra = e != null ? e.extra : -1;
            }
        }
        return false;
    }

    private static NiNode findRootCollision(NifFile nif) {
        for (int idx : nif.roots) {
            NifRecord rec = nif.get(idx);
            if (!(rec instanceof NiNode root)) {
                continue;
            }
            for (int childIdx : root.children) {
                NifRecord child = nif.get(childIdx);
                if (child instanceof NiNode n && n.rootCollision) {
                    return n;
                }
            }
        }
        return null;
    }

    private static void walk(NifFile nif, NiAvObject av, Matrix4 parent, boolean autogen, boolean inCol,
        boolean markers, List<Float> out) {
        if ("AvoidNode".equals(av.recordName)) {
            return;
        }
        Matrix4 world = new Matrix4(parent);
        av.transform.toMatrix(TMP_M);
        world.mul(TMP_M);
        boolean colHere = inCol || (av instanceof NiNode n && n.rootCollision && !autogen);
        if (av instanceof NiNode n && n.rootCollision && autogen) {
            return;
        }
        if ((autogen || colHere) && av instanceof NiTriBasedGeom geom && geom.skin < 0
            && !skipDrawable(geom, markers, colHere)) {
            addTris(nif, geom, world, out);
        }
        if (av instanceof NiNode group) {
            for (int childIdx : group.children) {
                NifRecord rec = nif.get(childIdx);
                if (rec instanceof NiAvObject child) {
                    walk(nif, child, world, autogen, colHere, markers, out);
                }
            }
        }
    }

    private static boolean skipDrawable(NiTriBasedGeom geom, boolean markers, boolean inCol) {
        if (!inCol && geom.skipMeshes) {
            return true;
        }
        String name = geom.name == null ? "" : geom.name;
        if (name.regionMatches(true, 0, "shadow", 0, 6) || name.regionMatches(true, 0, "tri shadow", 0, 10)) {
            return true;
        }
        return markers && name.regionMatches(true, 0, "tri editormarker", 0, 16);
    }

    private static void addTris(NifFile nif, NiTriBasedGeom geom, Matrix4 world, List<Float> out) {
        NifRecord dataRec = nif.get(geom.data);
        if (!(dataRec instanceof NiTriShapeData data) || data.vertices.length < 9 || data.triangles.length < 3) {
            return;
        }
        short[] idx = data.triangles;
        float[] v = data.vertices;
        for (int i = 0; i + 2 < idx.length; i += 3) {
            emit(v, idx[i] & 0xffff, world, out);
            emit(v, idx[i + 1] & 0xffff, world, out);
            emit(v, idx[i + 2] & 0xffff, world, out);
        }
    }

    private static void emit(float[] v, int i, Matrix4 world, List<Float> out) {
        int o = i * 3;
        if (o + 2 >= v.length) {
            return;
        }
        TMP_V.set(v[o], v[o + 1], v[o + 2]).mul(world);
        out.add(TMP_V.x);
        out.add(TMP_V.y);
        out.add(TMP_V.z);
    }

    private static final Matrix4 TMP_M = new Matrix4();
    private static final Vector3 TMP_V = new Vector3();

    /** One interned mesh at a placed instance. Bullet, F7, and Recast share this. */
    public static final class Pending {
        final CollisionMesh mesh;
        final SceneNode node;
        final int gridX;
        final int gridY;

        public Pending(CollisionMesh mesh, SceneNode node) {
            this(mesh, node, 0, 0);
        }

        public Pending(CollisionMesh mesh, SceneNode node, int gridX, int gridY) {
            this.mesh = mesh;
            this.node = node;
            this.gridX = gridX;
            this.gridY = gridY;
        }
    }
}
