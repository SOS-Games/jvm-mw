/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;

/**
 * TES3 water plane. Rewrite of {@code SceneUtil::createWaterGeometry} plus
 * shader-on {@code Water::createShaderWaterStateSet} with refraction.
 */
public final class WaterMesh {
    public static final float HEIGHT = -1f;
    public static final int RTT_SIZE = 512;
    public static final float REPEATS_PER_CELL = 900f / 150f;

    private MeshGpu gpu;

    public SceneNode attach(SceneNode cellRoot, int gridX, int gridY) {
        int cells = 2 * EsmFile.CELL_GRID_RADIUS + 1;
        float size = LandRecord.CELL_SIZE * cells;
        float cx = gridX * (float) LandRecord.CELL_SIZE + LandRecord.CELL_SIZE / 2f;
        float cy = gridY * (float) LandRecord.CELL_SIZE + LandRecord.CELL_SIZE / 2f;
        int segments = Math.max(3, cells * 2);
        float repeats = REPEATS_PER_CELL * cells;
        gpu = upload(cx, cy, size, segments, repeats);
        SceneNode node = new SceneNode();
        node.name = "water";
        node.meshes.add(new MeshInstance(gpu));
        cellRoot.addChild(node);
        return node;
    }

    public void update(float dt) {
    }

    public void dispose() {
        if (gpu != null) {
            gpu.dispose();
            gpu = null;
        }
    }

    private static MeshGpu upload(float cx, float cy, float size, int segments, float repeats) {
        int n = segments + 1;
        float step = size / segments;
        float uvStep = repeats / segments;
        float originX = cx - size / 2f;
        float originY = cy - size / 2f;
        float[] interleaved = new float[n * n * MeshGpu.STRIDE_FLOATS];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int o = (y * n + x) * MeshGpu.STRIDE_FLOATS;
                interleaved[o] = originX + x * step;
                interleaved[o + 1] = originY + y * step;
                interleaved[o + 2] = HEIGHT;
                interleaved[o + 3] = 0f;
                interleaved[o + 4] = 0f;
                interleaved[o + 5] = 1f;
                interleaved[o + 6] = x * uvStep;
                interleaved[o + 7] = repeats - y * uvStep;
                interleaved[o + 8] = 1f;
                interleaved[o + 9] = 1f;
                interleaved[o + 10] = 1f;
                interleaved[o + 11] = 1f;
            }
        }
        short[] indices = new short[segments * segments * 6];
        int w = 0;
        for (int y = 0; y < segments; y++) {
            for (int x = 0; x < segments; x++) {
                int i00 = y * n + x;
                int i10 = i00 + 1;
                int i01 = i00 + n;
                int i11 = i01 + 1;
                indices[w++] = (short) i00;
                indices[w++] = (short) i10;
                indices[w++] = (short) i01;
                indices[w++] = (short) i10;
                indices[w++] = (short) i11;
                indices[w++] = (short) i01;
            }
        }
        MeshGpu mesh = new MeshGpu(interleaved, indices);
        mesh.waterShader = true;
        mesh.alphaBlend = false;
        mesh.cull = false;
        mesh.depthTest = true;
        mesh.depthWrite = true;
        return mesh;
    }
}
