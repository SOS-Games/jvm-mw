/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Moves skinned vertices on the CPU: each vertex is a weighted blend of
 * bone poses. Rest pose first, then again after idle animation moves the
 * bones. Skin bind-pose data is rotation, then translation, then scale —
 * the other order flattens people onto the ground.
 */
public final class Skinning {
    private Skinning() {
    }

    public static Map<String, Matrix4> restBoneWorlds(NifFile nif) {
        Map<String, Matrix4> worlds = new HashMap<>();
        Matrix4 id = new Matrix4();
        for (int root : nif.roots) {
            walk(nif, root, id, worlds);
        }
        return worlds;
    }

    public static int apply(NifFile nif, NiTriBasedGeom geom, float[] verts, float[] norms,
        Map<String, Matrix4> boneWorld) {
        NifRecord skinRec = nif.get(geom.skin);
        if (!(skinRec instanceof NiSkinInstance skin)) {
            return -1;
        }
        NifRecord dataRec = nif.get(skin.data);
        if (!(dataRec instanceof NiSkinData data)) {
            return -2;
        }
        if (data.bones.size() != skin.bones.size()) {
            return -3;
        }
        int n = verts.length / 3;
        float[] acc = new float[n * 16];
        for (int i = 0; i < n; i++) {
            acc[i * 16 + Matrix4.M33] = 1f;
        }
        Matrix4 skinT = new Matrix4();
        data.transform.toMatrix(skinT);
        Matrix4 invBind = new Matrix4();
        Matrix4 boneMat = new Matrix4();
        int found = 0;
        int missing = 0;
        for (int b = 0; b < skin.bones.size(); b++) {
            NifRecord boneRec = nif.get(skin.bones.get(b));
            if (!(boneRec instanceof NiAvObject av) || av.name.isEmpty()) {
                missing++;
                continue;
            }
            Matrix4 world = boneWorld.get(av.name.toLowerCase(Locale.ROOT));
            if (world == null) {
                missing++;
                continue;
            }
            found++;
            data.bones.get(b).transform.toMatrix(invBind);
            boneMat.set(world).mul(invBind);
            float[] bm = boneMat.val;
            for (NiSkinData.Weight w : data.bones.get(b).weights) {
                if (w.vertex < 0 || w.vertex >= n) {
                    continue;
                }
                int o = w.vertex * 16;
                for (int i = 0; i < 16; i++) {
                    if (i % 4 != 3) {
                        acc[o + i] += bm[i] * w.weight;
                    }
                }
            }
        }
        Matrix4 out = new Matrix4();
        Matrix4 accM = new Matrix4();
        Vector3 v = new Vector3();
        for (int i = 0; i < n; i++) {
            int o = i * 16;
            System.arraycopy(acc, o, accM.val, 0, 16);
            out.set(skinT).mul(accM);
            int i3 = i * 3;
            v.set(verts[i3], verts[i3 + 1], verts[i3 + 2]).mul(out);
            verts[i3] = v.x;
            verts[i3 + 1] = v.y;
            verts[i3 + 2] = v.z;
            if (norms != null && norms.length == n * 3) {
                v.set(norms[i3], norms[i3 + 1], norms[i3 + 2]);
                float nx = v.x * out.val[Matrix4.M00] + v.y * out.val[Matrix4.M01] + v.z * out.val[Matrix4.M02];
                float ny = v.x * out.val[Matrix4.M10] + v.y * out.val[Matrix4.M11] + v.z * out.val[Matrix4.M12];
                float nz = v.x * out.val[Matrix4.M20] + v.y * out.val[Matrix4.M21] + v.z * out.val[Matrix4.M22];
                norms[i3] = nx;
                norms[i3 + 1] = ny;
                norms[i3 + 2] = nz;
            }
        }
        return missing == 0 ? found : -1000 - missing;
    }

    public static String describe(NifFile nif, NiSkinInstance skin) {
        StringBuilder sb = new StringBuilder();
        NifRecord dataRec = nif.get(skin.data);
        NifRecord rootRec = nif.get(skin.root);
        sb.append("skinInst data=").append(skin.data).append(" root=").append(skin.root);
        if (rootRec != null) {
            sb.append('(').append(rootRec.name).append(')');
        }
        sb.append(" bones=").append(skin.bones.size());
        if (dataRec instanceof NiSkinData data) {
            sb.append(" dataBones=").append(data.bones.size());
            sb.append(" skinT=(")
                .append(fmt(data.transform.translation.x)).append(',')
                .append(fmt(data.transform.translation.y)).append(',')
                .append(fmt(data.transform.translation.z)).append(") s=")
                .append(fmt(data.transform.scale));
        }
        sb.append(" [");
        for (int i = 0; i < skin.bones.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            NifRecord rec = nif.get(skin.bones.get(i));
            sb.append(rec instanceof NiAvObject av ? av.name : String.valueOf(skin.bones.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static void walk(NifFile nif, int idx, Matrix4 parent, Map<String, Matrix4> worlds) {
        NifRecord rec = nif.get(idx);
        if (!(rec instanceof NiAvObject av)) {
            return;
        }
        Matrix4 local = new Matrix4();
        av.transform.toMatrix(local);
        Matrix4 world = new Matrix4(parent).mul(local);
        if (!av.name.isEmpty()) {
            worlds.putIfAbsent(av.name.toLowerCase(Locale.ROOT), world);
        }
        if (av instanceof NiNode node) {
            for (int child : node.children) {
                walk(nif, child, world, worlds);
            }
        }
    }

    private static String fmt(float v) {
        if (v == (int) v) {
            return Integer.toString((int) v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
