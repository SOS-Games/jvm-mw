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
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Heightfield with {@code VTEX}/{@code LTEX} and TES3 blendmaps. Rewrite of
 * {@code Storage::getBlendmaps} + {@code Terrain::createPasses}.
 */
public final class LandMesh {
    public static final String DEFAULT_TEXTURE = "_land_default.dds";
    public static final int CHUNK_SIZE = 1;
    public static final int BLENDMAP_SIZE = LandRecord.TEXTURE_SIZE * CHUNK_SIZE + 1;
    public static final int IMAGE_SCALE = 2;
    public static final int BLENDMAP_IMAGE_SIZE = BLENDMAP_SIZE * IMAGE_SCALE;
    public static final int TILE_COUNT = LandRecord.TEXTURE_SIZE * CHUNK_SIZE;

    private final List<MeshGpu> gpus = new ArrayList<>();
    private final Map<String, DdsTexture> textures = new HashMap<>();
    private final List<Integer> blendIds = new ArrayList<>();
    private int whiteTex;

    public SceneNode attach(SceneNode cellRoot, LandRecord land, Map<Integer, String> palette,
        List<LandRecord> allLands) {
        SceneNode node = new SceneNode();
        node.name = "land:" + land.gridX + "," + land.gridY;
        Map<Integer, String> ltex = palette != null ? palette : Map.of();
        Map<Long, LandRecord> byGrid = indexLands(allLands);
        List<String> samples = new ArrayList<>(BLENDMAP_SIZE * BLENDMAP_SIZE);
        Map<String, byte[]> images = new LinkedHashMap<>();
        for (int y = 0; y < BLENDMAP_SIZE; y++) {
            for (int x = 0; x < BLENDMAP_SIZE; x++) {
                String vfs = textureName(sampleVtex(land, byGrid, x, y), ltex);
                samples.add(vfs);
                images.computeIfAbsent(vfs, k -> new byte[BLENDMAP_IMAGE_SIZE * BLENDMAP_IMAGE_SIZE]);
            }
        }
        boolean blend = images.size() > 1;
        if (blend) {
            for (int y = 0; y < BLENDMAP_SIZE; y++) {
                for (int x = 0; x < BLENDMAP_SIZE; x++) {
                    byte[] data = images.get(samples.get(y * BLENDMAP_SIZE + x));
                    int realX = x * IMAGE_SCALE;
                    int realY = y * IMAGE_SCALE;
                    int w = BLENDMAP_IMAGE_SIZE;
                    data[(realY + 0) * w + realX + 0] = (byte) 255;
                    data[(realY + 1) * w + realX + 0] = (byte) 255;
                    data[(realY + 0) * w + realX + 1] = (byte) 255;
                    data[(realY + 1) * w + realX + 1] = (byte) 255;
                }
            }
        }
        MeshGpu geom = upload(land);
        boolean first = true;
        for (Map.Entry<String, byte[]> e : images.entrySet()) {
            MeshGpu mesh = first ? geom : upload(land);
            first = false;
            int tex = bind(e.getKey());
            mesh.baseTex = tex;
            mesh.darkTex = tex;
            mesh.detailTex = tex;
            mesh.glowTex = tex;
            mesh.textureId = tex;
            mesh.uvScale = TILE_COUNT;
            mesh.terrainPass = true;
            if (blend) {
                mesh.terrainPass = true;
                mesh.alphaBlend = true;
                mesh.useBlendMap = true;
                mesh.blendMapTex = uploadBlend(e.getValue());
                mesh.blendMapWrapS = GL20.GL_CLAMP_TO_EDGE;
                mesh.blendMapWrapT = GL20.GL_CLAMP_TO_EDGE;
                if (mesh == geom) {
                    mesh.blendSrc = GL20.GL_SRC_ALPHA;
                    mesh.blendDst = GL20.GL_ZERO;
                    mesh.depthFunc = GL20.GL_LEQUAL;
                } else {
                    mesh.blendSrc = GL20.GL_SRC_ALPHA;
                    mesh.blendDst = GL20.GL_ONE;
                    mesh.depthFunc = GL20.GL_EQUAL;
                }
            }
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
        for (int id : blendIds) {
            Gdx.gl.glDeleteTexture(id);
        }
        blendIds.clear();
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

    public static int layerCount(LandRecord land, List<LandRecord> allLands, Map<Integer, String> palette) {
        Map<Integer, String> ltex = palette != null ? palette : Map.of();
        Map<Long, LandRecord> byGrid = indexLands(allLands);
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int y = 0; y < BLENDMAP_SIZE; y++) {
            for (int x = 0; x < BLENDMAP_SIZE; x++) {
                ids.add(textureName(sampleVtex(land, byGrid, x, y), ltex));
            }
        }
        return ids.size();
    }

    private static Map<Long, LandRecord> indexLands(List<LandRecord> allLands) {
        Map<Long, LandRecord> byGrid = new HashMap<>();
        if (allLands != null) {
            for (LandRecord land : allLands) {
                byGrid.put(gridKey(land.gridX, land.gridY), land);
            }
        }
        return byGrid;
    }

    private static int sampleVtex(LandRecord land, Map<Long, LandRecord> byGrid, int x, int y) {
        int gx = land.gridX;
        int gy = land.gridY;
        int tx = x;
        int ty = y;
        if (x >= LandRecord.TEXTURE_SIZE) {
            gx += 1;
            tx = x - LandRecord.TEXTURE_SIZE;
        }
        if (y >= LandRecord.TEXTURE_SIZE) {
            gy += 1;
            ty = y - LandRecord.TEXTURE_SIZE;
        }
        LandRecord src = gx == land.gridX && gy == land.gridY ? land : byGrid.get(gridKey(gx, gy));
        if (src == null) {
            return 0;
        }
        return src.texture(tx, ty);
    }

    private static long gridKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
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
                interleaved[o + 6] = x / (float) (n - 1);
                interleaved[o + 7] = y / (float) (n - 1);
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
        mesh.ambient[1] = 0.58f;
        mesh.ambient[2] = 0.62f;
        mesh.diffuse[0] = 1f;
        mesh.diffuse[1] = 0.98f;
        mesh.diffuse[2] = 0.9f;
        return mesh;
    }

    private int uploadBlend(byte[] data) {
        int id = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, id);
        ByteBuffer px = BufferUtils.newByteBuffer(data.length);
        px.put(data).flip();
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL30.GL_R8, BLENDMAP_IMAGE_SIZE, BLENDMAP_IMAGE_SIZE, 0,
            GL30.GL_RED, GL20.GL_UNSIGNED_BYTE, px);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        blendIds.add(id);
        return id;
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
