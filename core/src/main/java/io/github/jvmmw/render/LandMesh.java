/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.resource.DdsTexture;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Heightfield with {@code VTEX}/{@code LTEX} (no blendmaps). Rewrite of
 * {@code RenderingManager::addCell} terrain. One GPU mesh per texture per tile.
 */
public final class LandMesh {
    public static final String DEFAULT_TEXTURE = "_land_default.dds";

    private final List<MeshGpu> gpus = new ArrayList<>();
    private final Map<String, DdsTexture> textures = new HashMap<>();
    private int whiteTex;

    public SceneNode attach(SceneNode cellRoot, LandRecord land, Map<Integer, String> palette) {
        SceneNode node = new SceneNode();
        node.name = "land:" + land.gridX + "," + land.gridY;
        Map<Integer, String> ltex = palette != null ? palette : Map.of();
        Map<String, List<Integer>> squares = new LinkedHashMap<>();
        for (int ty = 0; ty < LandRecord.TEXTURE_SIZE; ty++) {
            for (int tx = 0; tx < LandRecord.TEXTURE_SIZE; tx++) {
                String vfs = textureName(land.texture(tx, ty), ltex);
                squares.computeIfAbsent(vfs, k -> new ArrayList<>()).add((ty << 8) | tx);
            }
        }
        for (Map.Entry<String, List<Integer>> e : squares.entrySet()) {
            MeshGpu mesh = upload(land, e.getValue());
            int tex = bind(e.getKey());
            mesh.baseTex = tex;
            mesh.darkTex = tex;
            mesh.detailTex = tex;
            mesh.glowTex = tex;
            mesh.textureId = tex;
            gpus.add(mesh);
            node.meshes.add(new MeshInstance(mesh));
        }
        cellRoot.addChild(node);
        return node;
    }

    public void dispose() {
        for (MeshGpu gpu : gpus) {
            gpu.dispose();
        }
        gpus.clear();
        for (DdsTexture dds : textures.values()) {
            dds.dispose();
        }
        textures.clear();
        if (whiteTex != 0) {
            Gdx.gl.glDeleteTexture(whiteTex);
            whiteTex = 0;
        }
    }

    static String textureName(int vtex, Map<Integer, String> palette) {
        String raw = DEFAULT_TEXTURE;
        if (vtex != 0) {
            String hit = palette.get(vtex - 1);
            if (hit != null && !hit.isEmpty()) {
                raw = hit;
            }
        }
        return TexturePaths.correctTexturePath(raw, TestData::vfsExists);
    }

    private MeshGpu upload(LandRecord land, List<Integer> squares) {
        int n = LandRecord.SIZE;
        float step = LandRecord.CELL_SIZE / (float) (n - 1);
        float originX = land.gridX * (float) LandRecord.CELL_SIZE;
        float originY = land.gridY * (float) LandRecord.CELL_SIZE;
        int verts = squares.size() * 25;
        float[] interleaved = new float[verts * MeshGpu.STRIDE_FLOATS];
        short[] indices = new short[squares.size() * 16 * 6];
        int vo = 0;
        int io = 0;
        int base = 0;
        for (int packed : squares) {
            int tx = packed & 0xff;
            int ty = packed >>> 8;
            for (int j = 0; j <= 4; j++) {
                for (int i = 0; i <= 4; i++) {
                    int x = tx * 4 + i;
                    int y = ty * 4 + j;
                    int o = vo * MeshGpu.STRIDE_FLOATS;
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
                    interleaved[o + 6] = i / 4f;
                    interleaved[o + 7] = j / 4f;
                    interleaved[o + 8] = 1f;
                    interleaved[o + 9] = 1f;
                    interleaved[o + 10] = 1f;
                    interleaved[o + 11] = 1f;
                    vo++;
                }
            }
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    int i00 = base + j * 5 + i;
                    int i10 = i00 + 1;
                    int i01 = i00 + 5;
                    int i11 = i01 + 1;
                    indices[io++] = (short) i00;
                    indices[io++] = (short) i10;
                    indices[io++] = (short) i01;
                    indices[io++] = (short) i10;
                    indices[io++] = (short) i11;
                    indices[io++] = (short) i01;
                }
            }
            base += 25;
        }
        MeshGpu mesh = new MeshGpu(interleaved, indices);
        mesh.ambient[0] = 0.55f;
        mesh.ambient[1] = 0.58f;
        mesh.ambient[2] = 0.62f;
        mesh.diffuse[0] = 1f;
        mesh.diffuse[1] = 0.98f;
        mesh.diffuse[2] = 0.9f;
        return mesh;
    }

    private int bind(String vfs) {
        DdsTexture cached = textures.get(vfs);
        if (cached != null) {
            return cached.textureId;
        }
        try {
            Path file = TestData.openPath(vfs);
            DdsTexture dds = DdsTexture.load(file);
            textures.put(vfs, dds);
            return dds.textureId;
        } catch (Exception e) {
            Gdx.app.error("LandMesh", "Texture failed: " + vfs + " " + e.getMessage());
            return white();
        }
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
