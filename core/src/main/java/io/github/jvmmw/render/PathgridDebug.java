/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmPathgrid;
import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug overlay of a cell’s walk graph: a blue sphere per node and cyan
 * lines for edges. F5 hides it. Exterior points in the ESM are local to
 * that cell (0–8192); we add the cell origin so they sit on Town, then
 * parent under the cell root so the same −90° X as land and kit applies.
 * Water cameras skip this. Wander still walks a straight line.
 */
public final class PathgridDebug {
    public static boolean visible = true;

    private static final float RADIUS = 28f;
    private static final int SLICES = 8;
    private static final int STACKS = 6;
    private static final int VERTS_PER = (STACKS + 1) * (SLICES + 1);
    private static final int IDX_PER = STACKS * SLICES * 6;
    private static final float[] NODE_RGB = {0.2f, 0.45f, 1f};
    private static final float[] EDGE_RGB = {0.45f, 1f, 1f};

    private final List<MeshGpu> gpus = new ArrayList<>();
    private SceneNode group;

    public void attach(SceneNode cellRoot, EsmFile.LoadedCell cell) {
        dispose();
        List<Grid> grids = new ArrayList<>();
        if (cell.interior) {
            addGrid(grids, cell.pathgrid, 0f, 0f);
        } else {
            for (EsmFile.GridTile tile : cell.tiles) {
                addGrid(grids, tile.pathgrid,
                    tile.gridX * (float) LandRecord.CELL_SIZE,
                    tile.gridY * (float) LandRecord.CELL_SIZE);
            }
        }
        int points = 0;
        int edges = 0;
        for (Grid g : grids) {
            points += g.grid.points.size();
            edges += countValidEdges(g.grid);
        }
        if (points == 0) {
            return;
        }
        group = new SceneNode();
        group.name = "pathgrid-debug";
        group.debugDraw = true;
        group.meshes.add(new MeshInstance(buildSpheres(grids, points)));
        if (edges > 0) {
            group.meshes.add(new MeshInstance(buildLines(grids, edges)));
        }
        cellRoot.addChild(group);
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

    private static void addGrid(List<Grid> grids, EsmPathgrid grid, float ox, float oy) {
        if (grid == null || grid.points.isEmpty()) {
            return;
        }
        grids.add(new Grid(grid, ox, oy));
    }

    private static int countValidEdges(EsmPathgrid grid) {
        int n = 0;
        int pts = grid.points.size();
        for (EsmPathgrid.Edge e : grid.edges) {
            if (e.v0 == e.v1 || e.v0 < 0 || e.v1 < 0 || e.v0 >= pts || e.v1 >= pts) {
                continue;
            }
            n++;
        }
        return n;
    }

    private MeshGpu buildSpheres(List<Grid> grids, int points) {
        float[] interleaved = new float[points * VERTS_PER * MeshGpu.STRIDE_FLOATS];
        short[] indices = new short[points * IDX_PER];
        int base = 0;
        int w = 0;
        for (Grid g : grids) {
            for (EsmPathgrid.Point p : g.grid.points) {
                float cx = g.originX + p.x;
                float cy = g.originY + p.y;
                float cz = p.z;
                int v0 = base;
                for (int st = 0; st <= STACKS; st++) {
                    float phi = (float) (Math.PI * st / (double) STACKS);
                    float sinPhi = (float) Math.sin(phi);
                    float cosPhi = (float) Math.cos(phi);
                    for (int sl = 0; sl <= SLICES; sl++) {
                        float theta = (float) (2.0 * Math.PI * sl / (double) SLICES);
                        float nx = sinPhi * (float) Math.cos(theta);
                        float ny = sinPhi * (float) Math.sin(theta);
                        float nz = cosPhi;
                        putVert(interleaved, base, cx + nx * RADIUS, cy + ny * RADIUS, cz + nz * RADIUS,
                            nx, ny, nz, NODE_RGB);
                        base++;
                    }
                }
                for (int st = 0; st < STACKS; st++) {
                    for (int sl = 0; sl < SLICES; sl++) {
                        int i0 = v0 + st * (SLICES + 1) + sl;
                        int i1 = i0 + 1;
                        int i2 = i0 + (SLICES + 1);
                        int i3 = i2 + 1;
                        indices[w++] = (short) i0;
                        indices[w++] = (short) i2;
                        indices[w++] = (short) i1;
                        indices[w++] = (short) i1;
                        indices[w++] = (short) i2;
                        indices[w++] = (short) i3;
                    }
                }
            }
        }
        return style(new MeshGpu(interleaved, indices), GL20.GL_TRIANGLES);
    }

    private MeshGpu buildLines(List<Grid> grids, int edges) {
        float[] interleaved = new float[edges * 2 * MeshGpu.STRIDE_FLOATS];
        short[] indices = new short[edges * 2];
        int base = 0;
        int w = 0;
        for (Grid g : grids) {
            int pts = g.grid.points.size();
            for (EsmPathgrid.Edge e : g.grid.edges) {
                if (e.v0 == e.v1 || e.v0 < 0 || e.v1 < 0 || e.v0 >= pts || e.v1 >= pts) {
                    continue;
                }
                EsmPathgrid.Point a = g.grid.points.get(e.v0);
                EsmPathgrid.Point b = g.grid.points.get(e.v1);
                putVert(interleaved, base, g.originX + a.x, g.originY + a.y, a.z + 8f,
                    0f, 0f, 1f, EDGE_RGB);
                indices[w++] = (short) base;
                base++;
                putVert(interleaved, base, g.originX + b.x, g.originY + b.y, b.z + 8f,
                    0f, 0f, 1f, EDGE_RGB);
                indices[w++] = (short) base;
                base++;
            }
        }
        MeshGpu mesh = style(new MeshGpu(interleaved, indices), GL20.GL_LINES);
        mesh.cull = false;
        return mesh;
    }

    private MeshGpu style(MeshGpu mesh, int primitive) {
        int white = GpuCache.whiteId();
        mesh.primitive = primitive;
        mesh.baseTex = white;
        mesh.darkTex = white;
        mesh.detailTex = white;
        mesh.glowTex = white;
        mesh.textureId = white;
        mesh.colorMode = MeshGpu.COLOR_EMISSION;
        mesh.ambient[0] = mesh.ambient[1] = mesh.ambient[2] = 0f;
        mesh.diffuse[0] = mesh.diffuse[1] = mesh.diffuse[2] = 0f;
        mesh.noSorter = true;
        mesh.depthWrite = true;
        gpus.add(mesh);
        return mesh;
    }

    private static void putVert(float[] interleaved, int vi, float x, float y, float z,
        float nx, float ny, float nz, float[] rgb) {
        int o = vi * MeshGpu.STRIDE_FLOATS;
        interleaved[o] = x;
        interleaved[o + 1] = y;
        interleaved[o + 2] = z;
        interleaved[o + 3] = nx;
        interleaved[o + 4] = ny;
        interleaved[o + 5] = nz;
        interleaved[o + 6] = 0f;
        interleaved[o + 7] = 0f;
        interleaved[o + 8] = rgb[0];
        interleaved[o + 9] = rgb[1];
        interleaved[o + 10] = rgb[2];
        interleaved[o + 11] = 1f;
    }

    private record Grid(EsmPathgrid grid, float originX, float originY) {
    }
}
