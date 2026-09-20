package io.github.jvmmw.debug;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.nif.NiAvObject;
import io.github.jvmmw.nif.NiNode;
import io.github.jvmmw.nif.NiTriBasedGeom;
import io.github.jvmmw.nif.NiTriShapeData;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NifRecord;
import io.github.jvmmw.render.EsmTransforms;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless check for cave / platform kit holes. ESM placement can look
 * adjacent while the actual triangles never touch — that is the Zainsipilu
 * screenshot. {@code cell} prints each kit piece’s world box, then which
 * hulls share an edge and which nearby pair is the widest miss.
 */
public final class KitSeams {
    /** Vertices closer than this count as a shared seam. */
    static final float MEET = 16f;
    /** Only compare pieces whose boxes are at least this close. */
    private static final float NEAR = 128f;
    private static final float CELL = 32f;

    private KitSeams() {
    }

    static boolean isKitModel(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        return m.contains("moldcave") || m.contains("pycave") || m.contains("cavern_door")
            || m.contains("in_cave") || m.contains("plat_");
    }

    static void dump(EsmFile.LoadedCell cell) {
        List<Chunk> chunks = new ArrayList<>();
        for (CellRef ref : cell.refs) {
            if (ref.deleted) {
                continue;
            }
            EsmObject obj = cell.objects.get(ref.refId.toLowerCase(Locale.ROOT));
            if (obj == null || obj.model.isEmpty() || !isKitModel(obj.model)) {
                continue;
            }
            Chunk chunk = load(ref, obj);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        if (chunks.isEmpty()) {
            return;
        }
        int n = chunks.size();
        float[][] dist = new float[n][n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            dist[i][i] = 0f;
        }
        float worstNear = -1f;
        int worstA = -1;
        int worstB = -1;
        int meets = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float gap = aabbGap(chunks.get(i), chunks.get(j));
                if (gap > NEAR) {
                    dist[i][j] = dist[j][i] = Float.POSITIVE_INFINITY;
                    continue;
                }
                float d = vertexDist(chunks.get(i), chunks.get(j));
                dist[i][j] = dist[j][i] = d;
                if (d <= MEET) {
                    union(parent, i, j);
                    meets++;
                    System.out.println("seam meet a=" + chunks.get(i).label
                        + " b=" + chunks.get(j).label
                        + " dist=" + fmt(d));
                } else if (d < 256f && d > worstNear) {
                    worstNear = d;
                    worstA = i;
                    worstB = j;
                }
            }
        }
        Map<Integer, List<Integer>> islands = new HashMap<>();
        for (int i = 0; i < n; i++) {
            islands.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        System.out.println("seam pieces=" + n + " meet=" + meets + " islands=" + islands.size()
            + " meetDist<" + (int) MEET);
        int island = 0;
        for (List<Integer> members : islands.values()) {
            StringBuilder sb = new StringBuilder("seam island ").append(island++).append(" n=")
                .append(members.size());
            for (int i : members) {
                sb.append(' ').append(chunks.get(i).label);
            }
            System.out.println(sb);
        }
        if (worstA >= 0) {
            System.out.println("seam gap a=" + chunks.get(worstA).label
                + " b=" + chunks.get(worstB).label
                + " dist=" + fmt(worstNear)
                + " aabbGap=" + fmt(aabbGap(chunks.get(worstA), chunks.get(worstB))));
        }
    }

    static String kitLine(CellRef ref, String model) {
        Chunk chunk = load(ref, model == null ? "" : model);
        if (chunk == null) {
            return "gl=fail";
        }
        return "gl=(" + (int) chunk.min[0] + ".." + (int) chunk.max[0] + ","
            + (int) chunk.min[1] + ".." + (int) chunk.max[1] + ","
            + (int) chunk.min[2] + ".." + (int) chunk.max[2] + ")"
            + " verts=" + (chunk.verts.length / 3)
            + chunk.shapes;
    }

    private static Chunk load(CellRef ref, EsmObject obj) {
        return load(ref, obj.model);
    }

    private static Chunk load(CellRef ref, String model) {
        try {
            String vfs = TexturePaths.normalizeMeshPath(model);
            Path file = TestData.ensureNif(vfs);
            NifFile nif = NifFile.parse(Files.readAllBytes(file), vfs);
            Matrix4 cell = new Matrix4().setToRotation(1, 0, 0, -90);
            Matrix4 esm = new Matrix4();
            EsmTransforms.setLocal(esm, ref.pos, ref.rot, ref.scale);
            Matrix4 world = new Matrix4(cell).mul(esm);
            Chunk chunk = new Chunk();
            chunk.label = shortModel(model) + "@(" + (int) ref.pos[0] + "," + (int) ref.pos[1] + ","
                + (int) ref.pos[2] + ")";
            List<Float> out = new ArrayList<>();
            StringBuilder shapes = new StringBuilder();
            for (int idx : nif.roots) {
                NifRecord rec = nif.get(idx);
                if (rec instanceof NiAvObject av) {
                    walk(nif, av, world, out, shapes);
                }
            }
            if (out.size() < 9) {
                return null;
            }
            chunk.verts = new float[out.size()];
            for (int i = 0; i < out.size(); i++) {
                chunk.verts[i] = out.get(i);
                int ax = i % 3;
                chunk.min[ax] = Math.min(chunk.min[ax], out.get(i));
                chunk.max[ax] = Math.max(chunk.max[ax], out.get(i));
            }
            chunk.shapes = shapes.toString();
            return chunk;
        } catch (Exception e) {
            return null;
        }
    }

    private static void walk(NifFile nif, NiAvObject av, Matrix4 parent, List<Float> out, StringBuilder shapes) {
        if (av instanceof NiNode n && n.rootCollision) {
            return;
        }
        Matrix4 local = new Matrix4();
        av.transform.toMatrix(local);
        Matrix4 world = new Matrix4(parent).mul(local);
        if (av instanceof NiTriBasedGeom geom && geom.skin < 0 && !av.skipMeshes) {
            NifRecord dataRec = nif.get(geom.data);
            if (dataRec instanceof NiTriShapeData data && data.vertices.length >= 9) {
                Vector3 v = new Vector3();
                for (int i = 0; i + 2 < data.vertices.length; i += 3) {
                    v.set(data.vertices[i], data.vertices[i + 1], data.vertices[i + 2]).mul(world);
                    out.add(v.x);
                    out.add(v.y);
                    out.add(v.z);
                }
                shapes.append(" t=").append((int) av.transform.translation.x)
                    .append(',').append((int) av.transform.translation.y)
                    .append(',').append((int) av.transform.translation.z);
            }
        }
        if (av instanceof NiNode group) {
            for (int childIdx : group.children) {
                NifRecord rec = nif.get(childIdx);
                if (rec instanceof NiAvObject child) {
                    walk(nif, child, world, out, shapes);
                }
            }
        }
    }

    private static float vertexDist(Chunk a, Chunk b) {
        Map<Long, List<Integer>> grid = new HashMap<>();
        float[] bv = b.verts;
        for (int i = 0; i < bv.length; i += 3) {
            grid.computeIfAbsent(key(bv[i], bv[i + 1], bv[i + 2]), k -> new ArrayList<>()).add(i);
        }
        float best = Float.POSITIVE_INFINITY;
        float[] av = a.verts;
        Vector3 pa = new Vector3();
        Vector3 pb = new Vector3();
        for (int i = 0; i < av.length; i += 3) {
            pa.set(av[i], av[i + 1], av[i + 2]);
            int gx = (int) Math.floor(pa.x / CELL);
            int gy = (int) Math.floor(pa.y / CELL);
            int gz = (int) Math.floor(pa.z / CELL);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> bin = grid.get(key(gx + dx, gy + dy, gz + dz));
                        if (bin == null) {
                            continue;
                        }
                        for (int j : bin) {
                            pb.set(bv[j], bv[j + 1], bv[j + 2]);
                            best = Math.min(best, pa.dst(pb));
                            if (best <= 0.5f) {
                                return best;
                            }
                        }
                    }
                }
            }
        }
        if (best != Float.POSITIVE_INFINITY) {
            return best;
        }
        for (int i = 0; i < av.length; i += 3) {
            pa.set(av[i], av[i + 1], av[i + 2]);
            for (int j = 0; j < bv.length; j += 3) {
                pb.set(bv[j], bv[j + 1], bv[j + 2]);
                best = Math.min(best, pa.dst(pb));
            }
        }
        return best;
    }

    private static long key(float x, float y, float z) {
        return key((int) Math.floor(x / CELL), (int) Math.floor(y / CELL), (int) Math.floor(z / CELL));
    }

    private static long key(int x, int y, int z) {
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
    }

    private static float aabbGap(Chunk a, Chunk b) {
        float dx = Math.max(0f, Math.max(a.min[0] - b.max[0], b.min[0] - a.max[0]));
        float dy = Math.max(0f, Math.max(a.min[1] - b.max[1], b.min[1] - a.max[1]));
        float dz = Math.max(0f, Math.max(a.min[2] - b.max[2], b.min[2] - a.max[2]));
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        a = find(parent, a);
        b = find(parent, b);
        if (a != b) {
            parent[b] = a;
        }
    }

    private static String shortModel(String model) {
        String p = model.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static final class Chunk {
        String label = "";
        String shapes = "";
        float[] verts = new float[0];
        final float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        final float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    }
}
