/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/**
 * Grey 65×65 heightfield. Rewrite of {@code RenderingManager::addCell} terrain
 * without {@code LTEX} blendmaps.
 */
public final class LandMesh {
    private MeshGpu gpu;
    private int whiteTex;

    public SceneNode attach(SceneNode cellRoot, LandRecord land) {
        SceneNode node = new SceneNode();
        node.name = "land:" + land.gridX + "," + land.gridY;
        gpu = upload(land);
        node.meshes.add(new MeshInstance(gpu));
        cellRoot.addChild(node);
        return node;
    }

    public void dispose() {
        if (gpu != null) {
            gpu.dispose();
            gpu = null;
        }
        if (whiteTex != 0) {
            Gdx.gl.glDeleteTexture(whiteTex);
            whiteTex = 0;
        }
    }

    private MeshGpu upload(LandRecord land) {
        int n = LandRecord.SIZE;
        float step = LandRecord.CELL_SIZE / (float) (n - 1);
        float originX = land.gridX * (float) LandRecord.CELL_SIZE;
        float originY = land.gridY * (float) LandRecord.CELL_SIZE;
        float[] interleaved = new float[n * n * MeshGpu.STRIDE_FLOATS];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int o = (y * n + x) * MeshGpu.STRIDE_FLOATS;
                interleaved[o] = originX + x * step;
                interleaved[o + 1] = originY + y * step;
                interleaved[o + 2] = land.height(x, y);
                int x0 = Math.max(0, x - 1);
                int x1 = Math.min(n - 1, x + 1);
                int y0 = Math.max(0, y - 1);
                int y1 = Math.min(n - 1, y + 1);
                float dx = land.height(x1, y) - land.height(x0, y);
                float dy = land.height(x, y1) - land.height(x, y0);
                float nx = -dx;
                float ny = -dy;
                float nz = step * (x1 - x0);
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-5f) {
                    nx = 0f;
                    ny = 0f;
                    nz = 1f;
                } else {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }
                interleaved[o + 3] = nx;
                interleaved[o + 4] = ny;
                interleaved[o + 5] = nz;
                interleaved[o + 8] = 1f;
                interleaved[o + 9] = 1f;
                interleaved[o + 10] = 1f;
                interleaved[o + 11] = 1f;
            }
        }
        int quads = (n - 1) * (n - 1);
        short[] indices = new short[quads * 6];
        int w = 0;
        for (int y = 0; y < n - 1; y++) {
            for (int x = 0; x < n - 1; x++) {
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
        mesh.ambient[0] = 0.55f;
        mesh.ambient[1] = 0.52f;
        mesh.ambient[2] = 0.48f;
        mesh.diffuse[0] = 0.62f;
        mesh.diffuse[1] = 0.58f;
        mesh.diffuse[2] = 0.52f;
        int white = white();
        mesh.baseTex = white;
        mesh.darkTex = white;
        mesh.detailTex = white;
        mesh.glowTex = white;
        mesh.textureId = white;
        return mesh;
    }

    private int white() {
        if (whiteTex != 0) {
            return whiteTex;
        }
        whiteTex = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, whiteTex);
        java.nio.ByteBuffer px = java.nio.ByteBuffer.allocateDirect(4);
        px.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, 1, 1, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        return whiteTex;
    }
}
