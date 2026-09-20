/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.resource.DdsTexture;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TES3 simple water plane. Rewrite of {@code SceneUtil::createWaterGeometry} plus
 * {@code Water::createSimpleWaterStateSet}.
 */
public final class WaterMesh {
    public static final float HEIGHT = -1f;
    public static final float ALPHA = 0.75f;
    public static final float FPS = 12f;
    public static final int FRAME_COUNT = 32;
    public static final String SURFACE = "water";
    public static final float REPEATS_PER_CELL = 900f / 150f;

    private MeshGpu gpu;
    private final List<DdsTexture> frames = new ArrayList<>();
    private float elapsed;
    private int frame;

    public SceneNode attach(SceneNode cellRoot, int gridX, int gridY) {
        loadFrames();
        if (frames.isEmpty()) {
            Gdx.app.error("WaterMesh", "No textures/water/" + SURFACE + "##.dds");
            return null;
        }
        int cells = 2 * EsmFile.CELL_GRID_RADIUS + 1;
        float size = LandRecord.CELL_SIZE * cells;
        float cx = gridX * (float) LandRecord.CELL_SIZE + LandRecord.CELL_SIZE / 2f;
        float cy = gridY * (float) LandRecord.CELL_SIZE + LandRecord.CELL_SIZE / 2f;
        int segments = Math.max(3, cells * 2);
        float repeats = REPEATS_PER_CELL * cells;
        gpu = upload(cx, cy, size, segments, repeats);
        bindFrame(0);
        SceneNode node = new SceneNode();
        node.name = "water";
        node.meshes.add(new MeshInstance(gpu));
        cellRoot.addChild(node);
        return node;
    }

    public void update(float dt) {
        if (gpu == null || frames.size() < 2) {
            return;
        }
        elapsed += dt;
        int next = ((int) (elapsed * FPS)) % frames.size();
        if (next != frame) {
            bindFrame(next);
        }
    }

    public void dispose() {
        if (gpu != null) {
            gpu.dispose();
            gpu = null;
        }
        for (DdsTexture dds : frames) {
            dds.dispose();
        }
        frames.clear();
        elapsed = 0f;
        frame = 0;
    }

    private void bindFrame(int i) {
        frame = i;
        int tex = frames.get(i).textureId;
        gpu.baseTex = tex;
        gpu.darkTex = tex;
        gpu.detailTex = tex;
        gpu.glowTex = tex;
        gpu.textureId = tex;
    }

    private void loadFrames() {
        int n = Math.min(FRAME_COUNT, 320);
        for (int i = 0; i < n; i++) {
            String raw = String.format(Locale.ROOT, "textures/water/%s%02d.dds", SURFACE, i);
            String vfs = TexturePaths.correctTexturePath(raw, TestData::vfsExists);
            if (!TestData.vfsExists(vfs)) {
                continue;
            }
            try {
                Path file = TestData.openPath(vfs);
                frames.add(DdsTexture.load(file));
            } catch (Exception e) {
                Gdx.app.error("WaterMesh", "Texture failed: " + vfs + " " + e.getMessage());
            }
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
        mesh.alphaBlend = true;
        mesh.cull = false;
        mesh.depthTest = true;
        mesh.depthWrite = false;
        mesh.matAlpha = ALPHA;
        mesh.ambient[0] = mesh.ambient[1] = mesh.ambient[2] = 1f;
        mesh.diffuse[0] = mesh.diffuse[1] = mesh.diffuse[2] = 1f;
        mesh.blendSrc = GL20.GL_SRC_ALPHA;
        mesh.blendDst = GL20.GL_ONE_MINUS_SRC_ALPHA;
        return mesh;
    }
}
