/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.math.Vector3;

/**
 * The water plane, sitting at height −1 in the Y-up cell. It follows the
 * camera and extends past the far clip, so a cell stream does not slide its
 * edge through the view. When the surface is in view, two 512 cameras capture
 * what’s under and what’s mirrored. A view full of land skips those cameras.
 */
public final class WaterMesh {
    public static final float HEIGHT = -1f;
    public static final int RTT_SIZE = 512;
    public static final float RTT_FEATURE_PIXELS = 20f;
    public static final float REPEATS_PER_CELL = 900f / 150f;
    /** Covers the exterior far clip (40000) in every direction from the camera. */
    public static final float EXTENT = 80000f;

    private MeshGpu gpu;
    private SceneNode node;

    public SceneNode attach(SceneNode cellRoot, int gridX, int gridY) {
        int segments = 4;
        float repeats = REPEATS_PER_CELL * (EXTENT / LandRecord.CELL_SIZE);
        gpu = upload(0f, 0f, EXTENT, segments, repeats);
        node = new SceneNode();
        node.name = "water";
        node.meshes.add(new MeshInstance(gpu));
        cellRoot.addChild(node);
        return node;
    }

    /** Keep the plane under the camera. Cell root is −90° X, so local Y is −GL Z. */
    public void follow(Vector3 eye) {
        if (node == null || eye == null) {
            return;
        }
        node.local.setToTranslation(eye.x, -eye.z, 0f);
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
