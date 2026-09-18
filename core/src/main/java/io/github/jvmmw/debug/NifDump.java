package io.github.jvmmw.debug;

import io.github.jvmmw.nif.NiAvObject;
import io.github.jvmmw.nif.NiNode;
import io.github.jvmmw.nif.NiStencilProperty;
import io.github.jvmmw.nif.NiTriBasedGeom;
import io.github.jvmmw.nif.NiTriShapeData;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NifRecord;

import com.badlogic.gdx.math.Matrix4;

/** Pretty-print a Morrowind NIF for agents. */
public final class NifDump {
    private NifDump() {
    }

    public static String dump(NifFile nif) {
        StringBuilder sb = new StringBuilder();
        sb.append("records=").append(nif.records.size()).append(" roots=").append(nif.roots).append('\n');
        for (int root : nif.roots) {
            dumpRec(nif, root, 0, sb);
        }
        return sb.toString();
    }

    private static void dumpRec(NifFile nif, int index, int depth, StringBuilder sb) {
        NifRecord rec = nif.get(index);
        indent(depth, sb);
        if (rec == null) {
            sb.append('[').append(index).append("] <null>\n");
            return;
        }
        sb.append(rec.recordIndex).append(' ').append(rec.recordName);
        if (!rec.name.isEmpty()) {
            sb.append(" name=").append(rec.name);
        }
        if (rec instanceof NiAvObject av) {
            sb.append(" t=(")
                .append(fmt(av.transform.translation.x)).append(',')
                .append(fmt(av.transform.translation.y)).append(',')
                .append(fmt(av.transform.translation.z)).append(')')
                .append(" s=").append(fmt(av.transform.scale))
                .append(" flags=0x").append(Integer.toHexString(av.flags));
            if (av.hidden()) {
                sb.append(" hidden");
            }
            if (av.skipMeshes) {
                sb.append(" skipMeshes");
            }
            if (av.controller >= 0) {
                NifRecord ctrl = nif.get(av.controller);
                sb.append(" ctrl=").append(av.controller);
                if (ctrl != null) {
                    sb.append('(').append(ctrl.recordName).append(')');
                }
            }
            if (av instanceof NiTriBasedGeom geom) {
                if (geom.strips) {
                    sb.append(" strips");
                }
                NifRecord dataRec = nif.get(geom.data);
                if (dataRec instanceof NiTriShapeData data && data.vertices.length >= 3) {
                    float minx = Float.POSITIVE_INFINITY, miny = Float.POSITIVE_INFINITY, minz = Float.POSITIVE_INFINITY;
                    float maxx = Float.NEGATIVE_INFINITY, maxy = Float.NEGATIVE_INFINITY, maxz = Float.NEGATIVE_INFINITY;
                    for (int i = 0; i + 2 < data.vertices.length; i += 3) {
                        minx = Math.min(minx, data.vertices[i]);
                        miny = Math.min(miny, data.vertices[i + 1]);
                        minz = Math.min(minz, data.vertices[i + 2]);
                        maxx = Math.max(maxx, data.vertices[i]);
                        maxy = Math.max(maxy, data.vertices[i + 1]);
                        maxz = Math.max(maxz, data.vertices[i + 2]);
                    }
                    sb.append(" verts=").append(data.numVertices)
                        .append(" tris=").append(data.triangles.length / 3)
                        .append(" aabb=(").append(fmt(minx)).append("..").append(fmt(maxx)).append(',')
                        .append(fmt(miny)).append("..").append(fmt(maxy)).append(',')
                        .append(fmt(minz)).append("..").append(fmt(maxz)).append(')');
                }
            }
            for (int pidx : av.properties) {
                NifRecord p = nif.get(pidx);
                if (p instanceof NiStencilProperty st) {
                    sb.append(" stencilDraw=").append(st.drawMode);
                }
            }
            if (av instanceof NiNode node && node.rootCollision) {
                sb.append(" RootCollision");
            }
            if (!isIdentityRotation(av.transform.rotation)) {
                sb.append(" R=[");
                for (int i = 0; i < 9; i++) {
                    if (i > 0) {
                        sb.append(i % 3 == 0 ? ' ' : ',');
                    }
                    sb.append(fmt(av.transform.rotation[i]));
                }
                sb.append(']');
                Matrix4 gl = new Matrix4();
                av.transform.toMatrix(gl);
                sb.append(" GL=[")
                    .append(fmt(gl.val[Matrix4.M00])).append(',').append(fmt(gl.val[Matrix4.M01])).append(',')
                    .append(fmt(gl.val[Matrix4.M02])).append(' ')
                    .append(fmt(gl.val[Matrix4.M10])).append(',').append(fmt(gl.val[Matrix4.M11])).append(',')
                    .append(fmt(gl.val[Matrix4.M12])).append(' ')
                    .append(fmt(gl.val[Matrix4.M20])).append(',').append(fmt(gl.val[Matrix4.M21])).append(',')
                    .append(fmt(gl.val[Matrix4.M22])).append(']');
            }
        }
        sb.append('\n');
        if (rec instanceof NiNode node) {
            for (int child : node.children) {
                if (child >= 0) {
                    dumpRec(nif, child, depth + 1, sb);
                }
            }
        }
    }

    private static boolean isIdentityRotation(float[] r) {
        for (int i = 0; i < 9; i++) {
            float expect = (i % 4 == 0) ? 1f : 0f;
            if (Math.abs(r[i] - expect) > 1e-4f) {
                return false;
            }
        }
        return true;
    }

    private static void indent(int depth, StringBuilder sb) {
        sb.append("  ".repeat(Math.max(0, depth)));
    }

    private static String fmt(float v) {
        if (v == (int) v) {
            return Integer.toString((int) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
